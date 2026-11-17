package io.rudycity.tvbrowser;

/** Keyboard-only spatial link selection when a page has no built-in D-pad handling. */
public final class TvNavigation {
    private TvNavigation() {}

    public static final String INSTALL = "(function(){" +
            "if(window.__tvNav)return;" +
            "var active=null,oldOutline='';" +
            "function options(){var n=document.querySelectorAll('a[href],button,input,select,textarea,summary,video,[role=button],[tabindex]'),out=[];" +
            "for(var i=0;i<n.length;i++){var e=n[i],r=e.getBoundingClientRect();" +
            "if(r.width>1&&r.height>1&&r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth&&" +
            "getComputedStyle(e).visibility!=='hidden'&&getComputedStyle(e).display!=='none')out.push(e);}" +
            "return out;}" +
            "function highlight(e){if(active){active.style.outline=oldOutline;}active=e;" +
            "if(e){oldOutline=e.style.outline;e.style.outline='4px solid #58b5ff';" +
            "try{e.focus({preventScroll:true});}catch(x){try{e.focus();}catch(y){}}" +
            "e.scrollIntoView(false);}}" +
            "window.__tvNav={move:function(dx,dy){var nodes=options(),base=active&&document.contains(active)?active:document.activeElement;" +
            "if(!base||base===document.body||nodes.indexOf(base)<0){if(nodes.length){highlight(nodes[0]);return;}" +
            "window.scrollBy(dx*220,dy*220);return;}" +
            "var a=base.getBoundingClientRect(),cx=(a.left+a.right)/2,cy=(a.top+a.bottom)/2,best=null,score=1e30;" +
            "for(var i=0;i<nodes.length;i++){var e=nodes[i];if(e===base)continue;var r=e.getBoundingClientRect()," +
            "x=(r.left+r.right)/2-cx,y=(r.top+r.bottom)/2-cy,along=x*dx+y*dy,across=Math.abs(x*dy-y*dx);" +
            "if(along<=2)continue;var s=along+across*2.5;if(s<score){score=s;best=e;}}" +
            "if(best)highlight(best);else window.scrollBy(dx*220,dy*220);}," +
            "select:function(){if(active&&document.contains(active)){active.focus();active.click();}" +
            "else if(document.activeElement&&document.activeElement.click)document.activeElement.click();}};" +
            "})();";
}
