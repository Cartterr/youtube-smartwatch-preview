package cl.dily.youtubepreview.phone;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import cl.dily.youtubepreview.shared.AccessUnitFrame;
import cl.dily.youtubepreview.shared.StreamHandshake;
import cl.dily.youtubepreview.shared.StreamSettings;
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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private ServerSocket serverSocket;

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
        output.write((StreamHandshake.v0(WIDTH, HEIGHT, SETTINGS.fps()).toJsonLine() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        output.flush();

        MediaCodec encoder = MediaCodec.createEncoderByType(StreamHandshake.CODEC);
        Surface inputSurface = null;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(StreamHandshake.CODEC, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, SETTINGS.bitrate());
            format.setInteger(MediaFormat.KEY_FRAME_RATE, SETTINGS.fps());
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();

            long frameIntervalNs = 1_000_000_000L / SETTINGS.fps();
            long nextFrameNs = System.nanoTime();
            int frame = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (running.get() && !client.isClosed()) {
                drawFrame(inputSurface, frame++);
                drainEncoder(encoder, info, output);
                long sleepNs = nextFrameNs - System.nanoTime();
                if (sleepNs > 0) {
                    sleepQuietly(sleepNs / 1_000_000L);
                }
                nextFrameNs += frameIntervalNs;
            }
        } finally {
            if (inputSurface != null) {
                inputSurface.release();
            }
            encoder.stop();
            encoder.release();
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
}

