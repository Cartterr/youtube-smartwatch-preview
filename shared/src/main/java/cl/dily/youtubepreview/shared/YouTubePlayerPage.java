package cl.dily.youtubepreview.shared;

public final class YouTubePlayerPage {
    private YouTubePlayerPage() {
    }

    public static String html(String videoId, int startSeconds) {
        String safeId = YouTubeHandoff.fromInput(videoId).videoId();
        int safeStart = Math.max(0, startSeconds);
        String src = "https://www.youtube.com/embed/" + safeId
                + "?autoplay=1&mute=1&playsinline=1&controls=0&disablekb=1"
                + "&fs=0&rel=0&modestbranding=1&start=" + safeStart;
        return "<!doctype html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">"
                + "<style>"
                + "html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000;}"
                + "#stage{position:fixed;inset:0;overflow:hidden;background:#000;}"
                + "iframe{position:absolute;top:50%;left:50%;width:177.78vh;height:100vh;"
                + "border:0;transform:translate(-50%,-50%);background:#000;}"
                + "</style></head><body>"
                + "<div id=\"stage\"><iframe allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" "
                + "allowfullscreen src=\"" + src + "\"></iframe></div>"
                + "</body></html>";
    }
}
