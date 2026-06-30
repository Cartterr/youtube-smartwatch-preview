package cl.dily.youtubepreview.wear;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import cl.dily.youtubepreview.shared.WatchOpenMessage;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public final class WatchCommandListenerService extends WearableListenerService {
    private static final String TAG = "YSWWear";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (WatchOpenMessage.PATH_YOUTUBE_STATE.equals(messageEvent.getPath())) {
            String json = WatchOpenMessage.decodePayload(messageEvent.getData());
            Log.d(TAG, "Received YouTube state: " + json);
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            prefs.edit().putString(MainActivity.KEY_YOUTUBE_STATE, json).apply();
            sendBroadcast(new Intent(MainActivity.ACTION_YOUTUBE_STATE_CHANGED)
                    .setPackage(getPackageName()));
            return;
        }

        if (!WatchOpenMessage.PATH_OPEN_YOUTUBE.equals(messageEvent.getPath())) {
            return;
        }
        String url = WatchOpenMessage.decodePayload(messageEvent.getData());
        Log.i(TAG, "Received handoff: " + url);
        Intent intent = new Intent(this, YouTubePlayerActivity.class)
                .putExtra(MainActivity.EXTRA_YOUTUBE_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
