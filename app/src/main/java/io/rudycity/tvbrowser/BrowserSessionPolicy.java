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

    /** A user-approved popup from a private tab still belongs in private mode. */
    public static boolean popupMustStayPrivate(boolean openerPrivate) {
        return openerPrivate;
    }
}
