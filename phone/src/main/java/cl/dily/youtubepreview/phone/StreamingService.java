package cl.dily.youtubepreview.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import cl.dily.youtubepreview.shared.StreamSource;

public final class StreamingService extends Service {
    public static final String ACTION_START = "cl.dily.youtubepreview.phone.START";
    public static final String ACTION_STOP = "cl.dily.youtubepreview.phone.STOP";
    public static final String EXTRA_SOURCE = "cl.dily.youtubepreview.phone.SOURCE";
    public static final String EXTRA_VIDEO_URI = "cl.dily.youtubepreview.phone.VIDEO_URI";

    private static final String CHANNEL_ID = "yswp-stream";
    private static final int NOTIFICATION_ID = 45990;

    private H264StreamingServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        String source = StreamSource.normalize(intent == null ? null : intent.getStringExtra(EXTRA_SOURCE));
        Uri videoUri = null;
        if (intent != null && intent.getStringExtra(EXTRA_VIDEO_URI) != null) {
            videoUri = Uri.parse(intent.getStringExtra(EXTRA_VIDEO_URI));
        }
        startForeground(NOTIFICATION_ID, buildNotification("Stream " + source + " on port 45990"));
        stopServer();
        server = new H264StreamingServer(this, source, videoUri);
        server.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("Smartwatch preview stream")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Smartwatch preview stream",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}

