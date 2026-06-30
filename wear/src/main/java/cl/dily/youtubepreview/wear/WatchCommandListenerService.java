package cl.dily.youtubepreview.wear;

import android.content.Intent;
import android.util.Log;
import cl.dily.youtubepreview.shared.WatchOpenMessage;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public final class WatchCommandListenerService extends WearableListenerService {
    private static final String TAG = "YSWWear";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (!WatchOpenMessage.PATH_OPEN_YOUTUBE.equals(messageEvent.getPath())) {
            return;
        }

        String url = WatchOpenMessage.decodePayload(messageEvent.getData());
        Log.i(TAG, "Received handoff: " + url);
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(MainActivity.ACTION_OPEN_YOUTUBE)
                .putExtra(MainActivity.EXTRA_YOUTUBE_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
