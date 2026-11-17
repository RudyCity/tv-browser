package io.rudycity.tvbrowser;

/** Trusted built-in page styling. Userscript text is stored separately and opt-in. */
public final class ExtensionScripts {
    private ExtensionScripts() {}

    // A page can restyle itself later; this is intentionally a best-effort document-end feature.
    public static final String DARK_MODE =
            "(function(){" +
            "var id='__tvbrowser_dark_mode';" +
            "if(document.getElementById(id))return;" +
            "var style=document.createElement('style');style.id=id;" +
            "style.textContent='html{background:#0e1118!important;" +
            "filter:invert(1) hue-rotate(180deg)!important;}" +
            "img,video,picture,canvas,svg,iframe{filter:invert(1) hue-rotate(180deg)!important;}';" +
            "(document.head||document.documentElement).appendChild(style);" +
            "})();";
}
