package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WatchOpenMessageTest {
    @Test
    void roundTripsUrlPayloadAsUtf8() {
        String url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=62s";

        assertEquals(url, WatchOpenMessage.decodePayload(WatchOpenMessage.encodePayload(url)));
    }

    @Test
    void exposesStableMessagePath() {
        assertEquals("/yswp/open-youtube", WatchOpenMessage.PATH_OPEN_YOUTUBE);
    }
}
