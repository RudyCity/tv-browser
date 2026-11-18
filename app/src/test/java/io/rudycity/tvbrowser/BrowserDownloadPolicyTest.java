package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowserDownloadPolicyTest {
    @Test public void normalizesUntrustedNames() {
        assertEquals("download.bin", BrowserDownloadPolicy.safeFilename(null));
        assertEquals("download.bin", BrowserDownloadPolicy.safeFilename(".."));
        assertEquals("report.pdf", BrowserDownloadPolicy.safeFilename("report.pdf"));
        assertEquals("a_b_c.apk", BrowserDownloadPolicy.safeFilename("a/b\\c.apk"));
        assertEquals("_.apk", BrowserDownloadPolicy.safeFilename(".apk"));
        assertEquals("_..private", BrowserDownloadPolicy.safeFilename("..private"));
    }

    @Test public void boundsServerSuppliedNamesWhileRetainingExtension() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 180; i++) longName.append('A');
        String apk = BrowserDownloadPolicy.safeFilename(longName.toString() + ".apk");
        assertEquals(120, apk.length());
        assertTrue(apk.endsWith(".apk"));
        assertEquals(120, BrowserDownloadPolicy.safeFilename(longName.toString()).length());
    }
}
