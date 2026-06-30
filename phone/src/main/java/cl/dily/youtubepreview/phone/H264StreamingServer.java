package cl.dily.youtubepreview.phone;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import cl.dily.youtubepreview.shared.AccessUnitFrame;
import cl.dily.youtubepreview.shared.StreamHandshake;
import cl.dily.youtubepreview.shared.StreamSettings;
import cl.dily.youtubepreview.shared.StreamSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

final class H264StreamingServer {
    private static final String TAG = "YSWP-PhoneStream";
    private static final int PORT = 45990;
    private static final int WIDTH = 360;
    private static final int HEIGHT = 360;
    private static final StreamSettings SETTINGS = StreamSettings.fromRequested(500_000, 12);

    private final Context context;
    private final String source;
    private final Uri videoUri;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private ServerSocket serverSocket;

    H264StreamingServer(Context context, String source, Uri videoUri) {
        this.context = context.getApplicationContext();
        this.source = StreamSource.normalize(source);
        this.videoUri = videoUri;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::runServer, "yswp-h264-server");
        thread.start();
    }

    void stop() {
        running.set(false);
        closeServerSocket();
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runServer() {
        try (ServerSocket socket = new ServerSocket(PORT)) {
            serverSocket = socket;
            while (running.get()) {
                try (Socket client = socket.accept()) {
                    client.setTcpNoDelay(true);
                    streamTo(client);
                } catch (IOException error) {
                    if (running.get()) {
                        Log.w(TAG, "Client stream failed", error);
                    }
                }
            }
        } catch (IOException error) {
            if (running.get()) {
                Log.e(TAG, "Server failed", error);
            }
        } finally {
            serverSocket = null;
        }
    }

    private void streamTo(Socket client) throws IOException {
        OutputStream output = client.getOutputStream();
        String activeSource = activeSource();
        output.write((StreamHandshake.v1(WIDTH, HEIGHT, SETTINGS.fps(), activeSource).toJsonLine() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        output.flush();

        MediaCodec encoder = MediaCodec.createEncoderByType(StreamHandshake.CODEC);
        Surface inputSurface = null;
        boolean encoderStarted = false;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(StreamHandshake.CODEC, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, SETTINGS.bitrate());
            format.setInteger(MediaFormat.KEY_FRAME_RATE, SETTINGS.fps());
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();
            encoderStarted = true;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (StreamSource.SAMPLE_MP4.equals(activeSource) || StreamSource.FILE_URI.equals(activeSource)) {
                streamMedia(inputSurface, encoder, info, output, client, activeSource);
            } else {
                streamSynthetic(inputSurface, encoder, info, output, client);
            }
        } finally {
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (encoderStarted) {
                encoder.stop();
            }
            encoder.release();
        }
    }

    private void streamSynthetic(Surface inputSurface, MediaCodec encoder, MediaCodec.BufferInfo info, OutputStream output, Socket client)
            throws IOException {
        long frameIntervalNs = 1_000_000_000L / SETTINGS.fps();
        long nextFrameNs = System.nanoTime();
        int frame = 0;
        while (running.get() && !client.isClosed()) {
            drawFrame(inputSurface, frame++);
            drainEncoder(encoder, info, output);
            long sleepNs = nextFrameNs - System.nanoTime();
            if (sleepNs > 0) {
                sleepQuietly(sleepNs / 1_000_000L);
            }
            nextFrameNs += frameIntervalNs;
        }
    }

    private void streamMedia(
            Surface inputSurface,
            MediaCodec encoder,
            MediaCodec.BufferInfo info,
            OutputStream output,
            Socket client,
            String activeSource) throws IOException {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setSurface(inputSurface);
            player.setVolume(0f, 0f);
            player.setLooping(true);
            if (StreamSource.FILE_URI.equals(activeSource)) {
                player.setDataSource(context, videoUri);
            } else {
                try (AssetFileDescriptor descriptor = context.getResources().openRawResourceFd(R.raw.v1_sample)) {
                    player.setDataSource(
                            descriptor.getFileDescriptor(),
                            descriptor.getStartOffset(),
                            descriptor.getLength());
                }
            }
            player.prepare();
            player.start();
            while (running.get() && !client.isClosed()) {
                drainEncoder(encoder, info, output);
                sleepQuietly(5L);
            }
        } finally {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
        }
    }

    private void drawFrame(Surface surface, int frame) {
        Canvas canvas = surface.lockCanvas(null);
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int shade = 25 + (frame * 5) % 90;
            canvas.drawColor(Color.rgb(shade, 12, 30));
            paint.setColor(Color.rgb(20, 180, 255));
            canvas.drawCircle(WIDTH / 2f, HEIGHT / 2f, 70 + (frame % 36), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f);
            paint.setColor(Color.rgb(255, 210, 80));
            canvas.drawCircle(WIDTH / 2f, HEIGHT / 2f, 155, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(28f);
            paint.setColor(Color.WHITE);
            canvas.drawText("YSW V0", WIDTH / 2f, HEIGHT / 2f - 6, paint);
            paint.setTextSize(18f);
            canvas.drawText("frame " + frame, WIDTH / 2f, HEIGHT / 2f + 28, paint);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }

    private void drainEncoder(MediaCodec encoder, MediaCodec.BufferInfo info, OutputStream output) throws IOException {
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            }
            if (index < 0) {
                continue;
            }
            ByteBuffer buffer = encoder.getOutputBuffer(index);
            if (buffer != null && info.size > 0) {
                byte[] payload = new byte[info.size];
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                buffer.get(payload);
                new AccessUnitFrame(info.presentationTimeUs, info.flags, payload).writeTo(output);
            }
            encoder.releaseOutputBuffer(index, false);
        }
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String activeSource() {
        if (StreamSource.FILE_URI.equals(source) && videoUri == null) {
            Log.w(TAG, "File URI source requested without a URI; falling back to synthetic");
            return StreamSource.SYNTHETIC;
        }
        return source;
    }
}

