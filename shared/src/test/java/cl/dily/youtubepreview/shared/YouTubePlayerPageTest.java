package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class YouTubePlayerPageTest {
    @Test
    void buildsMutedAutoplayingCroppedIframePage() {
        String html = YouTubePlayerPage.html("dQw4w9WgXcQ", 42);

        assertTrue(html.contains("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertTrue(html.contains("autoplay=1"));
        assertTrue(html.contains("mute=1"));
        assertTrue(html.contains("playsinline=1"));
        assertTrue(html.contains("controls=0"));
        assertTrue(html.contains("start=42"));
        assertTrue(html.contains("width:177.78vh"));
        assertTrue(html.contains("transform:translate(-50%,-50%)"));
    }
}
