package cl.dily.youtubepreview.wear;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import cl.dily.youtubepreview.shared.YouTubeHandoff;
import cl.dily.youtubepreview.shared.YouTubePlaybackSnapshot;
import cl.dily.youtubepreview.shared.YouTubePlayerPage;

public final class YouTubePlayerActivity extends Activity {
    private static final String SAMSUNG_INTERNET_PACKAGE = "com.sec.android.app.sbrowser";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PlayerRequest request = requestFromIntent();
        if (request.videoId.isBlank()) {
            setContentView(errorView("No exact YouTube video id"));
            return;
        }
        showPlayer(request);
    }

    private PlayerRequest requestFromIntent() {
        String stateJson = getIntent().getStringExtra(MainActivity.EXTRA_YOUTUBE_STATE);
        if (stateJson != null && !stateJson.isBlank()) {
            YouTubePlaybackSnapshot snapshot = YouTubePlaybackSnapshot.fromJson(stateJson);
            return new PlayerRequest(snapshot.videoId(), snapshot.currentSeconds(SystemClock.elapsedRealtime()));
        }

        String url = getIntent().getStringExtra(MainActivity.EXTRA_YOUTUBE_URL);
        if (url != null && !url.isBlank()) {
            YouTubeHandoff handoff = YouTubeHandoff.fromInput(url);
            return new PlayerRequest(handoff.videoId(), handoff.startSeconds());
        }
        return new PlayerRequest("", 0);
    }

    private void showPlayer(PlayerRequest request) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        try {
            WebView webView = new WebView(this);
            webView.setBackgroundColor(Color.BLACK);
            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebViewClient(new WebViewClient());
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(false);
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            setContentView(root);
            webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    YouTubePlayerPage.html(request.videoId, request.startSeconds),
                    "text/html",
                    "UTF-8",
                    null);
        } catch (RuntimeException error) {
            openBrowserFallback(request);
            setContentView(errorView("WebView unavailable. Opening browser fallback."));
        }
    }

    private void openBrowserFallback(PlayerRequest request) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(YouTubePlayerPage.embedUrl(request.videoId, request.startSeconds)));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage(SAMSUNG_INTERNET_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            intent.setPackage(null);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    private TextView errorView(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15f);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.BLACK);
        return view;
    }

    private static final class PlayerRequest {
        final String videoId;
        final int startSeconds;

        PlayerRequest(String videoId, int startSeconds) {
            this.videoId = videoId == null ? "" : videoId;
            this.startSeconds = Math.max(0, startSeconds);
        }
    }
}
