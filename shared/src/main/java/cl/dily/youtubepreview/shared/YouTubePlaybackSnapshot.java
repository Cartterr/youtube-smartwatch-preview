package cl.dily.youtubepreview.shared;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class YouTubePlaybackSnapshot {
    private static final int MAX_START_SECONDS = 12 * 60 * 60;

    private final String videoId;
    private final String title;
    private final String artist;
    private final boolean playing;
    private final long positionMs;
    private final long elapsedRealtimeMs;
    private final float speed;
    private final String packageName;
    private final String sourceUri;

    public YouTubePlaybackSnapshot(
            String videoId,
            String title,
            String artist,
            boolean playing,
            long positionMs,
            long elapsedRealtimeMs,
            float speed,
            String packageName,
            String sourceUri) {
        this.videoId = clean(videoId);
        this.title = clean(title);
        this.artist = clean(artist);
        this.playing = playing;
        this.positionMs = Math.max(0L, positionMs);
        this.elapsedRealtimeMs = Math.max(0L, elapsedRealtimeMs);
        this.speed = speed <= 0f ? 1.0f : speed;
        this.packageName = clean(packageName);
        this.sourceUri = clean(sourceUri);
    }

    public static YouTubePlaybackSnapshot empty() {
        return new YouTubePlaybackSnapshot("", "", "", false, 0L, 0L, 1.0f, "", "");
    }

    public static YouTubePlaybackSnapshot fromJson(String jsonLine) {
        Map<String, String> values = parseFlatJson(jsonLine);
        return new YouTubePlaybackSnapshot(
                values.get("videoId"),
                values.get("title"),
                values.get("artist"),
                Boolean.parseBoolean(values.getOrDefault("playing", "false")),
                parseLong(values.get("positionMs")),
                parseLong(values.get("elapsedRealtimeMs")),
                parseFloat(values.get("speed")),
                values.get("packageName"),
                values.get("sourceUri"));
    }

    public String toJson() {
        return "{"
                + "\"videoId\":\"" + escape(videoId) + "\","
                + "\"title\":\"" + escape(title) + "\","
                + "\"artist\":\"" + escape(artist) + "\","
                + "\"playing\":" + playing + ","
                + "\"positionMs\":" + positionMs + ","
                + "\"elapsedRealtimeMs\":" + elapsedRealtimeMs + ","
                + "\"speed\":" + String.format(Locale.US, "%.3f", speed) + ","
                + "\"packageName\":\"" + escape(packageName) + "\","
                + "\"sourceUri\":\"" + escape(sourceUri) + "\""
                + "}";
    }

    public boolean hasVideoId() {
        return !videoId.isBlank();
    }

    public int currentSeconds(long nowElapsedRealtimeMs) {
        long projectedMs = positionMs;
        if (playing && elapsedRealtimeMs > 0L && nowElapsedRealtimeMs > elapsedRealtimeMs) {
            projectedMs += (long) ((nowElapsedRealtimeMs - elapsedRealtimeMs) * speed);
        }
        long seconds = Math.max(0L, projectedMs / 1000L);
        return (int) Math.min(seconds, MAX_START_SECONDS);
    }

    public String toWatchUrl(long nowElapsedRealtimeMs) {
        if (!hasVideoId()) {
            return "";
        }
        return YouTubeHandoff.fromInput(videoId, currentSeconds(nowElapsedRealtimeMs)).toMobileWatchUrl();
    }

    public String videoId() {
        return videoId;
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public boolean playing() {
        return playing;
    }

    public long positionMs() {
        return positionMs;
    }

    public long elapsedRealtimeMs() {
        return elapsedRealtimeMs;
    }

    public float speed() {
        return speed;
    }

    public String packageName() {
        return packageName;
    }

    public String sourceUri() {
        return sourceUri;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static float parseFloat(String value) {
        if (value == null || value.isBlank()) {
            return 1.0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static Map<String, String> parseFlatJson(String jsonLine) {
        if (jsonLine == null) {
            throw new IllegalArgumentException("Snapshot JSON is required");
        }
        String json = jsonLine.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Snapshot must be a JSON object");
        }
        Map<String, String> values = new HashMap<>();
        String body = json.substring(1, json.length() - 1);
        if (body.isBlank()) {
            return values;
        }

        for (String field : splitFields(body)) {
            String[] pair = field.split(":", 2);
            if (pair.length == 2) {
                values.put(unquote(pair[0].trim()), unquote(pair[1].trim()));
            }
        }
        return values;
    }

    private static String[] splitFields(String body) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                current.append(c);
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (c == ',' && !inString) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return unescape(value.substring(1, value.length() - 1));
        }
        return value;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaping) {
                builder.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
