package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.OnEdt;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resize and save dialogs' panels, and the custom ratio parser. */
class PanelsTest {
    @Test
    void resizeKeepsTheRatio() throws Exception {
        OnEdt.run(() -> {
            ResizePanel panel = new ResizePanel(Fixture.MSG, 4000, 3000, ResizeQuality.BEST);
            assertEquals(Optional.empty(), panel.result());
            panel.widthField().setValue(2000);
            assertEquals(1500, ((Number) panel.heightField().getValue()).intValue());
            assertEquals(Optional.of(new Edit.Resize(2000, 1500, ResizeQuality.BEST)), panel.result());
            panel.keepRatioBox().setSelected(false);
            panel.heightField().setValue(1000);
            assertEquals(Optional.of(new Edit.Resize(2000, 1000, ResizeQuality.BEST)), panel.result());
        });
    }

    @Test
    void resizePresetsAndPercent() throws Exception {
        OnEdt.run(() -> {
            ResizePanel panel = new ResizePanel(Fixture.MSG, 3000, 4000, ResizeQuality.FAST);
            panel.preset(1920);
            assertEquals(Optional.of(new Edit.Resize(1440, 1920, ResizeQuality.FAST)), panel.result());
            panel.preset(-50);
            assertEquals(Optional.of(new Edit.Resize(1500, 2000, ResizeQuality.FAST)), panel.result());
            panel.percentButton().doClick(0);
            assertEquals(50, ((Number) panel.widthField().getValue()).intValue());
            panel.widthField().setValue(25);
            assertEquals(Optional.of(new Edit.Resize(750, 1000, ResizeQuality.FAST)), panel.result());
        });
    }

    @Test
    void saveSuggestsTheFormatOfTheName(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path original = dir.resolve("lake.jpg");
        OnEdt.run(() -> {
            SavePanel panel = new SavePanel(Fixture.MSG, dir.resolve("lake-edited.jpg"), original, true,
                    SaveOptions::defaults);
            SavePanel.Request request = panel.result().orElseThrow();
            assertEquals(dir.resolve("lake-edited.jpg"), request.target());
            assertEquals(ImageFormat.JPEG, request.options().format());
            assertEquals(SaveOptions.DEFAULT_JPEG_QUALITY, request.options().quality());
            assertTrue(request.options().keepLocation(), "location is kept by default");
            assertTrue(panel.keepLocationBox().isEnabled());

            panel.formatBox().setSelectedItem(ImageFormat.PNG);
            assertEquals("lake-edited.png", panel.nameField().getText());
            assertFalse(panel.keepCameraBox().isEnabled(), "PNG takes no camera data");
            assertFalse(panel.qualitySlider().isShowing());

            panel.nameField().setText("lake.jpg");
            assertEquals(ImageFormat.JPEG, panel.result().orElseThrow().options().format(), "a typed extension wins");
            assertTrue(panel.originalNoteLabel().getText().contains("lake.jpg"));

            panel.nameField().setText(" ");
            assertEquals(Optional.empty(), panel.result());
        });
    }

    @Test
    void unchecksLocation(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        OnEdt.run(() -> {
            SavePanel panel = new SavePanel(Fixture.MSG, dir.resolve("a.jpg"), dir.resolve("a.jpg"), true,
                    SaveOptions::defaults);
            panel.keepLocationBox().doClick(0);
            assertFalse(panel.result().orElseThrow().options().keepLocation());
            panel.keepCameraBox().doClick(0);
            assertFalse(panel.keepLocationBox().isEnabled(), "no location without the camera data");
        });
    }

    @Test
    void parsesCustomRatios() {
        assertEquals(Optional.of(AspectRatio.of(7, 5)), MainWindow.parseRatio("7:5"));
        assertEquals(Optional.of(AspectRatio.of(21, 9)), MainWindow.parseRatio(" 21 x 9 "));
        assertEquals(Optional.of(AspectRatio.of(2, 1)), MainWindow.parseRatio("2/1"));
        assertEquals(Optional.empty(), MainWindow.parseRatio("0:5"));
        assertEquals(Optional.empty(), MainWindow.parseRatio("wide"));
    }

    @Test
    void levellingFollowsTheDrawnLine() {
        assertEquals(0, EditorCanvas.levelling(0, 0, 0, 100, 0), 1e-9);
        assertEquals(-10, EditorCanvas.levelling(0, 0, 0, 100, Math.tan(Math.toRadians(10)) * 100), 1e-9);
        // The same line drawn backwards, and a nearly vertical one.
        assertEquals(-10, EditorCanvas.levelling(0, 100, Math.tan(Math.toRadians(10)) * 100, 0, 0), 1e-9);
        // Its top leans right, like a picture turned clockwise: turn it back the same way as above.
        assertEquals(-5, EditorCanvas.levelling(0, 0, 0, Math.tan(Math.toRadians(5)) * 100, -100), 1e-9);
        assertEquals(45, EditorCanvas.levelling(40, 0, 0, 100, -20), 1e-9);
    }
}
