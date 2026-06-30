package cl.dily.youtubepreview.wear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cl.dily.youtubepreview.shared.YouTubePlaybackSnapshot;

import java.util.Locale;

public final class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewClient.StatusListener {
    public static final String ACTION_OPEN_YOUTUBE = "cl.dily.youtubepreview.OPEN_YOUTUBE";
    public static final String ACTION_YOUTUBE_STATE_CHANGED = "cl.dily.youtubepreview.YOUTUBE_STATE_CHANGED";
    public static final String EXTRA_YOUTUBE_URL = "cl.dily.youtubepreview.YOUTUBE_URL";
    public static final String EXTRA_YOUTUBE_STATE = "cl.dily.youtubepreview.YOUTUBE_STATE";
    public static final String PREFS = "yswp-watch";
    public static final String KEY_YOUTUBE_STATE = "youtube-state";

    private static final String KEY_HOST = "host";
    private static final String DEFAULT_HOST = "10.0.2.2";

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStateUi();
        }
    };

    private SurfaceView surfaceView;
    private TextView statusView;
    private TextView titleView;
    private TextView detailView;
    private TextView stateView;
    private Button openButton;
    private PreviewClient client;
    private String host;
    private boolean streamMode;
    private boolean receiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (openYouTubeIfRequested(getIntent())) {
            finish();
            return;
        }

        String requestedHost = getIntent().getStringExtra(KEY_HOST);
        if (requestedHost != null && !requestedHost.trim().isEmpty()) {
            streamMode = true;
            initStreamMode(requestedHost.trim());
        } else {
            streamMode = false;
            setContentView(createStateContent());
            updateStateUi();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (openYouTubeIfRequested(intent)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!streamMode) {
            registerStateReceiver();
            updateStateUi();
        }
    }

    @Override
    protected void onPause() {
        if (!streamMode) {
            unregisterStateReceiver();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterStateReceiver();
        stopClient();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (streamMode) {
            startClient(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopClient();
    }

    @Override
    public void onStatus(String status, boolean keepScreenOn) {
        runOnUiThread(() -> {
            updateStreamStatus(status);
            if (keepScreenOn) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void initStreamMode(String requestedHost) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        host = requestedHost;
        prefs.edit().putString(KEY_HOST, host).apply();
        setContentView(createStreamContent());
        updateStreamStatus("Waiting for surface. Host " + host);
    }

    private FrameLayout createStreamContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(statusView, statusParams);

        return root;
    }

    private FrameLayout createStateContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int padding = dp(28);
        panel.setPadding(padding, padding, padding, padding);
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17f);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(3);
        panel.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        detailView = new TextView(this);
        detailView.setTextColor(0xffbdbdbd);
        detailView.setTextSize(12f);
        detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.setMargins(0, dp(8), 0, 0);
        panel.addView(detailView, detailParams);

        openButton = new Button(this);
        openButton.setText("Open current video");
        openButton.setAllCaps(false);
        openButton.setOnClickListener(view -> openLatestSnapshot());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(18), 0, 0);
        panel.addView(openButton, buttonParams);

        stateView = new TextView(this);
        stateView.setTextColor(0xffe0e0e0);
        stateView.setTextSize(11f);
        stateView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.setMargins(0, dp(14), 0, 0);
        panel.addView(stateView, stateParams);

        return root;
    }

    private void updateStateUi() {
        YouTubePlaybackSnapshot snapshot = latestSnapshot();
        if (snapshot.packageName().isBlank()) {
            titleView.setText("No YouTube playback");
            detailView.setText("Play YouTube on the phone");
            stateView.setText("Enable YSW Preview sync in phone notification access.");
            openButton.setEnabled(false);
            return;
        }

        titleView.setText(snapshot.title().isBlank() ? "YouTube is playing" : snapshot.title());
        int seconds = snapshot.currentSeconds(SystemClock.elapsedRealtime());
        String artist = snapshot.artist().isBlank() ? snapshot.packageName() : snapshot.artist();
        detailView.setText(artist + " | " + formatSeconds(seconds));

        if (snapshot.hasVideoId()) {
            openButton.setEnabled(true);
            stateView.setText(snapshot.playing() ? "Ready, synced from phone." : "Paused on phone, opens at paused time.");
        } else {
            openButton.setEnabled(false);
            stateView.setText("Detected playback, but the phone did not expose the exact video id.");
        }
    }

    private void openLatestSnapshot() {
        YouTubePlaybackSnapshot snapshot = latestSnapshot();
        if (!snapshot.hasVideoId()) {
            updateStateUi();
            return;
        }
        Intent intent = new Intent(this, YouTubePlayerActivity.class)
                .putExtra(EXTRA_YOUTUBE_STATE, snapshot.toJson());
        startActivity(intent);
    }

    private YouTubePlaybackSnapshot latestSnapshot() {
        String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_YOUTUBE_STATE, "");
        if (json == null || json.isBlank()) {
            return YouTubePlaybackSnapshot.empty();
        }
        try {
            return YouTubePlaybackSnapshot.fromJson(json);
        } catch (IllegalArgumentException e) {
            return YouTubePlaybackSnapshot.empty();
        }
    }

    private void registerStateReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(ACTION_YOUTUBE_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterStateReceiver() {
        if (!receiverRegistered) {
            return;
        }
        unregisterReceiver(stateReceiver);
        receiverRegistered = false;
    }

    private void startClient(SurfaceHolder holder) {
        stopClient();
        client = new PreviewClient(host, holder.getSurface(), this);
        client.start();
    }

    private void stopClient() {
        if (client != null) {
            client.stop();
            client = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateStreamStatus(String status) {
        if (statusView != null) {
            statusView.setText(status);
        }
    }

    private boolean openYouTubeIfRequested(Intent intent) {
        if (intent == null) {
            return false;
        }
        String url = intent.getStringExtra(EXTRA_YOUTUBE_URL);
        if (!ACTION_OPEN_YOUTUBE.equals(intent.getAction()) || url == null || url.isBlank()) {
            return false;
        }
        startActivity(new Intent(this, YouTubePlayerActivity.class)
                .putExtra(EXTRA_YOUTUBE_URL, url.trim()));
        return true;
    }

    private String formatSeconds(int seconds) {
        int minutes = seconds / 60;
        int remaining = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, remaining);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
