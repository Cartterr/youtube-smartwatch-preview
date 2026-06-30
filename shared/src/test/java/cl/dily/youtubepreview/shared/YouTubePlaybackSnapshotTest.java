package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class YouTubePlaybackSnapshotTest {
    @Test
    void roundTripsPlaybackSnapshotJson() {
        YouTubePlaybackSnapshot snapshot = new YouTubePlaybackSnapshot(
                "dQw4w9WgXcQ",
                "Demo Video",
                "Demo Channel",
                true,
                62_000L,
                1_000L,
                1.0f,
                "com.google.android.youtube",
                "https://youtu.be/dQw4w9WgXcQ");

        YouTubePlaybackSnapshot parsed = YouTubePlaybackSnapshot.fromJson(snapshot.toJson());

        assertEquals("dQw4w9WgXcQ", parsed.videoId());
        assertEquals("Demo Video", parsed.title());
        assertEquals("Demo Channel", parsed.artist());
        assertTrue(parsed.playing());
        assertEquals(62_000L, parsed.positionMs());
        assertEquals(1_000L, parsed.elapsedRealtimeMs());
        assertEquals(1.0f, parsed.speed());
        assertEquals("com.google.android.youtube", parsed.packageName());
        assertEquals("https://youtu.be/dQw4w9WgXcQ", parsed.sourceUri());
    }

    @Test
    void projectsCurrentSecondsWhilePlaying() {
        YouTubePlaybackSnapshot snapshot = new YouTubePlaybackSnapshot(
                "dQw4w9WgXcQ", "Demo", "", true, 62_000L, 1_000L, 1.0f,
                "com.google.android.youtube", "");

        assertEquals(66, snapshot.currentSeconds(5_000L));
    }

    @Test
    void doesNotProjectCurrentSecondsWhilePaused() {
        YouTubePlaybackSnapshot snapshot = new YouTubePlaybackSnapshot(
                "dQw4w9WgXcQ", "Demo", "", false, 62_000L, 1_000L, 1.0f,
                "com.google.android.youtube", "");

        assertEquals(62, snapshot.currentSeconds(5_000L));
    }

    @Test
    void buildsWatchUrlFromVideoIdAndCurrentPosition() {
        YouTubePlaybackSnapshot snapshot = new YouTubePlaybackSnapshot(
                "dQw4w9WgXcQ", "Demo", "", true, 62_000L, 1_000L, 1.0f,
                "com.google.android.youtube", "");

        assertEquals(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=66s",
                snapshot.toWatchUrl(5_000L));
    }

    @Test
    void reportsMissingVideoId() {
        YouTubePlaybackSnapshot snapshot = new YouTubePlaybackSnapshot(
                "", "Demo", "", true, 62_000L, 1_000L, 1.0f,
                "com.google.android.youtube", "");

        assertFalse(snapshot.hasVideoId());
        assertEquals("", snapshot.toWatchUrl(5_000L));
    }

    @Test
    void exposesStableStateMessagePath() {
        assertEquals("/yswp/youtube-state", WatchOpenMessage.PATH_YOUTUBE_STATE);
    }
}
