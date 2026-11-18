package io.rudycity.tvbrowser;

import org.junit.Test;
import static org.junit.Assert.*;

/** Prevent permission approval from following navigation or a hidden tab. */
public final class BrowserSiteConsentPolicyTest {
    @Test public void onlyTheOriginalVisibleSessionMayReceiveConsent() {
        assertTrue(BrowserSiteConsentPolicy.mayHonorConsent(true, true, 4, 4));
        assertFalse(BrowserSiteConsentPolicy.mayHonorConsent(true, false, 4, 4));
        assertFalse(BrowserSiteConsentPolicy.mayHonorConsent(false, true, 4, 4));
        assertFalse(BrowserSiteConsentPolicy.mayHonorConsent(true, true, 4, 5));
        assertFalse(BrowserSiteConsentPolicy.mayHonorConsent(true, true, 5, 4));
    }

    @Test public void existingSessionCannotReusePriorNavigationConsent() {
        assertFalse(BrowserSiteConsentPolicy.mayHonorConsent(true, true, 0, 1));
        assertTrue(BrowserSiteConsentPolicy.mayHonorConsent(true, true, 0, 0));
    }
}
