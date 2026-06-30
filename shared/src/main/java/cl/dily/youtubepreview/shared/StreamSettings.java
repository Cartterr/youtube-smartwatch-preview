package cl.dily.youtubepreview.shared;

public final class StreamSettings {
    public static final int MIN_BITRATE = 300_000;
    public static final int MAX_BITRATE = 700_000;
    public static final int MIN_FPS = 10;
    public static final int MAX_FPS = 15;

    private final int bitrate;
    private final int fps;

    public StreamSettings(int bitrate, int fps) {
        this.bitrate = bitrate;
        this.fps = fps;
    }

    public static StreamSettings fromRequested(int requestedBitrate, int requestedFps) {
        return new StreamSettings(
                clamp(requestedBitrate, MIN_BITRATE, MAX_BITRATE),
                clamp(requestedFps, MIN_FPS, MAX_FPS));
    }

    public int bitrate() {
        return bitrate;
    }

    public int fps() {
        return fps;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

