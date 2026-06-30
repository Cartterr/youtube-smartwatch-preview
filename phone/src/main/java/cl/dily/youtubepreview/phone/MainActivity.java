package cl.dily.youtubepreview.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cl.dily.youtubepreview.shared.StreamSource;
import cl.dily.youtubepreview.shared.WatchOpenMessage;
import cl.dily.youtubepreview.shared.YouTubeHandoff;
import cl.dily.youtubepreview.shared.YouTubePlaybackSnapshot;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public final class MainActivity extends Activity {
    public static final String ACTION_OPEN_ON_WATCH = "cl.dily.youtubepreview.OPEN_ON_WATCH";
    public static final String EXTRA_YOUTUBE_INPUT = "cl.dily.youtubepreview.YOUTUBE_INPUT";
    public static final String EXTRA_START_SECONDS = "cl.dily.youtubepreview.START_SECONDS";

    private static final int NOTIFICATION_PERMISSION_REQUEST = 10;
    private static final int VIDEO_PICK_REQUEST = 11;
    private static final String TAG = "YSWPhone";

    private EditText youtubeInput;
    private EditText secondsInput;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ACTION_OPEN_ON_WATCH.equals(getIntent().getAction())) {
            requestNotificationPermission();
        }
        setContentView(createContent());
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private LinearLayout createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("YouTube Smartwatch Preview V3");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("Automatically publishes current YouTube playback to the watch. Manual URL entry remains as a debug fallback.");
        body.setTextSize(15f);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(16), 0, dp(24));
        root.addView(body, bodyParams);

        youtubeInput = new EditText(this);
        youtubeInput.setHint("YouTube URL or video id");
        youtubeInput.setSingleLine(false);
        youtubeInput.setMinLines(2);
        youtubeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(youtubeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        secondsInput = new EditText(this);
        secondsInput.setHint("Optional start seconds");
        secondsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams secondsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        secondsParams.setMargins(0, dp(12), 0, 0);
        root.addView(secondsInput, secondsParams);

        Button usePlayback = new Button(this);
        usePlayback.setText("Use phone playback time");
        usePlayback.setOnClickListener(view -> fillPlaybackTime());
        LinearLayout.LayoutParams playbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackParams.setMargins(0, dp(12), 0, 0);
        root.addView(usePlayback, playbackParams);

        Button openOnWatch = new Button(this);
        openOnWatch.setText("Open on watch");
        openOnWatch.setOnClickListener(view -> openYouTubeOnWatch());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        openParams.setMargins(0, dp(12), 0, 0);
        root.addView(openOnWatch, openParams);

        Button notificationAccess = new Button(this);
        notificationAccess.setText("Enable playback timestamp access");
        notificationAccess.setOnClickListener(view -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LinearLayout.LayoutParams accessParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        accessParams.setMargins(0, dp(12), 0, dp(24));
        root.addView(notificationAccess, accessParams);

        Button start = new Button(this);
        start.setText("Start synthetic");
        start.setOnClickListener(view -> startStreaming(StreamSource.SYNTHETIC, null));
        root.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button sample = new Button(this);
        sample.setText("Start sample video");
        sample.setOnClickListener(view -> startStreaming(StreamSource.SAMPLE_MP4, null));
        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sampleParams.setMargins(0, dp(12), 0, 0);
        root.addView(sample, sampleParams);

        Button pick = new Button(this);
        pick.setText("Pick local video");
        pick.setOnClickListener(view -> pickVideo());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pickParams.setMargins(0, dp(12), 0, 0);
        root.addView(pick, pickParams);

        Button stop = new Button(this);
        stop.setText("Stop stream");
        stop.setOnClickListener(view -> stopStreaming());
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stopParams.setMargins(0, dp(12), 0, 0);
        root.addView(stop, stopParams);

        status = new TextView(this);
        status.setText("Ready");
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(20), 0, 0);
        root.addView(status, statusParams);

        TextView androidId = new TextView(this);
        androidId.setText("Device: " + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        androidId.setTextSize(12f);
        androidId.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        idParams.setMargins(0, dp(20), 0, 0);
        root.addView(androidId, idParams);

        return root;
    }

    private void handleIncomingIntent(Intent intent) {
        String input = youtubeInputFromIntent(intent);
        if (input == null || input.isBlank()) {
            return;
        }
        youtubeInput.setText(input.trim());
        if (intent.hasExtra(EXTRA_START_SECONDS)) {
            secondsInput.setText(String.valueOf(intent.getIntExtra(EXTRA_START_SECONDS, 0)));
        }
        updateStatus("Loaded shared YouTube input");
        if (ACTION_OPEN_ON_WATCH.equals(intent.getAction())) {
            openYouTubeOnWatch();
        }
    }

    private String youtubeInputFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        String explicitInput = intent.getStringExtra(EXTRA_YOUTUBE_INPUT);
        if (explicitInput != null) {
            return explicitInput;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            return text == null ? null : text.toString();
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            return intent.getData().toString();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VIDEO_PICK_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
        }
        startStreaming(StreamSource.FILE_URI, uri);
    }

    private void startStreaming(String source, Uri videoUri) {
        Intent intent = new Intent(this, StreamingService.class).setAction(StreamingService.ACTION_START);
        intent.putExtra(StreamingService.EXTRA_SOURCE, source);
        if (videoUri != null) {
            intent.putExtra(StreamingService.EXTRA_VIDEO_URI, videoUri.toString());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, VIDEO_PICK_REQUEST);
    }

    private void fillPlaybackTime() {
        YouTubePlaybackSnapshot snapshot = PlaybackPositionProbe.findYouTubeSnapshot(this);
        if (snapshot.packageName().isBlank()) {
            updateStatus("No YouTube playback timestamp available. Enable access, then play YouTube.");
            return;
        }
        int seconds = snapshot.currentSeconds(SystemClock.elapsedRealtime());
        secondsInput.setText(String.valueOf(seconds));
        if (snapshot.hasVideoId()) {
            youtubeInput.setText(snapshot.videoId());
            updateStatus("Detected " + labelFor(snapshot) + " at " + seconds + "s");
        } else {
            updateStatus("Detected " + labelFor(snapshot) + " at " + seconds
                    + "s, but YouTube did not expose an exact video id.");
        }
    }

    private void openYouTubeOnWatch() {
        try {
            String input = youtubeInput.getText().toString();
            if (input.isBlank()) {
                openDetectedYouTubeOnWatch();
                return;
            }
            YouTubeHandoff parsed = YouTubeHandoff.fromInput(input);
            int startSeconds = resolveStartSeconds(parsed.startSeconds());
            YouTubeHandoff handoff = YouTubeHandoff.fromInput(parsed.videoId(), startSeconds);
            sendUrlToWatch(handoff.toMobileWatchUrl());
        } catch (IllegalArgumentException e) {
            updateStatus(e.getMessage());
        }
    }

    private void openDetectedYouTubeOnWatch() {
        YouTubePlaybackSnapshot snapshot = PlaybackPositionProbe.findYouTubeSnapshot(this);
        if (snapshot.packageName().isBlank()) {
            updateStatus("No active YouTube playback detected.");
            return;
        }
        String url = snapshot.toWatchUrl(SystemClock.elapsedRealtime());
        if (url.isBlank()) {
            updateStatus("Detected " + labelFor(snapshot)
                    + ", but YouTube did not expose an exact video id.");
            return;
        }
        sendUrlToWatch(url);
    }

    private int resolveStartSeconds(int parsedSeconds) {
        String manual = secondsInput.getText().toString().trim();
        if (!manual.isEmpty()) {
            try {
                return Integer.parseInt(manual);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Timestamp must be whole seconds");
            }
        }

        int playbackSeconds = PlaybackPositionProbe.findYouTubeStartSeconds(this);
        if (playbackSeconds >= 0) {
            return playbackSeconds;
        }
        return parsedSeconds;
    }

    private void sendUrlToWatch(String url) {
        Log.i(TAG, "Finding watch for " + url);
        updateStatus("Finding watch...");
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> sendUrlToNodes(url, nodes))
                .addOnFailureListener(error -> {
                    Log.w(TAG, "Watch lookup failed", error);
                    updateStatus("Watch lookup failed: " + messageOf(error));
                });
    }

    private void sendUrlToNodes(String url, List<Node> nodes) {
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected Wear OS watch found");
            updateStatus("No connected Wear OS watch found");
            return;
        }

        byte[] payload = WatchOpenMessage.encodePayload(url);
        for (Node node : nodes) {
            Log.i(TAG, "Sending handoff to " + node.getDisplayName() + " (" + node.getId() + ")");
            Wearable.getMessageClient(this)
                    .sendMessage(node.getId(), WatchOpenMessage.PATH_OPEN_YOUTUBE, payload)
                    .addOnSuccessListener(id -> {
                        Log.i(TAG, "Handoff sent to " + node.getDisplayName() + ": " + id);
                        updateStatus("Sent to " + node.getDisplayName() + ": " + url);
                    })
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "Send failed", error);
                        updateStatus("Send failed: " + messageOf(error));
                    });
        }
    }

    private String messageOf(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private String labelFor(YouTubePlaybackSnapshot snapshot) {
        if (!snapshot.title().isBlank()) {
            return snapshot.title();
        }
        return snapshot.packageName();
    }

    private void stopStreaming() {
        startService(new Intent(this, StreamingService.class).setAction(StreamingService.ACTION_STOP));
    }

    private void updateStatus(String message) {
        status.setText(message);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

