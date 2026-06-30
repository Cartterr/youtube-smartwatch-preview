package cl.dily.youtubepreview.phone;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

final class PlaybackPositionProbe {
    private static final int MAX_SECONDS = 12 * 60 * 60;

    private PlaybackPositionProbe() {
    }

    static int findYouTubeStartSeconds(Context context) {
        MediaSessionManager manager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) {
            return -1;
        }

        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(
                    new ComponentName(context, PlaybackNotificationListener.class));
        } catch (SecurityException e) {
            return -1;
        }

        for (MediaController controller : controllers) {
            if (isYouTubePackage(controller.getPackageName())) {
                int seconds = secondsFromState(controller.getPlaybackState());
                if (seconds >= 0) {
                    return seconds;
                }
            }
        }
        return -1;
    }

    private static boolean isYouTubePackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        String normalized = packageName.toLowerCase(Locale.US);
        return normalized.equals("com.google.android.youtube")
                || normalized.equals("com.google.android.apps.youtube.music");
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
}
