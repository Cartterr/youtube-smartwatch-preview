package cl.dily.youtubepreview.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import cl.dily.youtubepreview.shared.StreamSource;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 10;
    private static final int VIDEO_PICK_REQUEST = 11;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        setContentView(createContent());
    }

    private LinearLayout createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(24);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("YouTube Smartwatch Preview V1");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("Streams synthetic frames, a bundled MP4, or a picked local video over the same H.264 watch transport.");
        body.setTextSize(15f);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(16), 0, dp(24));
        root.addView(body, bodyParams);

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

    private void stopStreaming() {
        startService(new Intent(this, StreamingService.class).setAction(StreamingService.ACTION_STOP));
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

