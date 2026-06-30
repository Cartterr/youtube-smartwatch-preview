package cl.dily.youtubepreview.shared;

public final class StreamSource {
    public static final String SYNTHETIC = "synthetic";
    public static final String SAMPLE_MP4 = "sample-mp4";
    public static final String FILE_URI = "file-uri";

    private StreamSource() {
    }

    public static String normalize(String source) {
        if (source == null) {
            return SYNTHETIC;
        }
        String value = source.trim();
        if (SAMPLE_MP4.equals(value) || FILE_URI.equals(value) || SYNTHETIC.equals(value)) {
            return value;
        }
        return SYNTHETIC;
    }
}
