package eu.amadeos.apps.imageeditor.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.exif.GpsDirectory;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegMetadataTest {
    private static final SaveOptions ALL = new SaveOptions(ImageFormat.JPEG, 90, false, true, true, true);
    private static final SaveOptions NO_GPS = new SaveOptions(ImageFormat.JPEG, 90, false, true, false, true);

    private static byte[] plainJpeg() throws Exception {
        return ImageSaver.encodeJpeg(TestPictures.quarters(64, 48), 90);
    }

    private static byte[] original() throws Exception {
        return TestPictures.withSegments(plainJpeg(), TestPictures.exifSegment(), TestPictures.xmpSegment());
    }

    private static Metadata metadata(byte[] jpeg) throws Exception {
        return ImageMetadataReader.readMetadata(new ByteArrayInputStream(jpeg));
    }

    @Test
    void findsTheSegments() throws Exception {
        JpegMetadata.Segments s = JpegMetadata.read(original());
        assertNotNull(s.exif());
        assertNotNull(s.xmp());
        assertNull(s.iptc());
        assertFalse(s.empty());
        assertTrue(JpegMetadata.read(plainJpeg()).empty());
        assertTrue(JpegMetadata.read(new byte[]{1, 2, 3}).empty());
    }

    @Test
    void theTestBlockIsValidExif() throws Exception {
        Metadata m = metadata(original());
        assertEquals(6, m.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION));
        GpsDirectory gps = m.getFirstDirectoryOfType(GpsDirectory.class);
        assertEquals(47.66, latitude(gps), 0.01);
        assertNotNull(m.getFirstDirectoryOfType(ExifThumbnailDirectory.class));
    }

    @Test
    void keepsEverythingButCorrectsOrientationSizeAndPreview() throws Exception {
        byte[] out = JpegMetadata.insert(plainJpeg(), JpegMetadata.read(original()), ALL, 64, 48);
        Metadata m = metadata(out);
        assertEquals(1, m.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION));
        ExifSubIFDDirectory sub = m.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        assertEquals(64, sub.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
        assertEquals(48, sub.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
        assertEquals(47.66, latitude(m.getFirstDirectoryOfType(GpsDirectory.class)), 0.01);
        assertNull(m.getFirstDirectoryOfType(ExifThumbnailDirectory.class), "the preview showed the old picture");
        assertFalse(TestPictures.contains(out, TestPictures.THUMBNAIL_BYTE, 16));
        String text = new String(out, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("Evening at the Lake"));
        assertTrue(text.contains("GPSLongitude"));
    }

    @Test
    void withoutLocationTheGpsBytesAreGone() throws Exception {
        byte[] out = JpegMetadata.insert(plainJpeg(), JpegMetadata.read(original()), NO_GPS, 64, 48);
        Metadata m = metadata(out);
        assertNull(m.getFirstDirectoryOfType(GpsDirectory.class));
        assertNotNull(m.getFirstDirectoryOfType(ExifSubIFDDirectory.class), "the rest of EXIF stays");
        String text = new String(out, StandardCharsets.ISO_8859_1);
        assertFalse(text.contains("GPSLatitude"));
        assertFalse(text.contains("GPSLongitude"));
        assertTrue(text.contains("Evening at the Lake"));
        // The latitude's rationals (47/1) are overwritten too, not just unlinked.
        byte[] exif = JpegMetadata.read(out).exif();
        assertFalse(containsInt(exif, 3700));
    }

    @Test
    void withoutCameraDataNoExif() throws Exception {
        SaveOptions noCamera = new SaveOptions(ImageFormat.JPEG, 90, false, false, true, true);
        JpegMetadata.Segments s = JpegMetadata.read(JpegMetadata.insert(plainJpeg(), JpegMetadata.read(original()),
                noCamera, 64, 48));
        assertNull(s.exif());
        assertNotNull(s.xmp());
    }

    @Test
    void withoutDescriptionNoXmp() throws Exception {
        SaveOptions noDescription = new SaveOptions(ImageFormat.JPEG, 90, false, true, true, false);
        JpegMetadata.Segments s = JpegMetadata.read(JpegMetadata.insert(plainJpeg(), JpegMetadata.read(original()),
                noDescription, 64, 48));
        assertNotNull(s.exif());
        assertNull(s.xmp());
    }

    @Test
    void oldMetadataOfTheTargetIsReplaced() throws Exception {
        byte[] target = TestPictures.withSegments(plainJpeg(), TestPictures.exifSegment());
        SaveOptions none = new SaveOptions(ImageFormat.JPEG, 90, false, false, false, false);
        byte[] out = JpegMetadata.insert(target, JpegMetadata.Segments.NONE, none, 64, 48);
        assertTrue(JpegMetadata.read(out).empty());
        assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(out)), "still a readable JPEG");
    }

    @Test
    void resultIsAReadableJpeg() throws Exception {
        byte[] out = JpegMetadata.insert(plainJpeg(), JpegMetadata.read(original()), ALL, 64, 48);
        assertEquals(64, javax.imageio.ImageIO.read(new ByteArrayInputStream(out)).getWidth());
    }

    private static boolean containsInt(byte[] b, int value) {
        for (int i = 0; i + 4 <= b.length; i++) {
            int v = (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24;
            if (v == value) return true;
        }
        return false;
    }

    /** The latitude in degrees; the test block has no longitude, so getGeoLocation() would be null. */
    private static double latitude(GpsDirectory gps) {
        com.drew.lang.Rational[] dms = gps.getRationalArray(GpsDirectory.TAG_LATITUDE);
        return dms[0].doubleValue() + dms[1].doubleValue() / 60 + dms[2].doubleValue() / 3600;
    }
}
