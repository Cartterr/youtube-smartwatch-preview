package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class YouTubeHandoffTest {
    @Test
    void buildsMobileWatchUrlFromStandardYouTubeUrlWithTimestamp() {
        YouTubeHandoff handoff = YouTubeHandoff.fromInput(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1m02s");

        assertEquals("dQw4w9WgXcQ", handoff.videoId());
        assertEquals(62, handoff.startSeconds());
        assertEquals(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=62s",
                handoff.toMobileWatchUrl());
    }

    @Test
    void buildsMobileWatchUrlFromShortLink() {
        YouTubeHandoff handoff = YouTubeHandoff.fromInput("https://youtu.be/dQw4w9WgXcQ?t=90");

        assertEquals("dQw4w9WgXcQ", handoff.videoId());
        assertEquals(90, handoff.startSeconds());
        assertEquals(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90s",
                handoff.toMobileWatchUrl());
    }

    @Test
    void buildsMobileWatchUrlFromShortsUrl() {
        YouTubeHandoff handoff = YouTubeHandoff.fromInput(
                "https://youtube.com/shorts/dQw4w9WgXcQ?feature=share");

        assertEquals("dQw4w9WgXcQ", handoff.videoId());
        assertEquals(0, handoff.startSeconds());
        assertEquals(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
                handoff.toMobileWatchUrl());
    }

    @Test
    void acceptsBareVideoIdAndExplicitTimestamp() {
        YouTubeHandoff handoff = YouTubeHandoff.fromInput("dQw4w9WgXcQ", 321);

        assertEquals("dQw4w9WgXcQ", handoff.videoId());
        assertEquals(321, handoff.startSeconds());
        assertEquals(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=321s",
                handoff.toMobileWatchUrl());
    }

    @Test
    void rejectsInputWithoutAValidVideoId() {
        assertThrows(IllegalArgumentException.class, () -> YouTubeHandoff.fromInput("not youtube"));
    }
}
