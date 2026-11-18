package io.rudycity.tvbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

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
    private FilePrompt uploadPrompt;
    private GeckoResult<PromptResponse> uploadResponse;
    private int uploadCode = -1;
    private int pdfCode = -1;

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

    @Override public GeckoResult<PromptResponse> onFilePrompt(
            GeckoSession from, FilePrompt prompt) {
        if (!eligible() || uploadResponse != null || prompt.type == FilePrompt.Type.FOLDER)
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
            session.saveAsPdf().accept(source -> new Thread(() -> {
                boolean completed = false;
                try (InputStream input = source;
                     OutputStream output = activity.getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IOException("No output stream");
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = input.read(bytes)) != -1) output.write(bytes, 0, read);
                    output.flush();
                    completed = true;
                } catch (IOException | RuntimeException error) {
                    // Do not log site URL or PDF data.
                }
                final boolean success = completed;
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        Toast.makeText(activity, success ? "PDF saved" : "PDF export failed",
                                Toast.LENGTH_LONG).show();
                });
            }, "rudy-pdf-export").start(), error -> activity.runOnUiThread(() -> {
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
        if (consentDialog != null) consentDialog.dismiss();
        consentDialog = null;
        rejectUpload();
        pdfCode = -1;
    }
}
