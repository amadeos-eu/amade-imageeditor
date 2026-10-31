package eu.amadeos.apps.imageeditor.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ImageSaverTest {
    @TempDir
    Path dir;

    private final ImageSaver plain = new ImageSaver(CommandRunner.system());

    static boolean installed(String program) {
        return java.util.Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
                .anyMatch(d -> !d.isEmpty() && Files.isExecutable(Path.of(d, program)));
    }

    private Path jpegWithExif(String name, int w, int h) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, TestPictures.withSegments(ImageSaver.encodeJpeg(TestPictures.quarters(w, h), 95),
                TestPictures.exifSegment()));
        return file;
    }

    @Test
    void writesEveryWritableFormatButWebp() throws Exception {
        BufferedImage q = TestPictures.quarters(32, 16);
        for (ImageFormat f : List.of(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.TIFF, ImageFormat.BMP)) {
            Path target = dir.resolve("out." + f.extension());
            plain.save(q, Optional.empty(), null, target, SaveOptions.defaults(f));
            BufferedImage back = ImageIO.read(target.toFile());
            assertNotNull(back, f.name());
            assertEquals(32, back.getWidth(), f.name());
            assertEquals(16, back.getHeight(), f.name());
        }
    }

    @Test
    void pngKeepsTransparencyAndBmpGetsWhite() throws Exception {
        BufferedImage clear = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        plain.save(clear, Optional.empty(), null, dir.resolve("a.png"), SaveOptions.defaults(ImageFormat.PNG));
        assertEquals(0, ImageIO.read(dir.resolve("a.png").toFile()).getRGB(0, 0) >>> 24);
        plain.save(clear, Optional.empty(), null, dir.resolve("a.bmp"), SaveOptions.defaults(ImageFormat.BMP));
        assertEquals(0xffffff, ImageIO.read(dir.resolve("a.bmp").toFile()).getRGB(0, 0) & 0xffffff);
    }

    @Test
    void tiffIsCompressed() throws Exception {
        BufferedImage flat = TestPictures.filled(400, 400, 0x336699);
        Path target = dir.resolve("flat.tif");
        plain.save(flat, Optional.empty(), null, target, SaveOptions.defaults(ImageFormat.TIFF));
        assertTrue(Files.size(target) < 400 * 400 * 3 / 10, "LZW: " + Files.size(target));
    }

    @Test
    void jpegQualityMakesADifference() throws Exception {
        BufferedImage q = TestPictures.quarters(200, 200);
        for (int x = 0; x < 200; x += 3) q.setRGB(x, x, 0x123456 * x);
        Path low = dir.resolve("low.jpg"), high = dir.resolve("high.jpg");
        plain.save(q, Optional.empty(), null, low, new SaveOptions(ImageFormat.JPEG, 20, false, true, true, true));
        plain.save(q, Optional.empty(), null, high, new SaveOptions(ImageFormat.JPEG, 98, false, true, true, true));
        assertTrue(Files.size(low) < Files.size(high));
    }

    @Test
    void jpegFromJpegKeepsTheMetadata() throws Exception {
        Path original = jpegWithExif("orig.jpg", 64, 48);
        ImageSaver.Source source = new ImageSaver.Source(original, ImageFormat.JPEG, Orientation.ROTATE_90);
        Path target = dir.resolve("edit.jpg");
        BufferedImage edited = TestPictures.quarters(40, 30);
        plain.save(edited, Optional.empty(), source, target, SaveOptions.defaults(ImageFormat.JPEG));
        var m = ImageMetadataReader.readMetadata(target.toFile());
        assertEquals(1, m.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION));
        assertNotNull(m.getFirstDirectoryOfType(GpsDirectory.class));
        SaveOptions noGps = new SaveOptions(ImageFormat.JPEG, 90, false, true, false, true);
        plain.save(edited, Optional.empty(), source, target, noGps);
        assertNull(ImageMetadataReader.readMetadata(target.toFile()).getFirstDirectoryOfType(GpsDirectory.class));
    }

    @Test
    void noPartFileIsLeftBehindWhenWritingFails() {
        ImageSaver failing = new ImageSaver(command -> {
            throw new ProgramMissingException("cwebp", new IOException("no"));
        });
        Path target = dir.resolve("x.webp");
        assertThrows(ImageSaver.CwebpMissingException.class, () -> failing.save(TestPictures.quarters(4, 4),
                Optional.empty(), null, target, SaveOptions.defaults(ImageFormat.WEBP)));
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(dir.resolve(".x.webp.part")));
    }

    @Test
    void webpCallsCwebpWithTheQuality() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        ImageSaver fake = new ImageSaver(command -> {
            commands.add(command);
            // Write something where cwebp would write the WebP file.
            Files.writeString(Path.of(command.get(command.indexOf("-o") + 1)), "RIFF");
            return new ProcessBuilder("true").start();
        });
        Path target = dir.resolve("x.webp");
        fake.save(TestPictures.quarters(4, 4), Optional.empty(), null, target,
                new SaveOptions(ImageFormat.WEBP, 70, false, true, true, true));
        assertTrue(Files.exists(target));
        List<String> c = commands.getFirst();
        assertEquals("cwebp", c.getFirst());
        assertEquals("70", c.get(c.indexOf("-q") + 1));
        fake.save(TestPictures.quarters(4, 4), Optional.empty(), null, target,
                new SaveOptions(ImageFormat.WEBP, 70, true, true, true, true));
        assertTrue(commands.get(1).contains("-lossless"));
        assertFalse(commands.get(1).contains("-q"));
    }

    @Test
    void realCwebpWritesAReadableWebp() throws Exception {
        assumeTrue(installed("cwebp"), "cwebp is not installed: sudo apt install webp");
        Path target = dir.resolve("real.webp");
        plain.save(TestPictures.quarters(40, 20), Optional.empty(), null, target, SaveOptions.defaults(ImageFormat.WEBP));
        BufferedImage back = ImageIO.read(target.toFile());
        assertEquals(40, back.getWidth());
    }

    // --8<-- [start:jpegtran-test]
    @Test
    void onlyTurnsAreSavedWithoutLossByJpegtran() throws Exception {
        assumeTrue(installed("jpegtran"), "jpegtran is not installed: sudo apt install libjpeg-turbo-progs");
        Path original = jpegWithExif("turn.jpg", 64, 48);
        byte[] before = Files.readAllBytes(original);
        ImageSaver.Source source = new ImageSaver.Source(original, ImageFormat.JPEG, Orientation.NORMAL);
        Path target = dir.resolve("turned.jpg");
        ImageSaver.Result result = plain.save(TestPictures.quarters(48, 64), Optional.of(Orientation.ROTATE_90),
                source, target, SaveOptions.defaults(ImageFormat.JPEG));
        assertTrue(result.lossless());
        assertFalse(result.trimmed());
        BufferedImage back = ImageIO.read(target.toFile());
        assertEquals(48, back.getWidth());
        assertEquals(64, back.getHeight());
        var m = ImageMetadataReader.readMetadata(target.toFile());
        assertEquals(1, m.getFirstDirectoryOfType(ExifIFD0Directory.class).getInt(ExifIFD0Directory.TAG_ORIENTATION));
        // The original is untouched.
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(original)));
    }
    // --8<-- [end:jpegtran-test]

    @Test
    void oddSizesAreTrimmedByJpegtran() throws Exception {
        assumeTrue(installed("jpegtran"), "jpegtran is not installed: sudo apt install libjpeg-turbo-progs");
        Path original = jpegWithExif("odd.jpg", 61, 45);
        ImageSaver.Source source = new ImageSaver.Source(original, ImageFormat.JPEG, Orientation.NORMAL);
        Path target = dir.resolve("odd-turned.jpg");
        ImageSaver.Result result = plain.save(TestPictures.quarters(45, 61), Optional.of(Orientation.ROTATE_90),
                source, target, SaveOptions.defaults(ImageFormat.JPEG));
        assertTrue(result.lossless());
        assertTrue(result.trimmed());
    }

    @Test
    void withoutJpegtranTheJpegIsEncoded() throws Exception {
        ImageSaver noJpegtran = new ImageSaver(command -> {
            throw new ProgramMissingException("jpegtran", new IOException("no"));
        });
        Path original = jpegWithExif("o.jpg", 64, 48);
        ImageSaver.Source source = new ImageSaver.Source(original, ImageFormat.JPEG, Orientation.NORMAL);
        ImageSaver.Result result = noJpegtran.save(TestPictures.quarters(48, 64), Optional.of(Orientation.ROTATE_90),
                source, dir.resolve("t.jpg"), SaveOptions.defaults(ImageFormat.JPEG));
        assertFalse(result.lossless());
        assertEquals(48, ImageIO.read(dir.resolve("t.jpg").toFile()).getWidth());
    }

    @Test
    void replacesAnExistingFile() throws Exception {
        Path target = dir.resolve("same.png");
        Files.writeString(target, "old");
        plain.save(TestPictures.quarters(8, 8), Optional.empty(), null, target, SaveOptions.defaults(ImageFormat.PNG));
        assertEquals(8, ImageIO.read(target.toFile()).getWidth());
    }

}
