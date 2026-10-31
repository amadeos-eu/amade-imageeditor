package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.SaveOptions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Carries the metadata of an original JPEG into the edited one: the EXIF block (camera, date, exposure, location),
 * the XMP packet and the IPTC block (title, caption, keywords, author, copyright). ImageIO writes none of these,
 * so the segments are copied as bytes, after a few corrections in the EXIF block:
 * <ul>
 *     <li>the orientation becomes "normal", since the pixels are upright now;</li>
 *     <li>the stored pixel width and height become the new ones;</li>
 *     <li>the small preview picture is removed, because it would still show the uncropped original;</li>
 *     <li>without "keep location" the GPS entries are removed, and their bytes overwritten with zeros.</li>
 * </ul>
 */
public final class JpegMetadata {
    private static final byte[] EXIF = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] XMP = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] IPTC = "Photoshop 3.0\0".getBytes(StandardCharsets.ISO_8859_1);

    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TAG_EXIF_IFD = 0x8769;
    private static final int TAG_GPS_IFD = 0x8825;
    private static final int TAG_THUMBNAIL_OFFSET = 0x0201;
    private static final int TAG_THUMBNAIL_LENGTH = 0x0202;
    private static final int TAG_PIXEL_WIDTH = 0xA002;
    private static final int TAG_PIXEL_HEIGHT = 0xA003;
    /** Bytes per value of the TIFF field types 1 to 12. */
    private static final int[] TYPE_SIZE = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};

    private static final Pattern XMP_GPS_ATTRIBUTE = Pattern.compile("\\s+exif:GPS\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*')");
    private static final Pattern XMP_GPS_ELEMENT = Pattern.compile("<exif:GPS(\\w+)\\b[^>]*?(/>|>.*?</exif:GPS\\1>)",
            Pattern.DOTALL);

    private JpegMetadata() {
    }

    /** The metadata segments of a JPEG, each with its marker and length bytes. */
    public record Segments(byte[] exif, byte[] xmp, byte[] iptc) {
        public static final Segments NONE = new Segments(null, null, null);

        public boolean empty() {
            return exif == null && xmp == null && iptc == null;
        }
    }

    // --8<-- [start:read]
    /** Finds the EXIF, XMP and IPTC segments in the headers of a JPEG. */
    public static Segments read(byte[] jpeg) {
        byte[] exif = null, xmp = null, iptc = null;
        if (jpeg.length < 4 || (jpeg[0] & 0xff) != 0xFF || (jpeg[1] & 0xff) != 0xD8) return Segments.NONE;
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xff;
            if (marker == 0xDA || marker == 0xD9) break;
            int length = u16(jpeg, pos + 2, false);
            int end = pos + 2 + length;
            if (length < 2 || end > jpeg.length) break;
            byte[] segment = Arrays.copyOfRange(jpeg, pos, end);
            if (marker == 0xE1 && exif == null && startsWith(jpeg, pos + 4, EXIF)) exif = segment;
            else if (marker == 0xE1 && xmp == null && startsWith(jpeg, pos + 4, XMP)) xmp = segment;
            else if (marker == 0xED && iptc == null && startsWith(jpeg, pos + 4, IPTC)) iptc = segment;
            pos = end;
        }
        return new Segments(exif, xmp, iptc);
    }
    // --8<-- [end:read]

    // --8<-- [start:insert]
    /**
     * The new JPEG with the chosen metadata of the original: after the start marker and the JFIF header, before
     * everything else. Metadata the new JPEG has already (from jpegtran) is replaced.
     */
    public static byte[] insert(byte[] jpeg, Segments original, SaveOptions options, int width, int height) {
        List<byte[]> segments = new ArrayList<>();
        if (options.keepCamera() && original.exif() != null) {
            byte[] exif = original.exif().clone();
            fixExif(exif, width, height, options.keepLocation());
            segments.add(exif);
        }
        if (options.keepDescription() && original.xmp() != null) {
            segments.add(options.keepLocation() ? original.xmp() : withoutGps(original.xmp()));
        }
        if (options.keepDescription() && original.iptc() != null) segments.add(original.iptc());

        byte[] stripped = strip(jpeg);
        int at = afterJfif(stripped);
        ByteArrayOutputStream out = new ByteArrayOutputStream(stripped.length + 70000);
        out.write(stripped, 0, at);
        for (byte[] segment : segments) out.write(segment, 0, segment.length);
        out.write(stripped, at, stripped.length - at);
        return out.toByteArray();
    }
    // --8<-- [end:insert]

    /** The JPEG without EXIF, XMP and IPTC segments. */
    static byte[] strip(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xff) != 0xFF || (jpeg[1] & 0xff) != 0xD8) return jpeg;
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(jpeg, 0, 2);
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xff;
            if (marker == 0xDA || marker == 0xD9) break;
            int length = u16(jpeg, pos + 2, false);
            int end = pos + 2 + length;
            if (length < 2 || end > jpeg.length) break;
            boolean metadata = marker == 0xE1 && (startsWith(jpeg, pos + 4, EXIF) || startsWith(jpeg, pos + 4, XMP)
                    || startsWith(jpeg, pos + 4, "http://ns.adobe.com/xmp/extension/".getBytes(StandardCharsets.ISO_8859_1)))
                    || marker == 0xED;
            if (!metadata) out.write(jpeg, pos, end - pos);
            pos = end;
        }
        out.write(jpeg, pos, jpeg.length - pos);
        return out.toByteArray();
    }

    private static int afterJfif(byte[] jpeg) {
        int pos = 2;
        if (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xFF && (jpeg[pos + 1] & 0xff) == 0xE0) {
            pos += 2 + u16(jpeg, pos + 2, false);
        }
        return Math.min(pos, jpeg.length);
    }

    // --8<-- [start:fix-exif]
    /** Corrects the EXIF segment in place; see the class description. */
    static void fixExif(byte[] segment, int width, int height, boolean keepLocation) {
        Tiff t = Tiff.of(segment);
        if (t == null) return;
        int ifd0 = t.ifd0();
        if (ifd0 < 0) return;
        int count = t.u16(ifd0);
        for (int i = count - 1; i >= 0; i--) {
            int entry = ifd0 + 2 + i * 12;
            int tag = t.u16(entry);
            if (tag == TAG_ORIENTATION && t.u16(entry + 2) == 3) t.put16(entry + 8, 1);
            else if (tag == TAG_EXIF_IFD) fixSize(t, (int) t.u32(entry + 8), width, height);
            else if (tag == TAG_GPS_IFD && !keepLocation) {
                t.wipeIfd((int) t.u32(entry + 8));
                t.removeEntry(ifd0, i);
            }
        }
        // IFD 1 holds the preview picture; dropping it ends the chain after IFD 0.
        int next = ifd0 + 2 + t.u16(ifd0) * 12;
        long ifd1 = t.u32(next);
        if (ifd1 != 0) {
            int pos = t.tiff + (int) ifd1;
            if (t.validIfd(pos)) {
                long thumbOffset = -1, thumbLength = -1;
                for (int i = 0; i < t.u16(pos); i++) {
                    int entry = pos + 2 + i * 12;
                    if (t.u16(entry) == TAG_THUMBNAIL_OFFSET) thumbOffset = t.u32(entry + 8);
                    if (t.u16(entry) == TAG_THUMBNAIL_LENGTH) thumbLength = t.u32(entry + 8);
                }
                if (thumbOffset > 0 && thumbLength > 0) t.zero(t.tiff + thumbOffset, thumbLength);
                t.wipeIfd((int) ifd1);
            }
            t.put32(next, 0);
        }
    }

    private static void fixSize(Tiff t, int offset, int width, int height) {
        int pos = t.tiff + offset;
        if (!t.validIfd(pos)) return;
        for (int i = 0; i < t.u16(pos); i++) {
            int entry = pos + 2 + i * 12;
            int tag = t.u16(entry), type = t.u16(entry + 2);
            if (tag != TAG_PIXEL_WIDTH && tag != TAG_PIXEL_HEIGHT) continue;
            int value = tag == TAG_PIXEL_WIDTH ? width : height;
            if (type == 3) t.put16(entry + 8, Math.min(value, 0xffff));
            else if (type == 4) t.put32(entry + 8, value);
        }
    }
    // --8<-- [end:fix-exif]

    /** The XMP segment without the exif:GPS properties. */
    static byte[] withoutGps(byte[] segment) {
        int header = 4 + XMP.length;
        String xml = new String(segment, header, segment.length - header, StandardCharsets.UTF_8);
        String clean = XMP_GPS_ELEMENT.matcher(XMP_GPS_ATTRIBUTE.matcher(xml).replaceAll("")).replaceAll("");
        byte[] body = clean.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = 2 + XMP.length + body.length;
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length);
        out.write(XMP, 0, XMP.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    /** The TIFF structure inside an EXIF segment. */
    private static final class Tiff {
        final byte[] b;
        final int tiff;
        final int end;
        final boolean little;

        private Tiff(byte[] b, int tiff, boolean little) {
            this.b = b;
            this.tiff = tiff;
            this.end = b.length;
            this.little = little;
        }

        static Tiff of(byte[] segment) {
            int tiff = 4 + EXIF.length;
            if (segment.length < tiff + 8) return null;
            boolean little = segment[tiff] == 'I' && segment[tiff + 1] == 'I';
            if (!little && !(segment[tiff] == 'M' && segment[tiff + 1] == 'M')) return null;
            return new Tiff(segment, tiff, little);
        }

        int ifd0() {
            int pos = tiff + (int) u32(tiff + 4);
            return validIfd(pos) ? pos : -1;
        }

        boolean validIfd(int pos) {
            return pos > tiff && pos + 2 <= end && pos + 2 + u16(pos) * 12 + 4 <= end;
        }

        /** Overwrites an IFD's entries, their values and the IFD itself with zeros. */
        void wipeIfd(int offset) {
            int pos = tiff + offset;
            if (!validIfd(pos)) return;
            int count = u16(pos);
            for (int i = 0; i < count; i++) {
                int entry = pos + 2 + i * 12;
                int type = u16(entry + 2);
                long size = type > 0 && type < TYPE_SIZE.length ? TYPE_SIZE[type] * u32(entry + 4) : 0;
                if (size > 4) zero(tiff + u32(entry + 8), size);
            }
            zero(pos, 2 + count * 12L + 4);
        }

        /** Takes entry {@code index} out of the IFD; the entries after it and the next-IFD link move up. */
        void removeEntry(int ifd, int index) {
            int count = u16(ifd);
            int from = ifd + 2 + (index + 1) * 12;
            int tail = ifd + 2 + count * 12 + 4;
            System.arraycopy(b, from, b, from - 12, tail - from);
            Arrays.fill(b, tail - 12, tail, (byte) 0);
            put16(ifd, count - 1);
        }

        void zero(long from, long length) {
            if (from < tiff || from >= end || length <= 0) return;
            Arrays.fill(b, (int) from, (int) Math.min(end, from + length), (byte) 0);
        }

        int u16(int pos) {
            return JpegMetadata.u16(b, pos, little);
        }

        long u32(int pos) {
            long v = 0;
            for (int i = 0; i < 4; i++) v = v << 8 | b[pos + (little ? 3 - i : i)] & 0xff;
            return v;
        }

        void put16(int pos, int value) {
            b[pos + (little ? 0 : 1)] = (byte) value;
            b[pos + (little ? 1 : 0)] = (byte) (value >> 8);
        }

        void put32(int pos, long value) {
            for (int i = 0; i < 4; i++) b[pos + (little ? i : 3 - i)] = (byte) (value >> (8 * i));
        }
    }

    private static boolean startsWith(byte[] b, int pos, byte[] prefix) {
        if (pos + prefix.length > b.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (b[pos + i] != prefix[i]) return false;
        }
        return true;
    }

    static int u16(byte[] b, int pos, boolean little) {
        int a = b[pos] & 0xff, c = b[pos + 1] & 0xff;
        return little ? a | c << 8 : a << 8 | c;
    }
}
