package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StreamSettingsTest {
    @Test
    void clampsBitrateAndFpsToV0Limits() {
        StreamSettings low = StreamSettings.fromRequested(100_000, 1);
        StreamSettings high = StreamSettings.fromRequested(2_000_000, 60);

        assertEquals(300_000, low.bitrate());
        assertEquals(10, low.fps());
        assertEquals(700_000, high.bitrate());
        assertEquals(15, high.fps());
    }
}

