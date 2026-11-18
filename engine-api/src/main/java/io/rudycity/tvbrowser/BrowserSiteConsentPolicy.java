package io.rudycity.tvbrowser;

/**
 * A website permission is only valid while its original document is active.
 * Pure Java so every Android flavor can regression-test the consent boundary.
 */
public final class BrowserSiteConsentPolicy {
    private BrowserSiteConsentPolicy() { }

    public static boolean mayHonorConsent(boolean sessionAlive, boolean tabVisible,
                                          int requestedEpoch, int currentEpoch) {
        return sessionAlive && tabVisible && requestedEpoch == currentEpoch;
    }
}
