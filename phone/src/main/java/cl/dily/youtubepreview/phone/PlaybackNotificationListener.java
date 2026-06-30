package cl.dily.youtubepreview.phone;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import cl.dily.youtubepreview.shared.WatchOpenMessage;
import cl.dily.youtubepreview.shared.YouTubePlaybackSnapshot;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public final class PlaybackNotificationListener extends NotificationListenerService {
    private static final String TAG = "YSWPhoneSync";
    private static final long POLL_INTERVAL_MS = 2_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager mediaSessionManager;
    private String lastJson = "";
    private boolean started;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    publishSnapshot();
                }
            };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            publishSnapshot();
            if (started) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        started = true;
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (mediaSessionManager != null) {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    new ComponentName(this, PlaybackNotificationListener.class),
                    handler);
        }
        publishSnapshot();
        handler.post(pollRunnable);
    }

    @Override
    public void onListenerDisconnected() {
        stopSync();
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        stopSync();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (PlaybackPositionProbe.isYouTubePackage(sbn.getPackageName())) {
            publishSnapshot();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (PlaybackPositionProbe.isYouTubePackage(sbn.getPackageName())) {
            publishSnapshot();
        }
    }

    private void stopSync() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            mediaSessionManager = null;
        }
    }

    private void publishSnapshot() {
        YouTubePlaybackSnapshot snapshot = PlaybackPositionProbe.findYouTubeSnapshot(this);
        String json = snapshot.toJson();
        if (json.equals(lastJson) && !snapshot.playing()) {
            return;
        }
        lastJson = json;
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> sendSnapshotToNodes(json, nodes))
                .addOnFailureListener(error -> Log.w(TAG, "Watch lookup failed", error));
    }

    private void sendSnapshotToNodes(String json, List<Node> nodes) {
        if (nodes.isEmpty()) {
            Log.d(TAG, "No connected watch for YouTube snapshot");
            return;
        }
        byte[] payload = WatchOpenMessage.encodePayload(json);
        for (Node node : nodes) {
            Wearable.getMessageClient(this)
                    .sendMessage(node.getId(), WatchOpenMessage.PATH_YOUTUBE_STATE, payload)
                    .addOnSuccessListener(id -> Log.d(TAG, "Sent YouTube state to " + node.getDisplayName()))
                    .addOnFailureListener(error -> Log.w(TAG, "Failed to send YouTube state", error));
        }
    }
}
