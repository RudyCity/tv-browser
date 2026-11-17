package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class PopupPolicyTest {
    @Test public void rejectsUnsafePopupUrls() {
        assertTrue(PopupPolicy.isPreviewable("https://example.org/path?a=1"));
        assertTrue(PopupPolicy.isPreviewable("http://192.168.1.1/"));
        assertFalse(PopupPolicy.isPreviewable("intent://play/#Intent;end"));
        assertFalse(PopupPolicy.isPreviewable("market://details?id=app"));
        assertFalse(PopupPolicy.isPreviewable("javascript:alert(1)"));
        assertFalse(PopupPolicy.isPreviewable("file:///tmp/setup.apk"));
        assertFalse(PopupPolicy.isPreviewable("https://user:pass@example.org"));
        assertFalse(PopupPolicy.isPreviewable("https://example.org/\nmalicious"));
        assertFalse(PopupPolicy.isPreviewable("https://example.org/" +
                new String(new char[4096]).replace('\0', 'x')));
    }

    @Test public void givesAccurateHostAndEncryptionHint() {
        assertEquals("cdn.example.org", PopupPolicy.host("https://cdn.example.org:8443/setup.apk?a=1"));
        assertNull(PopupPolicy.host("data:text/html,unsafe"));
        assertTrue(PopupPolicy.usesPlainHttp("HTTP://legacy.test/link"));
        assertFalse(PopupPolicy.usesPlainHttp("https://example.org"));
    }

    @Test public void executableHintsUsePathNotQuery() {
        assertTrue(PopupPolicy.mightInstallSoftware("https://cdn.example.org/update.APK?token=1"));
        assertTrue(PopupPolicy.mightInstallSoftware("https://cdn.example.org/install.xapk"));
        assertTrue(PopupPolicy.mightInstallSoftware("https://cdn.example.org/batch.ps1"));
        assertFalse(PopupPolicy.mightInstallSoftware("https://apk.example.org/article?file=update.apk"));
        assertFalse(PopupPolicy.mightInstallSoftware("https://example.org/article"));
    }
}
