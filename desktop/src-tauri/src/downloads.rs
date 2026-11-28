use std::{
    collections::{BTreeMap, HashMap},
    fs::{self, File, OpenOptions},
    future::poll_fn,
    io::Write,
    path::{Path, PathBuf},
    sync::{Arc, Mutex, MutexGuard},
    task::{Poll, Waker},
    time::{Duration, Instant},
};

use reqwest::{
    header::{CONTENT_RANGE, RANGE},
    Client, Response,
};
use serde_json::{json, Value};
use tauri::{AppHandle, Emitter, Manager};
use url::Url;

pub const PROGRESS_EVENT: &str = "browser-download-progress";
pub const FINISHED_EVENT: &str = "browser-download-finished";

/// The toolbar renders a progress bar, so updates have to be frequent enough to
/// look alive but rare enough not to flood the IPC channel with chunk-sized
/// messages. Either threshold is enough to publish a new snapshot.
const PROGRESS_BYTES_STEP: u64 = 256 * 1024;
const PROGRESS_INTERVAL: Duration = Duration::from_millis(250);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(20);
const USER_AGENT: &str = "RudyBrowser";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DownloadStatus {
    Running,
    Paused,
    Completed,
    Failed,
    Cancelled,
}

impl DownloadStatus {
    fn as_str(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Paused => "paused",
            Self::Completed => "completed",
            Self::Failed => "failed",
            Self::Cancelled => "cancelled",
        }
    }

    fn is_terminal(self) -> bool {
        matches!(self, Self::Completed | Self::Failed | Self::Cancelled)
    }
}

struct DownloadRecord {
    id: u64,
    tab_id: u64,
    url: String,
    path: PathBuf,
    received: u64,
    total: Option<u64>,
    status: DownloadStatus,
    error: Option<String>,
}

impl DownloadRecord {
    fn payload(&self) -> Value {
        json!({
            "id": self.id,
            "tabId": self.tab_id,
            "url": self.url,
            "path": self.path.to_string_lossy(),
            "received": self.received,
            "total": self.total,
            "percent": percent_of(self.received, self.total),
            "status": self.status.as_str(),
            "success": self.status == DownloadStatus::Completed,
            "error": self.error,
        })
    }
}

/// Servers regularly omit the total length, and a zero total would divide by
/// zero, so both cases report zero percent and let the UI stay indeterminate.
fn percent_of(received: u64, total: Option<u64>) -> f64 {
    match total {
        Some(total) if total > 0 => {
            let ratio = (received as f64 / total as f64) * 100.0;
            (ratio.clamp(0.0, 100.0) * 10.0).round() / 10.0
        }
        _ => 0.0,
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct StreamPlan {
    offset: u64,
    append: bool,
    total: Option<u64>,
}

impl StreamPlan {
    /// A body that ended is only complete when the expected length arrived;
    /// without a known total the end of the body is the only completion signal.
    fn is_complete(self, received: u64) -> bool {
        self.total.map(|total| received >= total).unwrap_or(true)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct ContentRange {
    start: u64,
    total: Option<u64>,
}

fn parse_content_range(raw: &str) -> Option<ContentRange> {
    let (range, total) = raw.trim().rsplit_once('/')?;
    let range = range.strip_prefix("bytes ")?;
    let (start, _end) = range.split_once('-')?;
    let start = start.trim().parse().ok()?;
    let total = match total.trim() {
        "*" => None,
        value => Some(value.parse().ok()?),
    };
    Some(ContentRange { start, total })
}

/// Decides where the body of the response has to be written. A 206 continues
/// the partial file, while a 200 means the server ignored the range header and
/// the only correct reaction is to start over from zero.
fn plan_stream(
    resumed_from: u64,
    status: u16,
    content_length: Option<u64>,
    content_range: Option<&str>,
) -> Result<StreamPlan, String> {
    if status == 206 {
        let range = content_range.and_then(parse_content_range);
        if let Some(range) = range {
            if range.start != resumed_from {
                return Err("The server resumed the download at the wrong offset".into());
            }
        }
        let total = range
            .and_then(|range| range.total)
            .or_else(|| content_length.map(|length| resumed_from + length));
        return Ok(StreamPlan {
            offset: resumed_from,
            append: resumed_from > 0,
            total,
        });
    }

    if (200..300).contains(&status) {
        return Ok(StreamPlan {
            offset: 0,
            append: false,
            total: content_length,
        });
    }

    Err(format!(
        "The server answered the download with HTTP {status}"
    ))
}

fn range_header_value(offset: u64) -> Option<String> {
    if offset == 0 {
        None
    } else {
        Some(format!("bytes={offset}-"))
    }
}

fn should_emit_progress(bytes_since_emit: u64, elapsed: Duration) -> bool {
    bytes_since_emit >= PROGRESS_BYTES_STEP || elapsed >= PROGRESS_INTERVAL
}

fn is_downloadable_url(raw: &str) -> bool {
    Url::parse(raw)
        .map(|url| matches!(url.scheme(), "http" | "https"))
        .unwrap_or(false)
}

fn file_length(path: &Path) -> u64 {
    fs::metadata(path)
        .map(|metadata| metadata.len())
        .unwrap_or(0)
}

fn open_destination(path: &Path, plan: StreamPlan) -> Result<File, String> {
    if plan.append {
        OpenOptions::new()
            .append(true)
            .open(path)
            .map_err(|error| format!("Could not reopen the partial download: {error}"))
    } else {
        File::create(path).map_err(|error| format!("Could not create the download file: {error}"))
    }
}

#[derive(Debug)]
enum RunOutcome {
    Completed,
    Failed(String),
    Cancelled,
}

impl RunOutcome {
    fn status(&self) -> DownloadStatus {
        match self {
            Self::Completed => DownloadStatus::Completed,
            Self::Failed(_) => DownloadStatus::Failed,
            Self::Cancelled => DownloadStatus::Cancelled,
        }
    }

    fn error(&self) -> Option<&str> {
        match self {
            Self::Failed(message) => Some(message.as_str()),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ControlAction {
    Continue,
    Pause,
    Cancel,
}

#[derive(Default)]
struct Gate {
    paused: bool,
    cancelled: bool,
    waker: Option<Waker>,
}

/// Carries the pause and cancel signals into the streaming task. The flags live
/// behind the same mutex as the waker so a signal can never be lost between the
/// state change and the registration of a new waker.
#[derive(Default)]
struct DownloadControl {
    gate: Mutex<Gate>,
}

impl DownloadControl {
    fn lock_gate(&self) -> MutexGuard<'_, Gate> {
        self.gate
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn pause(&self) {
        let mut gate = self.lock_gate();
        gate.paused = true;
        if let Some(waker) = gate.waker.take() {
            waker.wake();
        }
    }

    fn resume(&self) {
        let mut gate = self.lock_gate();
        gate.paused = false;
        if let Some(waker) = gate.waker.take() {
            waker.wake();
        }
    }

    fn cancel(&self) {
        let mut gate = self.lock_gate();
        gate.cancelled = true;
        if let Some(waker) = gate.waker.take() {
            waker.wake();
        }
    }

    fn action(&self) -> ControlAction {
        let gate = self.lock_gate();
        if gate.cancelled {
            ControlAction::Cancel
        } else if gate.paused {
            ControlAction::Pause
        } else {
            ControlAction::Continue
        }
    }

    /// Resolves once the transfer may continue again. `false` means the user
    /// cancelled the download while it was paused.
    async fn wait_for_resume(&self) -> bool {
        poll_fn(|context| {
            let mut gate = self.lock_gate();
            if gate.cancelled {
                return Poll::Ready(false);
            }
            if !gate.paused {
                return Poll::Ready(true);
            }
            gate.waker = Some(context.waker().clone());
            Poll::Pending
        })
        .await
    }
}

struct CancelPlan {
    payload: Value,
    control: Option<Arc<DownloadControl>>,
    path: PathBuf,
    tab_id: u64,
    url: String,
}

struct FinishedDownload {
    payload: Value,
    tab_id: u64,
    url: String,
    path: PathBuf,
    status: DownloadStatus,
}

struct DownloadSnapshot {
    url: String,
    path: PathBuf,
    status: DownloadStatus,
}

#[derive(Default)]
struct DownloadState {
    next_id: u64,
    records: BTreeMap<u64, DownloadRecord>,
    controls: HashMap<u64, Arc<DownloadControl>>,
}

impl DownloadState {
    fn register(
        &mut self,
        tab_id: u64,
        url: String,
        path: PathBuf,
    ) -> (u64, Arc<DownloadControl>, Value) {
        let id = self.next_id;
        self.next_id += 1;
        let control = Arc::new(DownloadControl::default());
        let record = DownloadRecord {
            id,
            tab_id,
            url,
            path,
            received: 0,
            total: None,
            status: DownloadStatus::Running,
            error: None,
        };
        let payload = record.payload();
        self.records.insert(id, record);
        self.controls.insert(id, Arc::clone(&control));
        (id, control, payload)
    }

    fn record(&self, id: u64) -> Result<&DownloadRecord, String> {
        self.records
            .get(&id)
            .ok_or_else(|| format!("Unknown download id: {id}"))
    }

    fn record_mut(&mut self, id: u64) -> Result<&mut DownloadRecord, String> {
        self.records
            .get_mut(&id)
            .ok_or_else(|| format!("Unknown download id: {id}"))
    }

    fn control(&self, id: u64) -> Option<Arc<DownloadControl>> {
        self.controls.get(&id).cloned()
    }

    fn payload(&self, id: u64) -> Result<Value, String> {
        self.record(id).map(DownloadRecord::payload)
    }

    fn list_payload(&self) -> Value {
        json!({
            "downloads": self
                .records
                .values()
                .map(DownloadRecord::payload)
                .collect::<Vec<_>>()
        })
    }

    fn mark_paused(&mut self, id: u64) -> Result<Value, String> {
        let record = self.record_mut(id)?;
        match record.status {
            DownloadStatus::Running => {
                record.status = DownloadStatus::Paused;
                Ok(record.payload())
            }
            DownloadStatus::Paused => Err("That download is already paused".into()),
            _ => Err("That download has already finished".into()),
        }
    }

    fn mark_running(&mut self, id: u64) -> Result<Value, String> {
        let record = self.record_mut(id)?;
        match record.status {
            DownloadStatus::Paused => {
                record.status = DownloadStatus::Running;
                Ok(record.payload())
            }
            DownloadStatus::Running => Err("That download is already running".into()),
            _ => Err("That download has already finished".into()),
        }
    }

    fn mark_cancelled(&mut self, id: u64) -> Result<CancelPlan, String> {
        let control = self.control(id);
        let record = self.record_mut(id)?;
        if record.status.is_terminal() {
            return Err("That download has already finished".into());
        }
        record.status = DownloadStatus::Cancelled;
        record.error = None;
        Ok(CancelPlan {
            payload: record.payload(),
            control,
            path: record.path.clone(),
            tab_id: record.tab_id,
            url: record.url.clone(),
        })
    }

    fn start_attempt(&mut self, id: u64, plan: StreamPlan) -> Result<Value, String> {
        let record = self.record_mut(id)?;
        record.received = plan.offset;
        record.total = plan.total;
        Ok(record.payload())
    }

    fn set_received(&mut self, id: u64, received: u64) -> Result<(), String> {
        self.record_mut(id)?.received = received;
        Ok(())
    }

    fn finish(&mut self, id: u64, outcome: &RunOutcome) -> Result<FinishedDownload, String> {
        let (payload, tab_id, url, path, status) = {
            let record = self.record_mut(id)?;
            // A cancellation the user already asked for outranks whatever the
            // task was doing when it noticed, otherwise a request that fails
            // afterwards would resurrect the download as a failure.
            let status = if record.status == DownloadStatus::Cancelled {
                DownloadStatus::Cancelled
            } else {
                outcome.status()
            };
            record.status = status;
            record.error = if status == DownloadStatus::Failed {
                outcome.error().map(str::to_string)
            } else {
                None
            };
            (
                record.payload(),
                record.tab_id,
                record.url.clone(),
                record.path.clone(),
                status,
            )
        };
        self.controls.remove(&id);
        Ok(FinishedDownload {
            payload,
            tab_id,
            url,
            path,
            status,
        })
    }
}

/// Owns every download of the session. Records are kept after the transfer
/// ends so the toolbar can still list completed and failed downloads.
pub struct DownloadManager {
    state: Mutex<DownloadState>,
}

impl Default for DownloadManager {
    fn default() -> Self {
        Self {
            state: Mutex::new(DownloadState::default()),
        }
    }
}

impl DownloadManager {
    fn lock_state(&self) -> Result<MutexGuard<'_, DownloadState>, String> {
        self.state
            .lock()
            .map_err(|error| format!("Download lock failed: {error}"))
    }

    pub fn start(
        &self,
        app: &AppHandle,
        tab_id: u64,
        url: &Url,
        path: PathBuf,
    ) -> Result<u64, String> {
        let (id, control, payload) = {
            let mut state = self.lock_state()?;
            state.register(tab_id, url.as_str().to_string(), path)
        };
        let _ = app.emit(PROGRESS_EVENT, payload);
        // Detached on purpose: the task reports through events and is driven by
        // the pause and cancel flags, not by the caller of this method.
        drop(tauri::async_runtime::spawn(run_download(
            app.clone(),
            id,
            control,
        )));
        Ok(id)
    }

    pub fn pause(&self, app: &AppHandle, id: u64) -> Result<Value, String> {
        let (payload, control) = {
            let mut state = self.lock_state()?;
            let control = state
                .control(id)
                .ok_or_else(|| "That download is no longer running".to_string())?;
            let payload = state.mark_paused(id)?;
            (payload, control)
        };
        control.pause();
        let _ = app.emit(PROGRESS_EVENT, payload.clone());
        Ok(payload)
    }

    pub fn resume(&self, app: &AppHandle, id: u64) -> Result<Value, String> {
        let (payload, control) = {
            let mut state = self.lock_state()?;
            let control = state
                .control(id)
                .ok_or_else(|| "That download is no longer running".to_string())?;
            let payload = state.mark_running(id)?;
            (payload, control)
        };
        control.resume();
        let _ = app.emit(PROGRESS_EVENT, payload.clone());
        Ok(payload)
    }

    pub fn cancel(&self, app: &AppHandle, id: u64) -> Result<Value, String> {
        let plan = {
            let mut state = self.lock_state()?;
            state.mark_cancelled(id)?
        };
        let _ = app.emit(PROGRESS_EVENT, plan.payload.clone());
        match plan.control {
            // The task owns the file handle, so it removes the partial file
            // once it notices the cancellation.
            Some(control) => control.cancel(),
            None => {
                let _ = fs::remove_file(&plan.path);
                let _ = app.emit(
                    FINISHED_EVENT,
                    json!({
                        "tabId": plan.tab_id,
                        "url": plan.url,
                        "path": plan.path.to_string_lossy(),
                        "success": false,
                    }),
                );
            }
        }
        Ok(plan.payload)
    }
}

fn with_state<T>(
    app: &AppHandle,
    action: impl FnOnce(&mut DownloadState) -> Result<T, String>,
) -> Result<T, String> {
    let manager = app.state::<DownloadManager>();
    let mut state = manager.lock_state()?;
    action(&mut state)
}

fn download_snapshot(app: &AppHandle, id: u64) -> Result<DownloadSnapshot, String> {
    with_state(app, |state| {
        let record = state.record(id)?;
        Ok(DownloadSnapshot {
            url: record.url.clone(),
            path: record.path.clone(),
            status: record.status,
        })
    })
}

fn publish_attempt(app: &AppHandle, id: u64, plan: StreamPlan) -> Result<(), String> {
    let payload = with_state(app, |state| state.start_attempt(id, plan))?;
    let _ = app.emit(PROGRESS_EVENT, payload);
    Ok(())
}

fn publish_received(app: &AppHandle, id: u64, received: u64) -> Result<(), String> {
    with_state(app, |state| state.set_received(id, received))
}

fn publish_progress_for(app: &AppHandle, id: u64) {
    if let Ok(payload) = with_state(app, |state| state.payload(id)) {
        let _ = app.emit(PROGRESS_EVENT, payload);
    }
}

fn finish_download(app: &AppHandle, id: u64, outcome: RunOutcome) {
    let finished = match with_state(app, |state| state.finish(id, &outcome)) {
        Ok(finished) => finished,
        Err(_) => return,
    };
    if finished.status == DownloadStatus::Cancelled {
        let _ = fs::remove_file(&finished.path);
    } else {
        // A cancelled download already reported its final state from the
        // command that cancelled it, so only the normal end is published here.
        let _ = app.emit(PROGRESS_EVENT, finished.payload);
    }
    let _ = app.emit(
        FINISHED_EVENT,
        json!({
            "tabId": finished.tab_id,
            "url": finished.url,
            "path": finished.path.to_string_lossy(),
            "success": finished.status == DownloadStatus::Completed,
        }),
    );
}

async fn run_download(app: AppHandle, id: u64, control: Arc<DownloadControl>) {
    let outcome = download_until_terminal(&app, id, &control).await;
    finish_download(&app, id, outcome);
}

async fn download_until_terminal(
    app: &AppHandle,
    id: u64,
    control: &DownloadControl,
) -> RunOutcome {
    let client = match Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(CONNECT_TIMEOUT)
        .build()
    {
        Ok(client) => client,
        Err(error) => {
            return RunOutcome::Failed(format!("Could not create the download client: {error}"))
        }
    };

    loop {
        let snapshot = match download_snapshot(app, id) {
            Ok(snapshot) => snapshot,
            Err(error) => return RunOutcome::Failed(error),
        };
        if snapshot.status == DownloadStatus::Cancelled {
            return RunOutcome::Cancelled;
        }
        if !is_downloadable_url(&snapshot.url) {
            return RunOutcome::Failed("Only HTTP and HTTPS downloads can be managed".into());
        }

        // The bytes on disk, not the counter, are what a resumed request has to
        // continue from, because a failed write never reaches the counter.
        let resumed_from = file_length(&snapshot.path);
        let mut request = client.get(snapshot.url.as_str());
        if let Some(range) = range_header_value(resumed_from) {
            request = request.header(RANGE, range);
        }
        let response = match request.send().await {
            Ok(response) => response,
            Err(error) => {
                return RunOutcome::Failed(format!("The download request failed: {error}"))
            }
        };
        let plan = match response_plan(resumed_from, &response) {
            Ok(plan) => plan,
            Err(error) => return RunOutcome::Failed(error),
        };
        let mut file = match open_destination(&snapshot.path, plan) {
            Ok(file) => file,
            Err(error) => return RunOutcome::Failed(error),
        };
        if let Err(error) = publish_attempt(app, id, plan) {
            return RunOutcome::Failed(error);
        }

        match stream_body(app, id, control, &mut file, response, plan).await {
            BodyOutcome::Completed => return RunOutcome::Completed,
            BodyOutcome::Cancelled => return RunOutcome::Cancelled,
            BodyOutcome::Failed(error) => return RunOutcome::Failed(error),
            // A pause returns the body instead of holding it, and the loop asks
            // for the remaining bytes with a range request.
            BodyOutcome::Paused => {}
        }
    }
}

fn response_plan(resumed_from: u64, response: &Response) -> Result<StreamPlan, String> {
    let status = response.status().as_u16();
    if status == 416 {
        return Err("The server rejected the resume range".into());
    }
    let content_range = response
        .headers()
        .get(CONTENT_RANGE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string);
    plan_stream(
        resumed_from,
        status,
        response.content_length(),
        content_range.as_deref(),
    )
}

enum BodyOutcome {
    Completed,
    Paused,
    Cancelled,
    Failed(String),
}

async fn stream_body(
    app: &AppHandle,
    id: u64,
    control: &DownloadControl,
    file: &mut File,
    mut response: Response,
    plan: StreamPlan,
) -> BodyOutcome {
    let mut received = plan.offset;
    let mut bytes_since_emit = 0u64;
    let mut last_emit = Instant::now();

    loop {
        match control.action() {
            ControlAction::Cancel => return BodyOutcome::Cancelled,
            ControlAction::Pause => {
                // The response stays alive while paused, so the connection is
                // not closed and the peer stops sending because nothing reads
                // the body. The resume then reconnects with a range request.
                if !control.wait_for_resume().await {
                    return BodyOutcome::Cancelled;
                }
                return BodyOutcome::Paused;
            }
            ControlAction::Continue => {}
        }

        match response.chunk().await {
            Ok(Some(chunk)) => {
                if let Err(error) = file.write_all(&chunk) {
                    return BodyOutcome::Failed(format!("Could not write the download: {error}"));
                }
                received += chunk.len() as u64;
                bytes_since_emit += chunk.len() as u64;
                if let Err(error) = publish_received(app, id, received) {
                    return BodyOutcome::Failed(error);
                }
                if should_emit_progress(bytes_since_emit, last_emit.elapsed()) {
                    bytes_since_emit = 0;
                    last_emit = Instant::now();
                    publish_progress_for(app, id);
                }
            }
            Ok(None) => {
                return if plan.is_complete(received) {
                    BodyOutcome::Completed
                } else {
                    BodyOutcome::Failed("The server closed the download before it finished".into())
                };
            }
            Err(error) => {
                return BodyOutcome::Failed(format!("The download stopped: {error}"));
            }
        }
    }
}

#[tauri::command]
pub fn browser_download_list(app: AppHandle) -> Value {
    let manager = app.state::<DownloadManager>();
    let payload = match manager.lock_state() {
        Ok(state) => state.list_payload(),
        Err(error) => json!({ "downloads": [], "error": error }),
    };
    payload
}

#[tauri::command]
pub fn browser_download_pause(app: AppHandle, id: u64) -> Result<Value, String> {
    app.state::<DownloadManager>().pause(&app, id)
}

#[tauri::command]
pub fn browser_download_resume(app: AppHandle, id: u64) -> Result<Value, String> {
    app.state::<DownloadManager>().resume(&app, id)
}

#[tauri::command]
pub fn browser_download_cancel(app: AppHandle, id: u64) -> Result<Value, String> {
    app.state::<DownloadManager>().cancel(&app, id)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn state_with_download() -> (DownloadState, u64) {
        let mut state = DownloadState::default();
        let (id, _control, _payload) = state.register(
            7,
            "https://example.com/file.zip".to_string(),
            PathBuf::from("downloads/file.zip"),
        );
        (state, id)
    }

    #[test]
    fn percent_stays_unknown_until_the_total_is_known() {
        assert_eq!(percent_of(0, None), 0.0);
        assert_eq!(percent_of(512, None), 0.0);
        assert_eq!(percent_of(512, Some(0)), 0.0);
    }

    #[test]
    fn percent_tracks_received_bytes_with_one_decimal() {
        assert_eq!(percent_of(0, Some(200)), 0.0);
        assert_eq!(percent_of(50, Some(200)), 25.0);
        assert_eq!(percent_of(1, Some(3)), 33.3);
        assert_eq!(percent_of(200, Some(200)), 100.0);
        assert_eq!(percent_of(300, Some(200)), 100.0);
    }

    #[test]
    fn range_header_is_only_sent_when_bytes_exist() {
        assert_eq!(range_header_value(0), None);
        assert_eq!(range_header_value(1024).as_deref(), Some("bytes=1024-"));
    }

    #[test]
    fn content_range_is_parsed_from_the_header() {
        assert_eq!(
            parse_content_range("bytes 120-499/500"),
            Some(ContentRange {
                start: 120,
                total: Some(500)
            })
        );
        assert_eq!(
            parse_content_range("bytes 0-0/*"),
            Some(ContentRange {
                start: 0,
                total: None
            })
        );
        assert_eq!(parse_content_range("bytes"), None);
        assert_eq!(parse_content_range("items 0-1/2"), None);
        assert_eq!(parse_content_range("bytes x-1/2"), None);
    }

    #[test]
    fn accepted_range_continues_the_partial_file() {
        let plan = plan_stream(120, 206, Some(380), Some("bytes 120-499/500")).unwrap();

        assert_eq!(plan.offset, 120);
        assert!(plan.append);
        assert_eq!(plan.total, Some(500));
    }

    #[test]
    fn ignored_range_restarts_the_file_from_zero() {
        let plan = plan_stream(120, 200, Some(500), None).unwrap();

        assert_eq!(plan.offset, 0);
        assert!(!plan.append);
        assert_eq!(plan.total, Some(500));
    }

    #[test]
    fn range_total_falls_back_to_the_content_length() {
        let plan = plan_stream(120, 206, Some(380), Some("bytes 120-499/*")).unwrap();

        assert_eq!(plan.offset, 120);
        assert_eq!(plan.total, Some(500));
    }

    #[test]
    fn a_range_answered_from_the_wrong_offset_is_refused() {
        assert!(plan_stream(120, 206, None, Some("bytes 0-99/500")).is_err());
    }

    #[test]
    fn error_responses_are_refused() {
        assert!(plan_stream(0, 404, Some(0), None).is_err());
        assert!(plan_stream(0, 500, None, None).is_err());
    }

    #[test]
    fn completion_needs_the_expected_length() {
        assert!(plan_stream(0, 200, Some(100), None)
            .unwrap()
            .is_complete(100));
        assert!(!plan_stream(0, 200, Some(100), None)
            .unwrap()
            .is_complete(99));
        assert!(plan_stream(0, 200, None, None).unwrap().is_complete(1));
    }

    #[test]
    fn task_outcomes_map_to_final_statuses() {
        assert_eq!(RunOutcome::Completed.status(), DownloadStatus::Completed);
        assert_eq!(
            RunOutcome::Failed("disk full".into()).status(),
            DownloadStatus::Failed
        );
        assert_eq!(RunOutcome::Cancelled.status(), DownloadStatus::Cancelled);
        assert_eq!(
            RunOutcome::Failed("disk full".into()).error(),
            Some("disk full")
        );
        assert_eq!(RunOutcome::Completed.error(), None);
    }

    #[test]
    fn progress_emits_are_throttled() {
        assert!(!should_emit_progress(0, Duration::ZERO));
        assert!(!should_emit_progress(
            PROGRESS_BYTES_STEP - 1,
            Duration::from_millis(249)
        ));
        assert!(should_emit_progress(
            PROGRESS_BYTES_STEP,
            Duration::from_millis(10)
        ));
        assert!(should_emit_progress(1024, PROGRESS_INTERVAL));
    }

    #[test]
    fn only_http_and_https_urls_are_downloadable() {
        assert!(is_downloadable_url("https://example.com/file.zip"));
        assert!(is_downloadable_url("http://localhost:8080/file.zip"));
        assert!(!is_downloadable_url("file:///C:/secret.txt"));
        assert!(!is_downloadable_url("data:text/plain,hello"));
        assert!(!is_downloadable_url("not a url"));
    }

    #[test]
    fn unknown_download_ids_are_refused() {
        let mut state = DownloadState::default();

        assert!(state.record(99).is_err());
        assert!(state.payload(99).is_err());
        assert!(state.mark_paused(99).is_err());
        assert!(state.mark_running(99).is_err());
        assert!(state.mark_cancelled(99).is_err());
        assert!(state.set_received(99, 1).is_err());
        assert!(state.finish(99, &RunOutcome::Completed).is_err());
    }

    #[test]
    fn pause_and_resume_follow_the_status_machine() {
        let (mut state, id) = state_with_download();

        assert_eq!(state.mark_paused(id).unwrap()["status"], "paused");
        assert!(state.mark_paused(id).is_err());
        assert_eq!(state.mark_running(id).unwrap()["status"], "running");
        assert!(state.mark_running(id).is_err());
    }

    #[test]
    fn a_finished_download_refuses_further_transitions() {
        let (mut state, id) = state_with_download();

        state.finish(id, &RunOutcome::Completed).unwrap();

        assert!(state.mark_paused(id).is_err());
        assert!(state.mark_running(id).is_err());
        assert!(state.mark_cancelled(id).is_err());
        assert!(state.control(id).is_none());
    }

    #[test]
    fn cancelling_returns_the_partial_path_and_status() {
        let (mut state, id) = state_with_download();

        let plan = state.mark_cancelled(id).unwrap();

        assert_eq!(plan.payload["status"], "cancelled");
        assert_eq!(plan.payload["success"], false);
        assert_eq!(plan.path, PathBuf::from("downloads/file.zip"));
        assert_eq!(plan.tab_id, 7);
        assert!(state.mark_cancelled(id).is_err());
    }

    #[test]
    fn a_requested_cancellation_survives_a_late_task_failure() {
        let (mut state, id) = state_with_download();

        state.mark_cancelled(id).unwrap();
        let finished = state
            .finish(id, &RunOutcome::Failed("connection reset".into()))
            .unwrap();

        assert_eq!(finished.status, DownloadStatus::Cancelled);
        assert_eq!(finished.payload["status"], "cancelled");
        assert_eq!(finished.payload["error"], Value::Null);
    }

    #[test]
    fn control_flags_map_to_task_actions() {
        let control = DownloadControl::default();

        assert_eq!(control.action(), ControlAction::Continue);
        control.pause();
        assert_eq!(control.action(), ControlAction::Pause);
        control.resume();
        assert_eq!(control.action(), ControlAction::Continue);
        control.cancel();
        assert_eq!(control.action(), ControlAction::Cancel);
    }

    #[test]
    fn waiting_for_a_resume_only_finishes_after_the_signal() {
        use std::{
            future::Future,
            pin::pin,
            task::{Context, Waker},
        };

        let control = DownloadControl::default();
        let mut context = Context::from_waker(Waker::noop());

        control.pause();
        let mut waiting = pin!(control.wait_for_resume());
        assert!(waiting.as_mut().poll(&mut context).is_pending());
        control.resume();
        assert_eq!(waiting.as_mut().poll(&mut context), Poll::Ready(true));

        control.pause();
        let mut waiting = pin!(control.wait_for_resume());
        assert!(waiting.as_mut().poll(&mut context).is_pending());
        control.cancel();
        assert_eq!(waiting.as_mut().poll(&mut context), Poll::Ready(false));
    }

    #[test]
    fn progress_payload_carries_the_frontend_contract_fields() {
        let (state, id) = state_with_download();

        let payload = state.payload(id).unwrap();

        for field in [
            "id", "tabId", "url", "path", "received", "total", "percent", "status", "success",
            "error",
        ] {
            assert!(payload.get(field).is_some(), "missing {field}");
        }
        assert_eq!(payload["status"], "running");
        assert_eq!(payload["success"], false);
        assert_eq!(payload["total"], Value::Null);
        assert_eq!(payload["percent"], 0.0);
        assert_eq!(payload["received"], 0);
    }

    #[test]
    fn a_resumed_attempt_reports_the_append_offset() {
        let (mut state, id) = state_with_download();

        let payload = state
            .start_attempt(
                id,
                plan_stream(120, 206, Some(380), Some("bytes 120-499/500")).unwrap(),
            )
            .unwrap();

        assert_eq!(payload["received"], 120);
        assert_eq!(payload["total"], 500);
        assert_eq!(payload["percent"], 24.0);
    }

    #[test]
    fn failed_downloads_report_their_reason() {
        let (mut state, id) = state_with_download();

        let finished = state
            .finish(id, &RunOutcome::Failed("disk full".into()))
            .unwrap();

        assert_eq!(finished.payload["status"], "failed");
        assert_eq!(finished.payload["success"], false);
        assert_eq!(finished.payload["error"], "disk full");
        assert_eq!(finished.url, "https://example.com/file.zip");
        assert_eq!(finished.tab_id, 7);
    }

    #[test]
    fn completed_downloads_report_success() {
        let (mut state, id) = state_with_download();

        let finished = state.finish(id, &RunOutcome::Completed).unwrap();

        assert_eq!(finished.payload["status"], "completed");
        assert_eq!(finished.payload["success"], true);
        assert_eq!(finished.payload["error"], Value::Null);
    }

    #[test]
    fn the_list_contains_every_download_in_creation_order() {
        let mut state = DownloadState::default();
        state.register(
            1,
            "https://example.com/a.zip".to_string(),
            PathBuf::from("a.zip"),
        );
        let (second, _control, _payload) = state.register(
            2,
            "https://example.com/b.zip".to_string(),
            PathBuf::from("b.zip"),
        );

        let payload = state.list_payload();
        let downloads = payload["downloads"].as_array().unwrap();

        assert_eq!(downloads.len(), 2);
        assert_eq!(downloads[0]["url"], "https://example.com/a.zip");
        assert_eq!(downloads[1]["url"], "https://example.com/b.zip");
        assert_eq!(downloads[1]["id"], second);
    }
}
