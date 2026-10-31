package eu.amadeos.apps.imageeditor.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Pictures and EXIF blocks for the tests. */
public final class TestPictures {
    private TestPictures() {
    }

    /** Four coloured quarters: red top left, green top right, blue bottom left, white bottom right. */
    public static BufferedImage quarters(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h / 2);
        g.setColor(Color.GREEN);
        g.fillRect(w / 2, 0, w - w / 2, h / 2);
        g.setColor(Color.BLUE);
        g.fillRect(0, h / 2, w / 2, h - h / 2);
        g.setColor(Color.WHITE);
        g.fillRect(w / 2, h / 2, w - w / 2, h - h / 2);
        g.dispose();
        return image;
    }

    public static BufferedImage filled(int w, int h, int rgb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(rgb));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    /** A byte that never appears elsewhere in the test EXIF block, to find the thumbnail. */
    public static final byte THUMBNAIL_BYTE = (byte) 0xAB;

    /**
     * An APP1 EXIF segment (marker and length included), little-endian, with orientation 6, pixel size 640 ×
     * 480, a GPS position 47° 39' 37" N and a 16-byte stand-in for the preview picture.
     */
    public static byte[] exifSegment() {
        ByteBuffer t = ByteBuffer.allocate(180).order(ByteOrder.LITTLE_ENDIAN);
        t.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        // IFD 0 at 8: orientation, EXIF IFD, GPS IFD; next IFD 1 at 134.
        t.position(8);
        t.putShort((short) 3);
        entry(t, 0x0112, 3, 1, 6);
        entry(t, 0x8769, 4, 1, 50);
        entry(t, 0x8825, 4, 1, 80);
        t.putInt(134);
        // EXIF IFD at 50: pixel width and height.
        t.position(50);
        t.putShort((short) 2);
        entry(t, 0xA002, 4, 1, 640);
        entry(t, 0xA003, 4, 1, 480);
        t.putInt(0);
        // GPS IFD at 80: latitude reference "N" and latitude (three rationals at 110).
        t.position(80);
        t.putShort((short) 2);
        t.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        entry(t, 0x0002, 5, 3, 110);
        t.putInt(0);
        t.position(110);
        t.putInt(47).putInt(1).putInt(39).putInt(1).putInt(3700).putInt(100);
        // IFD 1 at 134: the preview picture at 164, 16 bytes.
        t.position(134);
        t.putShort((short) 2);
        entry(t, 0x0201, 4, 1, 164);
        entry(t, 0x0202, 4, 1, 16);
        t.putInt(0);
        t.position(164);
        for (int i = 0; i < 16; i++) t.put(THUMBNAIL_BYTE);
        return segment(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), t.array()));
    }

    /** An XMP segment with a title and a GPS position, both as attribute and as element. */
    public static byte[] xmpSegment() {
        String xml = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                + "<rdf:Description xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:exif=\"http://ns.adobe.com/exif/1.0/\""
                + " exif:GPSLatitude=\"47,39.6N\">"
                + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">Evening at the Lake</rdf:li></rdf:Alt></dc:title>"
                + "<exif:GPSLongitude>9,10.5E</exif:GPSLongitude>"
                + "</rdf:Description></rdf:RDF></x:xmpmeta>";
        return segment(0xE1, concat("http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1),
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** A JPEG with the segments placed right after the start marker. */
    public static byte[] withSegments(byte[] jpeg, byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        for (byte[] s : segments) out.write(s, 0, s.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void entry(ByteBuffer t, int tag, int type, int count, int value) {
        t.putShort((short) tag).putShort((short) type).putInt(count);
        if (type == 3) t.putShort((short) value).putShort((short) 0);
        else t.putInt(value);
    }

    private static byte[] segment(int marker, byte[] body) {
        int length = body.length + 2;
        byte[] s = new byte[length + 2];
        s[0] = (byte) 0xFF;
        s[1] = (byte) marker;
        s[2] = (byte) (length >> 8);
        s[3] = (byte) length;
        System.arraycopy(body, 0, s, 4, body.length);
        return s;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static boolean contains(byte[] haystack, byte value, int run) {
        int count = 0;
        for (byte b : haystack) {
            count = b == value ? count + 1 : 0;
            if (count >= run) return true;
        }
        return false;
    }
}
