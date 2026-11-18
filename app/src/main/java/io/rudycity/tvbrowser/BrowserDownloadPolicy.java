package io.rudycity.tvbrowser;

/** Bounded filename for untrusted browser download headers. */
public final class BrowserDownloadPolicy {
    private static final int MAX_LENGTH = 120;
    private BrowserDownloadPolicy() { }

    public static String safeFilename(String supplied) {
        if (supplied == null || supplied.isEmpty()) return "download.bin";
        String cleaned = supplied.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned))
            return "download.bin";
        if (cleaned.charAt(0) == '.') cleaned = "_" + cleaned;
        if (cleaned.length() <= MAX_LENGTH) return cleaned;

        int dot = cleaned.lastIndexOf('.');
        String extension = dot > 0 && cleaned.length() - dot <= 13
                ? cleaned.substring(dot) : "";
        return cleaned.substring(0, MAX_LENGTH - extension.length()) + extension;
    }
}
