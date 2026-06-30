package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StreamSourceTest {
    @Test
    void normalizesKnownSources() {
        assertEquals(StreamSource.SYNTHETIC, StreamSource.normalize("synthetic"));
        assertEquals(StreamSource.SAMPLE_MP4, StreamSource.normalize("sample-mp4"));
        assertEquals(StreamSource.FILE_URI, StreamSource.normalize("file-uri"));
    }

    @Test
    void fallsBackToSyntheticWhenSourceIsBlankOrUnknown() {
        assertEquals(StreamSource.SYNTHETIC, StreamSource.normalize(null));
        assertEquals(StreamSource.SYNTHETIC, StreamSource.normalize("  "));
        assertEquals(StreamSource.SYNTHETIC, StreamSource.normalize("surprise"));
    }
}
