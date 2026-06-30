package cl.dily.youtubepreview.shared;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public final class AccessUnitFrame {
    public static final int FLAG_CODEC_CONFIG = 2;
    public static final int HEADER_BYTES = Integer.BYTES + Long.BYTES + Integer.BYTES;

    private final long ptsUs;
    private final int flags;
    private final byte[] payload;

    public AccessUnitFrame(long ptsUs, int flags, byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        this.ptsUs = ptsUs;
        this.flags = flags;
        this.payload = Arrays.copyOf(payload, payload.length);
    }

    public long ptsUs() {
        return ptsUs;
    }

    public int flags() {
        return flags;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        DataOutputStream output = new DataOutputStream(outputStream);
        output.writeInt(payload.length);
        output.writeLong(ptsUs);
        output.writeInt(flags);
        output.write(payload);
        output.flush();
    }

    public static AccessUnitFrame readFrom(InputStream inputStream, int maxPayloadBytes) throws IOException {
        DataInputStream input = new DataInputStream(inputStream);
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Access unit length must be non-negative");
        }
        if (length > maxPayloadBytes) {
            throw new IOException("Access unit length " + length + " exceeds max " + maxPayloadBytes);
        }
        long ptsUs = input.readLong();
        int flags = input.readInt();
        byte[] payload = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(payload, offset, length - offset);
            if (read == -1) {
                throw new EOFException();
            }
            offset += read;
        }
        return new AccessUnitFrame(ptsUs, flags, payload);
    }
}

