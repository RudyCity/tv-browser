package io.rudycity.tvbrowser;

/** Browser session isolation rules shared by crash recovery and popup handling. */
public final class BrowserSessionPolicy {
    private BrowserSessionPolicy() { }

    /** Private page state must never be migrated to a normal/shared engine. */
    public static boolean canTransferUrl(boolean privateMode) {
        return !privateMode;
    }

    /** Automatically falling back to WebView is never safe for private Gecko tabs. */
    public static boolean canAutoFallback(boolean privateMode, int attempts) {
        return !privateMode && attempts == 1;
    }

    /**
     * Consent for a popup cannot be replayed after navigation, renderer/tab
     * switching, or a redirect even when it comes back to the same URL.
     */
    public static boolean popupApprovalCurrent(boolean sameTabAndEngine,
                                               long capturedSequence,
                                               long currentSequence,
                                               String capturedUrl,
                                               String currentUrl) {
        return sameTabAndEngine && capturedSequence >= 0 &&
                capturedSequence == currentSequence &&
                PopupPolicy.isPreviewable(capturedUrl) &&
                capturedUrl.equals(currentUrl);
    }

    /** A user-approved popup from a private tab still belongs in private mode. */
    public static boolean popupMustStayPrivate(boolean openerPrivate) {
        return openerPrivate;
    }
}
