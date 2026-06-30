package cl.dily.youtubepreview.wear;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewClient.StatusListener {
    public static final String ACTION_OPEN_YOUTUBE = "cl.dily.youtubepreview.OPEN_YOUTUBE";
    public static final String EXTRA_YOUTUBE_URL = "cl.dily.youtubepreview.YOUTUBE_URL";

    private static final String PREFS = "yswp-watch";
    private static final String KEY_HOST = "host";
    private static final String DEFAULT_HOST = "10.0.2.2";
    private static final String SAMSUNG_INTERNET_PACKAGE = "com.sec.android.app.sbrowser";
    private static final String TAG = "YSWWear";

    private SurfaceView surfaceView;
    private TextView statusView;
    private PreviewClient client;
    private String host;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (openYouTubeIfRequested(getIntent())) {
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String requestedHost = getIntent().getStringExtra(KEY_HOST);
        if (requestedHost != null && !requestedHost.trim().isEmpty()) {
            host = requestedHost.trim();
            prefs.edit().putString(KEY_HOST, host).apply();
        } else {
            host = prefs.getString(KEY_HOST, DEFAULT_HOST);
        }

        setContentView(createContent());
        updateStatus("Waiting for surface. Host " + host);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (openYouTubeIfRequested(intent)) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        stopClient();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startClient(holder);
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
            updateStatus(status);
            if (keepScreenOn) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private FrameLayout createContent() {
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

    private void updateStatus(String status) {
        statusView.setText(status);
    }

    private boolean openYouTubeIfRequested(Intent intent) {
        if (intent == null) {
            return false;
        }
        String url = intent.getStringExtra(EXTRA_YOUTUBE_URL);
        if (!ACTION_OPEN_YOUTUBE.equals(intent.getAction()) || url == null || url.isBlank()) {
            return false;
        }
        openYouTubeUrl(url.trim());
        return true;
    }

    private void openYouTubeUrl(String url) {
        Log.i(TAG, "Opening YouTube URL: " + url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(SAMSUNG_INTERNET_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Samsung Internet not found, falling back to any browser", e);
            intent.setPackage(null);
            startActivity(intent);
        }
    }
}

