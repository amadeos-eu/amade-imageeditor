package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageLoaderTest {
    @TempDir
    Path dir;

    private final ImageLoader loader = new ImageLoader();

    @Test
    void opensAPng() throws Exception {
        Path file = dir.resolve("a.png");
        ImageIO.write(TestPictures.quarters(30, 20), "png", file.toFile());
        ImageLoader.Loaded loaded = loader.load(file);
        assertEquals(30, loaded.image().getWidth());
        assertEquals(ImageFormat.PNG, loaded.format());
        assertEquals(Orientation.NORMAL, loaded.stored());
        assertEquals(BufferedImage.TYPE_INT_RGB, loaded.image().getType());
    }

    @Test
    void turnsAJpegUprightByItsExifOrientation() throws Exception {
        Path file = dir.resolve("phone.jpg");
        Files.write(file, TestPictures.withSegments(ImageSaver.encodeJpeg(TestPictures.quarters(64, 48), 95),
                TestPictures.exifSegment()));
        ImageLoader.Loaded loaded = loader.load(file);
        assertEquals(Orientation.ROTATE_90, loaded.stored());
        assertEquals(48, loaded.image().getWidth());
        assertEquals(64, loaded.image().getHeight());
    }

    @Test
    void opensFormatsOfTheTwelveMonkeysPlugins() throws Exception {
        // TGA and PNM are read by TwelveMonkeys only; they are written here by its writers.
        for (String format : new String[]{"tga", "pnm", "tif"}) {
            Path file = dir.resolve("p." + format);
            boolean written = ImageIO.write(TestPictures.quarters(10, 6), format.equals("tif") ? "tiff" : format,
                    file.toFile());
            if (!written) continue;
            assertEquals(10, loader.load(file).image().getWidth(), format);
        }
    }

    @Test
    void unknownContentIsReported() throws Exception {
        Path file = dir.resolve("fake.png");
        Files.writeString(file, "no picture");
        assertThrows(ImageLoader.UnsupportedFormatException.class, () -> loader.load(file));
    }

    @Test
    void grayPicturesBecomeRgb() throws Exception {
        Path file = dir.resolve("gray.png");
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_BYTE_GRAY), "png", file.toFile());
        assertEquals(BufferedImage.TYPE_INT_RGB, loader.load(file).image().getType());
    }
}
