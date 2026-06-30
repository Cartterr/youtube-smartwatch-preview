package cl.dily.youtubepreview.shared;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeHandoff {
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern TIMESTAMP_PART = Pattern.compile("(\\d+)(h|m|s)?");
    private static final int MAX_START_SECONDS = 12 * 60 * 60;

    private final String videoId;
    private final int startSeconds;

    private YouTubeHandoff(String videoId, int startSeconds) {
        this.videoId = videoId;
        this.startSeconds = clampStartSeconds(startSeconds);
    }

    public static YouTubeHandoff fromInput(String input) {
        return new YouTubeHandoff(extractVideoId(input), extractTimestampSeconds(input));
    }

    public static YouTubeHandoff fromInput(String input, int startSeconds) {
        return new YouTubeHandoff(extractVideoId(input), startSeconds);
    }

    public String videoId() {
        return videoId;
    }

    public int startSeconds() {
        return startSeconds;
    }

    public String toMobileWatchUrl() {
        String url = "https://m.youtube.com/watch?v=" + videoId;
        if (startSeconds > 0) {
            return url + "&t=" + startSeconds + "s";
        }
        return url;
    }

    private static String extractVideoId(String input) {
        String trimmed = requireInput(input);
        if (VIDEO_ID.matcher(trimmed).matches()) {
            return trimmed;
        }

        URI uri = parseUri(trimmed);
        String host = normalizeHost(uri.getHost());
        if (host.equals("youtu.be")) {
            return validVideoId(firstPathSegment(uri));
        }

        if (host.equals("youtube.com")) {
            String queryId = queryParam(uri.getRawQuery(), "v");
            if (queryId != null) {
                return validVideoId(queryId);
            }

            String[] segments = pathSegments(uri);
            if (segments.length >= 2
                    && (segments[0].equals("shorts")
                    || segments[0].equals("live")
                    || segments[0].equals("embed")
                    || segments[0].equals("v"))) {
                return validVideoId(segments[1]);
            }
        }

        throw new IllegalArgumentException("No valid YouTube video id found");
    }

    private static int extractTimestampSeconds(String input) {
        URI uri = parseUri(requireInput(input));
        String timestamp = queryParam(uri.getRawQuery(), "t");
        if (timestamp == null) {
            timestamp = queryParam(uri.getRawQuery(), "start");
        }
        return parseTimestampSeconds(timestamp);
    }

    private static int parseTimestampSeconds(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return 0;
        }
        String value = timestamp.trim().toLowerCase(Locale.US);
        if (value.matches("\\d+")) {
            return clampStartSeconds(Integer.parseInt(value));
        }

        int total = 0;
        int consumed = 0;
        Matcher matcher = TIMESTAMP_PART.matcher(value);
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return 0;
            }
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            if ("h".equals(unit)) {
                total += amount * 60 * 60;
            } else if ("m".equals(unit)) {
                total += amount * 60;
            } else {
                total += amount;
            }
            consumed = matcher.end();
        }
        if (consumed != value.length()) {
            return 0;
        }
        return clampStartSeconds(total);
    }

    private static URI parseUri(String input) {
        try {
            return URI.create(input);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Invalid YouTube URL");
        }
    }

    private static String requireInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("YouTube input is required");
        }
        return input.trim();
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.toLowerCase(Locale.US);
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        if (normalized.startsWith("m.")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String firstPathSegment(URI uri) {
        String[] segments = pathSegments(uri);
        return segments.length == 0 ? "" : segments[0];
    }

    private static String[] pathSegments(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) {
            return new String[0];
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isBlank()) {
            return new String[0];
        }
        String[] rawSegments = normalized.split("/");
        String[] decoded = new String[rawSegments.length];
        for (int i = 0; i < rawSegments.length; i++) {
            decoded[i] = decode(rawSegments[i]);
        }
        return decoded;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            if (decode(key).equals(name)) {
                return equals >= 0 ? decode(pair.substring(equals + 1)) : "";
            }
        }
        return null;
    }

    private static String validVideoId(String candidate) {
        if (candidate != null && VIDEO_ID.matcher(candidate).matches()) {
            return candidate;
        }
        throw new IllegalArgumentException("Invalid YouTube video id");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static int clampStartSeconds(int seconds) {
        if (seconds < 0) {
            return 0;
        }
        return Math.min(seconds, MAX_START_SECONDS);
    }
}
