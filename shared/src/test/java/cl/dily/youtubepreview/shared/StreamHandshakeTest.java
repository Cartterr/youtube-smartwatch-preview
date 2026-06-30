package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StreamHandshakeTest {
    @Test
    void encodesAndParsesV0HandshakeJson() {
        StreamHandshake handshake = StreamHandshake.v0(360, 360, 12);

        assertEquals(
                "{\"protocol\":\"yswp-h264\",\"version\":1,\"codec\":\"video/avc\",\"width\":360,\"height\":360,\"fps\":12,\"source\":\"synthetic\"}",
                handshake.toJsonLine());
        assertEquals(handshake, StreamHandshake.parse(handshake.toJsonLine()));
    }

    @Test
    void encodesAndParsesRealVideoSource() {
        StreamHandshake handshake = StreamHandshake.v1(360, 360, 12, StreamSource.SAMPLE_MP4);

        assertEquals(StreamSource.SAMPLE_MP4, handshake.source());
        assertEquals(handshake, StreamHandshake.parse(handshake.toJsonLine()));
    }

    @Test
    void treatsMissingSourceAsSyntheticForOldClients() {
        StreamHandshake handshake = StreamHandshake.parse(
                "{\"protocol\":\"yswp-h264\",\"version\":1,\"codec\":\"video/avc\",\"width\":360,\"height\":360,\"fps\":12}");

        assertEquals(StreamSource.SYNTHETIC, handshake.source());
    }

    @Test
    void rejectsWrongProtocol() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> StreamHandshake.parse("{\"protocol\":\"other\",\"version\":1,\"codec\":\"video/avc\",\"width\":360,\"height\":360,\"fps\":12}"));

        assertEquals("Unsupported protocol: other", error.getMessage());
    }

    @Test
    void rejectsMissingRequiredFields() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> StreamHandshake.parse("{\"protocol\":\"yswp-h264\",\"version\":1,\"codec\":\"video/avc\",\"width\":360}"));

        assertEquals("Missing required handshake field: height", error.getMessage());
    }
}

