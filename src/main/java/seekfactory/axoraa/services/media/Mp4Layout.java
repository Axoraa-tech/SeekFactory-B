package seekfactory.axoraa.services.media;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Reads just enough of an MP4's top-level boxes to tell whether playback can start early (the
 * "moov" index before the "mdat" media data) and how long the video runs. No decoding, no ffprobe.
 */
public record Mp4Layout(boolean indexFirst, double durationSeconds) {

    public static Mp4Layout read(Path file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long length = in.length();
            long position = 0;
            Boolean indexFirst = null;
            double duration = 0;
            while (position + 8 <= length) {
                in.seek(position);
                long size = Integer.toUnsignedLong(in.readInt());
                String type = readType(in);
                long header = 8;
                if (size == 1) {           // 64-bit size follows
                    size = in.readLong();
                    header = 16;
                } else if (size == 0) {    // box runs to the end of the file
                    size = length - position;
                }
                if (size < header) break;  // corrupt: stop rather than loop
                if ("moov".equals(type)) {
                    if (indexFirst == null) indexFirst = true;
                    duration = readDuration(in, position + header, position + size);
                } else if ("mdat".equals(type) && indexFirst == null) {
                    indexFirst = false;
                }
                position += size;
            }
            return new Mp4Layout(Boolean.TRUE.equals(indexFirst), duration);
        }
    }

    /** Duration from moov/mvhd (timescale and duration, version 0 or 1). */
    private static double readDuration(RandomAccessFile in, long start, long end) throws IOException {
        long position = start;
        while (position + 8 <= end) {
            in.seek(position);
            long size = Integer.toUnsignedLong(in.readInt());
            String type = readType(in);
            if (size < 8) break;
            if ("mvhd".equals(type)) {
                int version = in.readUnsignedByte();
                in.skipBytes(3);  // flags
                if (version == 1) {
                    in.skipBytes(16);  // creation + modification time
                    long timescale = Integer.toUnsignedLong(in.readInt());
                    long duration = in.readLong();
                    return timescale > 0 ? (double) duration / timescale : 0;
                }
                in.skipBytes(8);
                long timescale = Integer.toUnsignedLong(in.readInt());
                long duration = Integer.toUnsignedLong(in.readInt());
                return timescale > 0 ? (double) duration / timescale : 0;
            }
            position += size;
        }
        return 0;
    }

    private static String readType(RandomAccessFile in) throws IOException {
        byte[] type = new byte[4];
        in.readFully(type);
        return new String(type, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
