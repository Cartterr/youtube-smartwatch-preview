package cl.dily.youtubepreview.wear;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import cl.dily.youtubepreview.shared.AccessUnitFrame;
import cl.dily.youtubepreview.shared.StreamHandshake;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

final class PreviewClient {
    interface StatusListener {
        void onStatus(String status, boolean keepScreenOn);
    }

    private static final String TAG = "YSWP-WatchPreview";
    private static final int PORT = 45990;
    private static final int MAX_ACCESS_UNIT_BYTES = 1024 * 1024;

    private final String host;
    private final Surface surface;
    private final StatusListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private volatile Socket activeSocket;

    PreviewClient(String host, Surface surface, StatusListener listener) {
        this.host = host;
        this.surface = surface;
        this.listener = listener;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::runLoop, "yswp-preview-client");
        thread.start();
    }

    void stop() {
        running.set(false);
        closeActiveSocket();
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        long backoffMs = 500L;
        while (running.get()) {
            try {
                connectAndDecode();
                backoffMs = 500L;
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Preview connection failed", error);
                listener.onStatus("Reconnect " + host + " in " + backoffMs + "ms", false);
                sleep(backoffMs);
                backoffMs = Math.min(8_000L, backoffMs * 2L);
            }
        }
    }

    private void connectAndDecode() throws IOException {
        try (Socket socket = new Socket()) {
            activeSocket = socket;
            listener.onStatus("Connecting " + host + ":" + PORT, false);
            socket.connect(new InetSocketAddress(host, PORT), 4_000);
            socket.setTcpNoDelay(true);
            InputStream input = socket.getInputStream();
            StreamHandshake handshake = StreamHandshake.parse(readAsciiLine(input));
            if (!running.get()) {
                return;
            }
            listener.onStatus("Connected " + handshake.source() + " " + handshake.width() + "x" + handshake.height() + " @" + handshake.fps() + "fps", true);
            decode(input, handshake);
        } finally {
            activeSocket = null;
        }
    }

    private void decode(InputStream input, StreamHandshake handshake) throws IOException {
        if (!running.get() || !surface.isValid()) {
            throw new IOException("Surface is no longer valid");
        }
        MediaCodec decoder = MediaCodec.createDecoderByType(handshake.codec());
        boolean started = false;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(handshake.codec(), handshake.width(), handshake.height());
            decoder.configure(format, surface, null, 0);
            decoder.start();
            started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long frames = 0;
            long startedAtMs = System.currentTimeMillis();
            while (running.get()) {
                AccessUnitFrame frame = AccessUnitFrame.readFrom(input, MAX_ACCESS_UNIT_BYTES);
                queueInput(decoder, frame);
                frames += drainOutput(decoder, info);
                long elapsed = Math.max(1L, System.currentTimeMillis() - startedAtMs);
                if (frames > 0 && frames % 30 == 0) {
                    long fps = frames * 1000L / elapsed;
                    listener.onStatus("Rendering " + fps + "fps from " + host, true);
                }
            }
        } finally {
            if (started) {
                decoder.stop();
            }
            decoder.release();
        }
    }

    private void queueInput(MediaCodec decoder, AccessUnitFrame frame) {
        int inputIndex = decoder.dequeueInputBuffer(10_000);
        if (inputIndex < 0) {
            return;
        }
        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
        byte[] payload = frame.payload();
        if (inputBuffer == null || payload.length > inputBuffer.capacity()) {
            decoder.queueInputBuffer(inputIndex, 0, 0, frame.ptsUs(), 0);
            return;
        }
        inputBuffer.clear();
        inputBuffer.put(payload);
        decoder.queueInputBuffer(inputIndex, 0, payload.length, frame.ptsUs(), frame.flags());
    }

    private int drainOutput(MediaCodec decoder, MediaCodec.BufferInfo info) {
        int rendered = 0;
        while (true) {
            int outputIndex = decoder.dequeueOutputBuffer(info, 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return rendered;
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || outputIndex < 0) {
                continue;
            }
            boolean render = info.size > 0;
            decoder.releaseOutputBuffer(outputIndex, render);
            if (render) {
                rendered++;
            }
        }
    }

    private String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value == -1) {
                throw new IOException("Stream ended before handshake");
            }
            if (value == '\n') {
                return output.toString(StandardCharsets.UTF_8.name());
            }
            if (value != '\r') {
                output.write(value);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeActiveSocket() {
        Socket socket = activeSocket;
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}

