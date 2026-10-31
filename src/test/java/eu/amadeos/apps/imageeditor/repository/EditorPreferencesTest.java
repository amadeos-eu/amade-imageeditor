package eu.amadeos.apps.imageeditor.repository;

import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPreferencesTest {
    @TempDir
    Path dir;

    @Test
    void defaultsWithoutAFile() {
        EditorPreferences p = new EditorPreferences(dir.resolve("none.properties"));
        assertEquals(SaveOptions.defaults(ImageFormat.JPEG), p.saveOptions(ImageFormat.JPEG));
        assertEquals(ResizeQuality.BEST, p.resizeQuality());
        assertTrue(p.straightenCrops());
        assertEquals(Optional.empty(), p.lastFolder());
    }

    @Test
    void choicesLastAndQualityIsPerFormat() {
        Path file = dir.resolve("imageeditor.properties");
        EditorPreferences p = new EditorPreferences(file);
        p.setSaveOptions(new SaveOptions(ImageFormat.WEBP, 60, true, false, false, true));
        p.setSaveOptions(new SaveOptions(ImageFormat.JPEG, 75, false, false, false, true));
        p.setResizeQuality(ResizeQuality.PIXELS);
        p.setStraightenCrops(false);
        p.setLastFolder(dir);

        EditorPreferences again = new EditorPreferences(file);
        SaveOptions webp = again.saveOptions(ImageFormat.WEBP);
        assertEquals(60, webp.quality());
        assertTrue(webp.lossless());
        assertEquals(75, again.saveOptions(ImageFormat.JPEG).quality());
        assertFalse(again.saveOptions(ImageFormat.PNG).keepCamera(), "metadata choices are shared");
        assertEquals(ResizeQuality.PIXELS, again.resizeQuality());
        assertFalse(again.straightenCrops());
        assertEquals(Optional.of(dir.toAbsolutePath()), again.lastFolder());
    }

    @Test
    void brokenValuesFallBack() throws Exception {
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "save.jpg.quality=lots\nresize.quality=HUGE\n");
        EditorPreferences p = new EditorPreferences(file);
        assertEquals(90, p.saveOptions(ImageFormat.JPEG).quality());
        assertEquals(ResizeQuality.BEST, p.resizeQuality());
    }
}
