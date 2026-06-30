package cl.dily.youtubepreview.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StreamHandshake {
    public static final String PROTOCOL = "yswp-h264";
    public static final int VERSION = 1;
    public static final String CODEC = "video/avc";

    private final String protocol;
    private final int version;
    private final String codec;
    private final int width;
    private final int height;
    private final int fps;
    private final String source;

    public StreamHandshake(String protocol, int version, String codec, int width, int height, int fps) {
        this(protocol, version, codec, width, height, fps, StreamSource.SYNTHETIC);
    }

    public StreamHandshake(String protocol, int version, String codec, int width, int height, int fps, String source) {
        this.protocol = protocol;
        this.version = version;
        this.codec = codec;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.source = StreamSource.normalize(source);
    }

    public static StreamHandshake v0(int width, int height, int fps) {
        return new StreamHandshake(PROTOCOL, VERSION, CODEC, width, height, fps);
    }

    public static StreamHandshake v1(int width, int height, int fps, String source) {
        return new StreamHandshake(PROTOCOL, VERSION, CODEC, width, height, fps, source);
    }

    public static StreamHandshake parse(String jsonLine) {
        Map<String, String> values = parseFlatJson(jsonLine);
        String protocol = required(values, "protocol");
        if (!PROTOCOL.equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        int version = parseInt(required(values, "version"), "version");
        String codec = required(values, "codec");
        int width = parseInt(required(values, "width"), "width");
        int height = parseInt(required(values, "height"), "height");
        int fps = parseInt(required(values, "fps"), "fps");
        return new StreamHandshake(protocol, version, codec, width, height, fps, values.get("source"));
    }

    public String toJsonLine() {
        return "{\"protocol\":\"" + protocol + "\",\"version\":" + version
                + ",\"codec\":\"" + codec + "\",\"width\":" + width
                + ",\"height\":" + height + ",\"fps\":" + fps
                + ",\"source\":\"" + source + "\"}";
    }

    public String protocol() {
        return protocol;
    }

    public int version() {
        return version;
    }

    public String codec() {
        return codec;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int fps() {
        return fps;
    }

    public String source() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StreamHandshake)) {
            return false;
        }
        StreamHandshake that = (StreamHandshake) other;
        return version == that.version
                && width == that.width
                && height == that.height
                && fps == that.fps
                && Objects.equals(protocol, that.protocol)
                && Objects.equals(codec, that.codec)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, version, codec, width, height, fps, source);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required handshake field: " + key);
        }
        return value;
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid integer handshake field: " + key, error);
        }
    }

    private static Map<String, String> parseFlatJson(String jsonLine) {
        if (jsonLine == null) {
            throw new IllegalArgumentException("Handshake JSON is required");
        }
        String json = jsonLine.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Handshake must be a JSON object");
        }
        Map<String, String> values = new HashMap<>();
        String body = json.substring(1, json.length() - 1);
        if (body.isEmpty()) {
            return values;
        }
        String[] fields = body.split(",");
        for (String field : fields) {
            String[] pair = field.split(":", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Invalid handshake field: " + field);
            }
            values.put(unquote(pair[0].trim()), unquote(pair[1].trim()));
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

