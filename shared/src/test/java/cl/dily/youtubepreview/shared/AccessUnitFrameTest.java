package cl.dily.youtubepreview.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AccessUnitFrameTest {
    @Test
    void roundTripsFrameHeaderAndPayload() throws IOException {
        AccessUnitFrame frame = new AccessUnitFrame(123_456L, AccessUnitFrame.FLAG_CODEC_CONFIG, new byte[] {1, 2, 3, 4});
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        frame.writeTo(output);
        AccessUnitFrame decoded = AccessUnitFrame.readFrom(new ByteArrayInputStream(output.toByteArray()), 1024);

        assertEquals(123_456L, decoded.ptsUs());
        assertEquals(AccessUnitFrame.FLAG_CODEC_CONFIG, decoded.flags());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, decoded.payload());
    }

    @Test
    void rejectsPayloadLargerThanConfiguredLimit() throws IOException {
        AccessUnitFrame frame = new AccessUnitFrame(0L, 0, new byte[] {9, 8, 7});
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        frame.writeTo(output);

        IOException error = assertThrows(
                IOException.class,
                () -> AccessUnitFrame.readFrom(new ByteArrayInputStream(output.toByteArray()), 2));

        assertEquals("Access unit length 3 exceeds max 2", error.getMessage());
    }

    @Test
    void rejectsTruncatedPayload() throws IOException {
        byte[] truncated = new byte[] {
                0, 0, 0, 4,
                0, 0, 0, 0, 0, 0, 0, 1,
                0, 0, 0, 0,
                1, 2
        };

        assertThrows(EOFException.class, () -> AccessUnitFrame.readFrom(new ByteArrayInputStream(truncated), 1024));
    }
}

