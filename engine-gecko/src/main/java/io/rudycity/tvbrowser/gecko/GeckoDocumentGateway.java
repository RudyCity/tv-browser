package io.rudycity.tvbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.InputType;
import org.mozilla.geckoview.AllowOrDeny;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Gecko-only Android SAF file chooser and Save-as-PDF gateway. Never exposes filesystem paths. */
final class GeckoDocumentGateway implements GeckoSession.PromptDelegate {
    private static final AtomicInteger NEXT_REQUEST = new AtomicInteger(9000);
    private final Activity activity;
    private final GeckoSession session;
    private final View view;
    private String pageUrl;
    private boolean destroyed;
    private AlertDialog consentDialog;
    private AlertDialog webDialog;
    private GeckoResult<PromptResponse> pendingWebResponse;
    private BasePrompt pendingWebPrompt;
    private FilePrompt uploadPrompt;
    private GeckoResult<PromptResponse> uploadResponse;
    private int uploadCode = -1;
    private int pdfCode = -1;
    private WebResponse pendingDownload;
    private int downloadCode = -1;
    private AlertDialog downloadConsent;

    GeckoDocumentGateway(Activity activity, GeckoSession session, View view) {
        this.activity = activity;
        this.session = session;
        this.view = view;
    }

    void setPageUrl(String value) { pageUrl = value; }

    private boolean eligible() {
        if (destroyed || activity.isFinishing() || activity.isDestroyed() ||
                view.getVisibility() != View.VISIBLE || pageUrl == null) return false;
        Uri uri = Uri.parse(pageUrl);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    }

    private static int newCode() { return NEXT_REQUEST.getAndIncrement(); }

    private static void closeResponse(WebResponse response) {
        if (response == null || response.body == null) return;
        try { response.body.close(); }
        catch (IOException ignored) { /* closing a response must not crash UI */ }
    }

    private static String downloadName(String url) {
        String segment;
        try { segment = Uri.parse(url).getLastPathSegment(); }
        catch (RuntimeException invalid) { segment = null; }
        if (segment == null || segment.isEmpty()) segment = "download.bin";
        segment = segment.replaceAll("[^A-Za-z0-9._-]", "_");
        if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
            segment = "download.bin";
        return segment.length() > 120 ? segment.substring(0, 120) : segment;
    }

    /** Preserve Gecko's authenticated HTTPS response body; never re-fetch its URL. */
    void offerDownload(WebResponse response) {
        if (response == null) return;
        Uri uri;
        try { uri = Uri.parse(response.uri); }
        catch (RuntimeException invalid) { closeResponse(response); return; }
        if (destroyed || pendingDownload != null || downloadConsent != null ||
                response.body == null || !response.isSecure ||
                !"https".equalsIgnoreCase(uri.getScheme()) ||
                uri.getHost() == null || !eligible()) {
            closeResponse(response);
            return;
        }
        pendingDownload = response;
        String name = downloadName(response.uri);
        boolean installer = name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") ||
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe") ||
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".msi");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Save downloaded file?")
                .setMessage("Source domain: " + uri.getHost() +
                        "\n\nFull link:\n" + limit(response.uri, 1200) +
                        "\n\nFile name: " + name +
                        (installer ? "\n\nWARNING: Executable/installer file. Never install untrusted apps." : "") +
                        "\n\nThis uses the original HTTPS response. The file will not be opened automatically.")
                .setNegativeButton("Block", (ignored, which) -> rejectDownload())
                .setPositiveButton("Choose save location", (ignored, which) -> {
                    if (destroyed || pendingDownload != response) {
                        rejectDownload();
                        return;
                    }
                    Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    create.addCategory(Intent.CATEGORY_OPENABLE);
                    create.setType("application/octet-stream");
                    create.putExtra(Intent.EXTRA_TITLE, name);
                    downloadCode = newCode();
                    try { activity.startActivityForResult(create, downloadCode); }
                    catch (RuntimeException error) { rejectDownload(); }
                })
                .create();
        downloadConsent = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (downloadConsent == dialog) downloadConsent = null;
            if (downloadCode == -1) rejectDownload();
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    private void rejectDownload() {
        WebResponse response = pendingDownload;
        pendingDownload = null;
        downloadCode = -1;
        closeResponse(response);
    }

    private void copyToDocument(InputStream source, Uri destination, String label) {
        new Thread(() -> {
            boolean completed = false;
            try (InputStream input = source;
                 OutputStream output = activity.getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IOException("No output stream");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
                completed = true;
            } catch (IOException | RuntimeException error) {
                // Do not log authenticated bytes, source URL or user document.
            }
            final boolean success = completed;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed())
                    Toast.makeText(activity, success ? label + " saved" : label + " failed",
                            Toast.LENGTH_LONG).show();
            });
        }, "rudy-document-save").start();
    }

    private interface WebChoice {
        PromptResponse accept();
        PromptResponse reject();
    }

    private static String limit(String input, int max) {
        if (input == null) return "";
        return input.length() > max ? input.substring(0, max) + "…" : input;
    }

    private void completeWebPrompt(PromptResponse selection) {
        GeckoResult<PromptResponse> result = pendingWebResponse;
        pendingWebResponse = null;
        pendingWebPrompt = null;
        if (result != null) result.complete(selection);
    }

    private GeckoResult<PromptResponse> webPrompt(BasePrompt prompt,
            String title, String message, View input, String affirmative, String negative,
            WebChoice resultChoice) {
        if (!eligible() || pendingWebResponse != null || uploadResponse != null)
            return GeckoResult.fromValue(resultChoice.reject());
        final GeckoResult<PromptResponse> result = new GeckoResult<>();
        pendingWebPrompt = prompt;
        pendingWebResponse = result;
        Uri site = Uri.parse(pageUrl);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(site.getHost() + "\n\n" + limit(message, 850))
                .setNegativeButton(negative, (ignored, which) ->
                        completeWebPrompt(resultChoice.reject()))
                .setPositiveButton(affirmative, (ignored, which) ->
                        completeWebPrompt(resultChoice.accept()));
        if (input != null) builder.setView(input);
        AlertDialog dialog = builder.create();
        webDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (webDialog == dialog) webDialog = null;
            if (pendingWebResponse == result)
                completeWebPrompt(resultChoice.reject());
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onAlertPrompt(
            GeckoSession from, AlertPrompt prompt) {
        return webPrompt(prompt, "Website message", prompt.message,
                null, "OK", "Close", new WebChoice() {
                    @Override public PromptResponse accept() { return prompt.dismiss(); }
                    @Override public PromptResponse reject() { return prompt.dismiss(); }
                });
    }

    @Override public GeckoResult<PromptResponse> onButtonPrompt(
            GeckoSession from, ButtonPrompt prompt) {
        return webPrompt(prompt, "Website confirmation", prompt.message,
                null, "Confirm", "Cancel", new WebChoice() {
                    @Override public PromptResponse accept() {
                        return prompt.confirm(ButtonPrompt.Type.POSITIVE);
                    }
                    @Override public PromptResponse reject() {
                        return prompt.confirm(ButtonPrompt.Type.NEGATIVE);
                    }
                });
    }

    @Override public GeckoResult<PromptResponse> onTextPrompt(
            GeckoSession from, TextPrompt prompt) {
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        if (prompt.defaultValue != null) field.setText(prompt.defaultValue);
        field.setPadding(24, 12, 24, 12);
        return webPrompt(prompt, "Website text input", prompt.message,
                field, "Submit", "Cancel", new WebChoice() {
                    @Override public PromptResponse accept() {
                        return prompt.confirm(field.getText().toString());
                    }
                    @Override public PromptResponse reject() { return prompt.dismiss(); }
                });
    }

    @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(
            GeckoSession from, BeforeUnloadPrompt prompt) {
        return webPrompt(prompt, "Leave this website?",
                "The page may contain unsaved changes. Leave this page?",
                null, "Leave", "Stay", new WebChoice() {
                    @Override public PromptResponse accept() {
                        return prompt.confirm(AllowOrDeny.ALLOW);
                    }
                    @Override public PromptResponse reject() {
                        return prompt.confirm(AllowOrDeny.DENY);
                    }
                });
    }

    private static void flattenChoices(ChoicePrompt.Choice[] entries,
            List<ChoicePrompt.Choice> flattened, String parent) {
        if (entries == null) return;
        for (ChoicePrompt.Choice choice : entries) {
            if (flattened.size() >= 100) return;
            if (choice.items != null) {
                flattenChoices(choice.items, flattened, parent + choice.label + " / ");
            } else if (!choice.separator) flattened.add(choice);
        }
    }

    @Override public GeckoResult<PromptResponse> onChoicePrompt(
            GeckoSession from, ChoicePrompt prompt) {
        if (!eligible() || pendingWebResponse != null || uploadResponse != null)
            return GeckoResult.fromValue(prompt.dismiss());
        final List<ChoicePrompt.Choice> choices = new ArrayList<>();
        flattenChoices(prompt.choices, choices, "");
        if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss());
        final GeckoResult<PromptResponse> result = new GeckoResult<>();
        pendingWebPrompt = prompt;
        pendingWebResponse = result;
        String[] names = new String[choices.size()];
        boolean[] selected = new boolean[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            ChoicePrompt.Choice choice = choices.get(i);
            names[i] = (choice.disabled ? "(Disabled) " : "") + limit(choice.label, 90);
            selected[i] = choice.selected;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Choose from " + Uri.parse(pageUrl).getHost())
                .setNegativeButton("Cancel", (ignored, which) ->
                        completeWebPrompt(prompt.dismiss()));
        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            builder.setMultiChoiceItems(names, selected, (dialog, index, checked) -> {
                if (choices.get(index).disabled) {
                    ((AlertDialog) dialog).getListView().setItemChecked(index, selected[index]);
                    return;
                }
                selected[index] = checked;
            });
            builder.setPositiveButton("Done", (ignored, which) -> {
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < selected.length; i++)
                    if (selected[i] && !choices.get(i).disabled) ids.add(choices.get(i).id);
                completeWebPrompt(prompt.confirm(ids.toArray(new String[0])));
            });
        } else {
            builder.setItems(names, (ignored, index) -> {
                if (choices.get(index).disabled) {
                    completeWebPrompt(prompt.dismiss());
                } else completeWebPrompt(prompt.confirm(choices.get(index).id));
            });
        }
        AlertDialog dialog = builder.create();
        webDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (webDialog == dialog) webDialog = null;
            if (pendingWebResponse == result) completeWebPrompt(prompt.dismiss());
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        return result;
    }

    @Override public GeckoResult<PromptResponse> onFilePrompt(
            GeckoSession from, FilePrompt prompt) {
        if (!eligible() || uploadResponse != null || pendingWebResponse != null ||
                prompt.type == FilePrompt.Type.FOLDER)
            return GeckoResult.fromValue(prompt.dismiss());
        uploadPrompt = prompt;
        uploadResponse = new GeckoResult<>();
        final GeckoResult<PromptResponse> result = uploadResponse;
        Uri origin = Uri.parse(pageUrl);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Upload file to website?")
                .setMessage("Website: " + origin.getScheme() + "://" + origin.getHost() +
                        "\n\nOnly documents selected in Android's file picker are shared. " +
                        "Block uploads to untrusted sites.")
                .setNegativeButton("Block", (ignored, which) -> rejectUpload())
                .setPositiveButton("Choose files", (ignored, which) -> {
                    if (!eligible()) { rejectUpload(); return; }
                    Intent select = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    select.addCategory(Intent.CATEGORY_OPENABLE);
                    select.setType("*/*");
                    if (prompt.mimeTypes != null && prompt.mimeTypes.length > 0) {
                        if (prompt.mimeTypes.length == 1 && prompt.mimeTypes[0] != null)
                            select.setType(prompt.mimeTypes[0]);
                        else select.putExtra(Intent.EXTRA_MIME_TYPES, prompt.mimeTypes);
                    }
                    if (prompt.type == FilePrompt.Type.MULTIPLE)
                        select.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    int code = newCode();
                    uploadCode = code;
                    try { activity.startActivityForResult(select, code); }
                    catch (RuntimeException error) { rejectUpload(); }
                }).create();
        consentDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (consentDialog == dialog) consentDialog = null;
            if (uploadCode == -1) rejectUpload();
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        return result;
    }

    private void rejectUpload() {
        FilePrompt prompt = uploadPrompt;
        GeckoResult<PromptResponse> response = uploadResponse;
        uploadPrompt = null;
        uploadResponse = null;
        uploadCode = -1;
        if (prompt != null && response != null) {
            try { response.complete(prompt.dismiss()); }
            catch (RuntimeException ignored) { /* already completed */ }
        }
    }

    boolean savePagePdf() {
        if (!eligible() || pdfCode != -1) return false;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/pdf");
        create.putExtra(Intent.EXTRA_TITLE, "Rudy-Browser-page.pdf");
        int code = newCode();
        pdfCode = code;
        try { activity.startActivityForResult(create, code); }
        catch (RuntimeException failure) { pdfCode = -1; return false; }
        return true;
    }

    boolean onActivityResult(int request, int result, Intent data) {
        if (request == downloadCode && pendingDownload != null) {
            WebResponse response = pendingDownload;
            pendingDownload = null;
            downloadCode = -1;
            if (destroyed || result != Activity.RESULT_OK || data == null ||
                    data.getData() == null || !"content".equals(data.getData().getScheme())) {
                closeResponse(response);
                return true;
            }
            copyToDocument(response.body, data.getData(), "Download");
            return true;
        }
        if (request == uploadCode && uploadResponse != null) {
            FilePrompt prompt = uploadPrompt;
            GeckoResult<PromptResponse> response = uploadResponse;
            uploadPrompt = null;
            uploadResponse = null;
            uploadCode = -1;
            if (destroyed || result != Activity.RESULT_OK || data == null) {
                response.complete(prompt.dismiss());
                return true;
            }
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < Math.min(clip.getItemCount(), 8); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null && "content".equals(uri.getScheme())) uris.add(uri);
                }
            } else if (data.getData() != null &&
                    "content".equals(data.getData().getScheme())) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) response.complete(prompt.dismiss());
            else if (prompt.type == FilePrompt.Type.MULTIPLE)
                response.complete(prompt.confirm(activity.getApplicationContext(), uris.toArray(new Uri[0])));
            else response.complete(prompt.confirm(activity.getApplicationContext(), uris.get(0)));
            return true;
        }
        if (request == pdfCode) {
            pdfCode = -1;
            if (destroyed || result != Activity.RESULT_OK || data == null ||
                    data.getData() == null || !"content".equals(data.getData().getScheme())) return true;
            final Uri destination = data.getData();
            session.saveAsPdf().accept(source -> copyToDocument(source, destination, "PDF"),
                    error -> activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed())
                    Toast.makeText(activity, "PDF generation failed", Toast.LENGTH_LONG).show();
            }));
            return true;
        }
        return false;
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        if (pendingWebResponse != null && pendingWebPrompt != null)
            completeWebPrompt(pendingWebPrompt.dismiss());
        if (webDialog != null) webDialog.dismiss();
        webDialog = null;
        if (consentDialog != null) consentDialog.dismiss();
        consentDialog = null;
        rejectUpload();
        pdfCode = -1;
        if (downloadConsent != null) downloadConsent.dismiss();
        downloadConsent = null;
        rejectDownload();
    }
}
