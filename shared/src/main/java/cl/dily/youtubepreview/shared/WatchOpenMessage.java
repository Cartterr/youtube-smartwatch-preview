package cl.dily.youtubepreview.shared;

import java.nio.charset.StandardCharsets;

public final class WatchOpenMessage {
    public static final String PATH_OPEN_YOUTUBE = "/yswp/open-youtube";

    private WatchOpenMessage() {
    }

    public static byte[] encodePayload(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        return url.getBytes(StandardCharsets.UTF_8);
    }

    public static String decodePayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload is required");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }
}
