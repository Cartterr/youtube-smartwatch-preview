package cl.dily.youtubepreview.phone;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.SystemClock;
import cl.dily.youtubepreview.shared.YouTubeHandoff;
import cl.dily.youtubepreview.shared.YouTubePlaybackSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlaybackPositionProbe {
    private static final int MAX_SECONDS = 12 * 60 * 60;
    private static final Pattern YOUTUBE_ID_IN_TEXT = Pattern.compile(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?[^\\s]*v=|shorts/|embed/|live/)|[?&]v=)([A-Za-z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE);

    private PlaybackPositionProbe() {
    }

    static YouTubePlaybackSnapshot findYouTubeSnapshot(Context context) {
        MediaSessionManager manager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) {
            return YouTubePlaybackSnapshot.empty();
        }

        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(
                    new ComponentName(context, PlaybackNotificationListener.class));
        } catch (SecurityException e) {
            return YouTubePlaybackSnapshot.empty();
        }

        for (MediaController controller : controllers) {
            if (isYouTubePackage(controller.getPackageName())) {
                return snapshotFromController(controller);
            }
        }
        return YouTubePlaybackSnapshot.empty();
    }

    static int findYouTubeStartSeconds(Context context) {
        YouTubePlaybackSnapshot snapshot = findYouTubeSnapshot(context);
        if (!snapshot.packageName().isBlank()) {
            return snapshot.currentSeconds(SystemClock.elapsedRealtime());
        }
        return -1;
    }

    static boolean isYouTubePackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.US);
        return normalized.equals("com.google.android.youtube")
                || normalized.equals("com.google.android.apps.youtube.music");
    }

    private static YouTubePlaybackSnapshot snapshotFromController(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        List<String> candidates = new ArrayList<>();
        String title = "";
        String artist = "";

        if (metadata != null) {
            title = firstNonBlank(
                    valueOf(metadata.getString(MediaMetadata.METADATA_KEY_TITLE)),
                    valueOf(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
            artist = firstNonBlank(
                    valueOf(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)),
                    valueOf(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)),
                    valueOf(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)));
            collectMetadataCandidates(metadata, candidates);
        }

        MediaDescription description = metadata == null ? null : metadata.getDescription();
        if (description != null) {
            title = firstNonBlank(title, valueOf(description.getTitle()));
            artist = firstNonBlank(artist, valueOf(description.getSubtitle()));
            Uri mediaUri = description.getMediaUri();
            if (mediaUri != null) {
                candidates.add(mediaUri.toString());
            }
            candidates.add(valueOf(description.getMediaId()));
            candidates.add(valueOf(description.getDescription()));
        }

        String sourceUri = firstUrlCandidate(candidates);
        String videoId = extractVideoId(candidates);
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        long positionMs = state == null ? 0L : Math.max(0L, state.getPosition());
        long updateTime = state == null ? SystemClock.elapsedRealtime() : Math.max(0L, state.getLastPositionUpdateTime());
        float speed = state == null || state.getPlaybackSpeed() <= 0f ? 1.0f : state.getPlaybackSpeed();

        return new YouTubePlaybackSnapshot(
                videoId,
                title,
                artist,
                playing,
                positionMs,
                updateTime,
                speed,
                controller.getPackageName(),
                sourceUri);
    }

    private static int secondsFromState(PlaybackState state) {
        if (state == null || state.getPosition() < 0) {
            return -1;
        }

        long positionMs = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING
                && state.getLastPositionUpdateTime() > 0
                && state.getPlaybackSpeed() > 0f) {
            long elapsedMs = Math.max(0, SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime());
            positionMs += (long) (elapsedMs * state.getPlaybackSpeed());
        }

        long seconds = Math.max(0, positionMs / 1000L);
        return (int) Math.min(seconds, MAX_SECONDS);
    }

    private static void collectMetadataCandidates(MediaMetadata metadata, List<String> candidates) {
        for (String key : metadata.keySet()) {
            CharSequence text = metadata.getText(key);
            if (text != null) {
                candidates.add(text.toString());
            }
        }
    }

    private static String extractVideoId(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return YouTubeHandoff.fromInput(candidate).videoId();
            } catch (IllegalArgumentException ignored) {
                Matcher matcher = YOUTUBE_ID_IN_TEXT.matcher(candidate);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return "";
    }

    private static String firstUrlCandidate(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate != null && candidate.startsWith("http")) {
                return candidate;
            }
        }
        return "";
    }

    private static String valueOf(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
