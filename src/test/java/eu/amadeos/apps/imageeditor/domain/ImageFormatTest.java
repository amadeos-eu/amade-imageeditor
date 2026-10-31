package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFormatTest {
    @Test
    void byExtensionIgnoringCase() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.of(Path.of("/a/IMG_1.JPG")));
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.of(Path.of("b.jpeg")));
        assertEquals(Optional.of(ImageFormat.TIFF), ImageFormat.of(Path.of("scan.tiff")));
        assertEquals(Optional.of(ImageFormat.PSD), ImageFormat.of(Path.of("layers.psd")));
        assertEquals(Optional.empty(), ImageFormat.of(Path.of("notes.txt")));
        assertEquals(Optional.empty(), ImageFormat.of(Path.of(".hidden")));
    }

    @Test
    void writableFormatsInDialogOrder() {
        assertEquals(List.of(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.TIFF, ImageFormat.BMP),
                ImageFormat.writableFormats());
        assertFalse(ImageFormat.PSD.writable());
        assertFalse(ImageFormat.GIF.writable());
    }

    @Test
    void withExtensionReplacesOrAdds() {
        assertEquals(Path.of("/p/a.webp"), ImageFormat.WEBP.withExtension(Path.of("/p/a.jpg")));
        assertEquals(Path.of("/p/a.b.png"), ImageFormat.PNG.withExtension(Path.of("/p/a.b.tif")));
        assertEquals(Path.of("/p/plain.tif"), ImageFormat.TIFF.withExtension(Path.of("/p/plain")));
    }

    @Test
    void matchesAllItsExtensions() {
        assertTrue(ImageFormat.TIFF.matches(Path.of("x.tif")));
        assertTrue(ImageFormat.TIFF.matches(Path.of("x.TIFF")));
        assertFalse(ImageFormat.TIFF.matches(Path.of("x.png")));
    }

    @Test
    void lossyAndAlpha() {
        assertTrue(ImageFormat.JPEG.lossy());
        assertTrue(ImageFormat.WEBP.lossy());
        assertFalse(ImageFormat.PNG.lossy());
        assertTrue(ImageFormat.PNG.alpha());
        assertFalse(ImageFormat.JPEG.alpha());
        assertFalse(ImageFormat.BMP.alpha());
    }

    @Test
    void imageFiles() {
        assertTrue(ImageFormat.isImage(Path.of("a.webp")));
        assertTrue(ImageFormat.isImage(Path.of("a.tga")));
        assertFalse(ImageFormat.isImage(Path.of("a.pdf")));
    }
}
