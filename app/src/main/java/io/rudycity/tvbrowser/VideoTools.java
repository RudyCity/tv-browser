package io.rudycity.tvbrowser;

/** Whitelisted, first-party HTML5 media commands. No arbitrary JavaScript inputs. */
public final class VideoTools {
    public static final String PLAY_PAUSE = "play_pause";
    public static final String REWIND = "rewind_10";
    public static final String FORWARD = "forward_10";
    public static final String MUTE = "mute";
    public static final String SPEED_NORMAL = "speed_normal";
    public static final String SPEED_FAST = "speed_fast";
    public static final String SPEED_SLOW = "speed_slow";
    public static final String[] COMMANDS = {
            PLAY_PAUSE, REWIND, FORWARD, MUTE, SPEED_SLOW, SPEED_NORMAL, SPEED_FAST
    };
    private VideoTools() {}

    /** Returns null for unknown commands; executed only on the current first-party page. */
    public static String javascript(String command) {
        String action;
        if (PLAY_PAUSE.equals(command)) action = "if(v.paused){v.play();}else{v.pause();}";
        else if (REWIND.equals(command)) action = "v.currentTime=Math.max(0,v.currentTime-10);";
        else if (FORWARD.equals(command)) action =
                "if(isFinite(v.duration)){v.currentTime=Math.min(v.duration,v.currentTime+10);}else{v.currentTime+=10;}";
        else if (MUTE.equals(command)) action = "v.muted=!v.muted;";
        else if (SPEED_NORMAL.equals(command)) action = "v.playbackRate=1;";
        else if (SPEED_FAST.equals(command)) action = "v.playbackRate=1.5;";
        else if (SPEED_SLOW.equals(command)) action = "v.playbackRate=0.75;";
        else return null;
        return "(function(){var videos=document.querySelectorAll('video');"
                + "var v=null;for(var i=0;i<videos.length;i++){"
                + "if(!v||videos[i].clientWidth*videos[i].clientHeight>"
                + "v.clientWidth*v.clientHeight)v=videos[i];}"
                + "if(!v)return 'no-video';try{" + action
                + "return 'ok';}catch(e){return 'failed';}})();";
    }
}
