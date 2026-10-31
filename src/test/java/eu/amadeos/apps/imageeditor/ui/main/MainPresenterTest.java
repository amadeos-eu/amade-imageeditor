package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;

    private Fixture fixture;
    private FakeView view;
    private MainPresenter presenter;
    private Path lake;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(dir);
        view = new FakeView();
        presenter = fixture.presenter(view);
        lake = fixture.picture();
    }

    private void open() {
        presenter.start(List.of(lake));
    }

    @Test
    void startsEmptyWithAHint() {
        presenter.start(List.of());
        assertEquals(Fixture.MSG.get("empty.hint"), view.noImage);
        assertFalse(view.picture);
        assertEquals("Image Editor", view.title);
    }

    @Test
    void opensThePictureFromTheCommandLine() {
        open();
        assertEquals(400, view.image.getWidth());
        assertEquals("lake.png - Image Editor", view.title);
        assertTrue(view.picture);
        assertFalse(view.busy);
        assertTrue(view.status.startsWith("400 × 300   PNG"), view.status);
        assertEquals(Optional.empty(), view.undo);
    }

    @Test
    void aFileThatIsNoPictureShowsAnError() throws Exception {
        Path text = dir.resolve("notes.png");
        Files.writeString(text, "hello");
        presenter.start(List.of(text));
        assertEquals(1, view.errors.size());
        assertTrue(view.errors.getFirst().contains("notes.png"), view.errors.getFirst());
        assertNotNull(view.noImage);
    }

    @Test
    void aFolderIsRefused() {
        presenter.start(List.of(dir));
        assertEquals(1, view.errors.size());
    }

    @Test
    void rotateUndoRedoAndTheTitleStar() {
        open();
        presenter.rotateRight();
        assertEquals(300, view.image.getWidth());
        assertEquals("lake.png * - Image Editor", view.title);
        assertEquals(Optional.of("Rotate Right"), view.undo);
        presenter.undo();
        assertEquals(400, view.image.getWidth());
        assertEquals(Optional.of("Rotate Right"), view.redo);
        assertEquals("lake.png - Image Editor", view.title);
        presenter.redo();
        assertEquals(300, view.image.getWidth());
    }

    @Test
    void flips() {
        open();
        presenter.flipHorizontally();
        assertEquals(0x00ff00, view.image.getRGB(0, 0) & 0xffffff);
        presenter.flipVertically();
        assertEquals(0xffffff, view.image.getRGB(0, 0) & 0xffffff);
    }

    @Test
    void cropToolWithARatio() {
        open();
        presenter.selectTool(Tool.CROP);
        assertEquals(Tool.CROP, view.tool);
        assertEquals(new Rectangle(0, 0, 400, 300), view.crop);
        presenter.setRatio(AspectRatio.SQUARE);
        assertEquals(new Rectangle(50, 0, 300, 300), view.crop);
        assertTrue(view.status.contains("Crop 300 × 300 at 50, 0"), view.status);
        presenter.typeCrop(CropBox.Field.WIDTH, 100);
        assertEquals(new Rectangle(50, 0, 100, 100), view.crop);
        presenter.applyTool();
        assertEquals(Tool.NONE, view.tool);
        assertEquals(100, view.image.getWidth());
        assertEquals(100, view.image.getHeight());
        assertEquals(Optional.of("Crop"), view.undo);
    }

    @Test
    void cropOfTheWholePictureIsNoStep() {
        open();
        presenter.selectTool(Tool.CROP);
        presenter.applyTool();
        assertEquals(Optional.empty(), view.undo);
    }

    @Test
    void portraitAndOriginal() {
        open();
        presenter.selectTool(Tool.CROP);
        presenter.setRatio(AspectRatio.of(3, 2));
        assertEquals(1.5, view.ratioValue, 1e-9);
        presenter.setPortrait(true);
        assertEquals(2 / 3.0, view.ratioValue, 1e-9);
        assertEquals(2 / 3.0, (double) view.crop.width / view.crop.height, 0.01);
        presenter.setRatio(AspectRatio.ORIGINAL);
        assertFalse(view.portrait, "Original follows the landscape picture");
        assertEquals(4 / 3.0, view.ratioValue, 1e-9);
    }

    @Test
    void customRatio() {
        open();
        presenter.selectTool(Tool.CROP);
        view.ratioAnswer = Optional.of(AspectRatio.of(5, 7));
        presenter.customRatio();
        assertEquals("7:5", view.ratio.key());
        assertTrue(view.portrait);
        assertEquals(5 / 7.0, view.ratioValue, 1e-9);
    }

    @Test
    void canvasDragReachesThePresenter() {
        open();
        presenter.selectTool(Tool.CROP);
        presenter.cropChanged(new Rectangle(10, 20, 30, 40));
        assertEquals(new Rectangle(10, 20, 30, 40), view.crop);
        assertEquals(new Rectangle(10, 20, 30, 40), presenter.cropBox());
    }

    @Test
    void theSameToolAgainClosesIt() {
        open();
        presenter.selectTool(Tool.STRAIGHTEN);
        presenter.selectTool(Tool.STRAIGHTEN);
        assertEquals(Tool.NONE, view.tool);
    }

    @Test
    void escapeCancelsAndEnterApplies() {
        open();
        presenter.selectTool(Tool.STRAIGHTEN);
        presenter.setAngle(4);
        presenter.escape();
        assertEquals(Tool.NONE, view.tool);
        assertEquals(400, view.image.getWidth());
        presenter.selectTool(Tool.STRAIGHTEN);
        assertEquals(0, view.degrees, "a new start");
        presenter.setAngle(4);
        presenter.enter();
        assertTrue(view.image.getWidth() < 400);
        assertEquals(Optional.of("Straighten"), view.undo);
    }

    @Test
    void straightenAngleIsRoundedAndLimited() {
        open();
        presenter.selectTool(Tool.STRAIGHTEN);
        presenter.setAngle(2.345);
        assertEquals(2.3, view.degrees, 1e-9);
        presenter.levelLine(-80);
        assertEquals(-45, view.degrees, 1e-9);
        assertTrue(view.status.contains("Straighten -45.0°"), view.status);
    }

    @Test
    void straightenWithoutCropKeepsTheSizeAndIsRemembered() {
        open();
        presenter.selectTool(Tool.STRAIGHTEN);
        presenter.setStraightenCrops(false);
        presenter.setAngle(3);
        presenter.applyTool();
        assertEquals(400, view.image.getWidth());
        assertFalse(fixture.preferences.straightenCrops());
    }

    @Test
    void adjustShowsALookAndAppliesOneStep() {
        open();
        presenter.selectTool(Tool.ADJUST);
        assertNull(view.look);
        presenter.setAdjustments(Adjustments.NONE.withBrightness(-50));
        assertNotNull(view.look);
        BufferedImage preview = view.look.apply(view.image);
        assertTrue((preview.getRGB(399, 299) & 0xff) < 0xff);
        presenter.setCompare(true);
        assertTrue(view.compare);
        presenter.applyTool();
        assertEquals(Optional.of("Adjust Colours"), view.undo);
        assertTrue((view.image.getRGB(399, 299) & 0xff) < 0xff);
    }

    @Test
    void resetInAdjustKeepsThePanelOpen() {
        open();
        presenter.selectTool(Tool.ADJUST);
        presenter.setAdjustments(Adjustments.NONE.withContrast(30));
        presenter.cancelTool();
        assertEquals(Tool.ADJUST, view.tool);
        assertEquals(Adjustments.NONE, view.adjustments);
        presenter.cancelTool();
        assertEquals(Tool.NONE, view.tool);
    }

    @Test
    void spaceTogglesTheComparisonOnlyInAdjust() {
        open();
        presenter.toggleCompare();
        assertFalse(view.compare);
        presenter.selectTool(Tool.ADJUST);
        presenter.toggleCompare();
        assertTrue(view.compare);
        presenter.toggleCompare();
        assertFalse(view.compare);
    }

    @Test
    void autoLevelsIsAStep() {
        open();
        presenter.autoLevels();
        assertEquals(Optional.of("Auto Levels"), view.undo);
    }

    @Test
    void resizeAsksAndRemembersTheQuality() {
        open();
        view.resizeAnswer = Optional.of(new Edit.Resize(200, 150, ResizeQuality.FAST));
        presenter.resize();
        assertEquals(200, view.image.getWidth());
        assertEquals(ResizeQuality.FAST, fixture.preferences.resizeQuality());
    }

    @Test
    void resizeCancelledChangesNothing() {
        open();
        presenter.resize();
        assertEquals(400, view.image.getWidth());
        assertEquals(Optional.empty(), view.undo);
    }

    @Test
    void anOpenToolIsAppliedBeforeResizing() {
        open();
        presenter.selectTool(Tool.CROP);
        presenter.setRatio(AspectRatio.SQUARE);
        view.resizeAnswer = Optional.of(new Edit.Resize(30, 30, ResizeQuality.BEST));
        presenter.resize();
        assertEquals(30, view.image.getWidth());
        assertEquals(2, presenter.history().edits().size());
    }

    @Test
    void saveWritesTheFileAgain() throws Exception {
        open();
        presenter.rotateLeft();
        presenter.save();
        assertEquals(List.of(), view.errors);
        assertEquals(300, ImageIO.read(lake.toFile()).getWidth());
        assertEquals("lake.png - Image Editor", view.title);
    }

    @Test
    void saveAsWritesANewFileAndRemembersTheOptions() throws Exception {
        open();
        presenter.rotateLeft();
        Path target = dir.resolve("lake.jpg");
        SaveOptions options = new SaveOptions(ImageFormat.JPEG, 70, false, true, true, true);
        view.saveAnswer = Optional.of(new SavePanel.Request(target, options));
        presenter.saveAs();
        assertEquals(lake, view.saveSuggested);
        assertFalse(view.saveFromJpeg);
        assertEquals(300, ImageIO.read(target.toFile()).getWidth());
        assertEquals(400, ImageIO.read(lake.toFile()).getWidth(), "the original stays");
        assertEquals(70, fixture.preferences.saveOptions(ImageFormat.JPEG).quality());
        assertEquals(target, presenter.file());
        assertEquals("lake.jpg - Image Editor", view.title);
    }

    @Test
    void saveAsDoesNotReplaceWithoutConsent() throws Exception {
        open();
        Path other = dir.resolve("other.png");
        Files.writeString(other, "keep me");
        view.replaceAnswer = false;
        view.saveAnswer = Optional.of(new SavePanel.Request(other, SaveOptions.defaults(ImageFormat.PNG)));
        presenter.saveAs();
        assertEquals("keep me", Files.readString(other));
    }

    @Test
    void readOnlyFormatsGoToSaveAs() throws Exception {
        Path tga = dir.resolve("art.tga");
        if (!ImageIO.write(eu.amadeos.apps.imageeditor.service.TestPictures.quarters(20, 10), "tga", tga.toFile())) return;
        presenter.start(List.of(tga));
        presenter.rotateLeft();
        presenter.save();
        assertEquals(dir.resolve("art.png"), view.saveSuggested);
    }

    @Test
    void savingAppliesTheOpenTool() throws Exception {
        open();
        presenter.selectTool(Tool.CROP);
        presenter.setRatio(AspectRatio.SQUARE);
        presenter.save();
        assertEquals(300, ImageIO.read(lake.toFile()).getWidth());
        assertEquals(Tool.NONE, view.tool);
    }

    @Test
    void unsavedChangesAreAskedAboutOnExit() {
        open();
        presenter.rotateLeft();
        view.unsavedAnswer = MainView.Unsaved.CANCEL;
        presenter.exit();
        assertFalse(view.closed);
        view.unsavedAnswer = MainView.Unsaved.DISCARD;
        presenter.exit();
        assertTrue(view.closed);
        assertTrue(fixture.exited.get());
    }

    @Test
    void saveThenExit() throws Exception {
        open();
        presenter.rotateLeft();
        view.unsavedAnswer = MainView.Unsaved.SAVE;
        presenter.exit();
        assertTrue(view.closed);
        assertEquals(300, ImageIO.read(lake.toFile()).getWidth());
    }

    @Test
    void noQuestionWithoutChanges() {
        open();
        presenter.exit();
        assertEquals(0, view.unsavedAsked);
        assertTrue(view.closed);
    }

    @Test
    void openAnotherPictureAsksFirst() throws Exception {
        open();
        presenter.rotateLeft();
        Path second = dir.resolve("second.png");
        javax.imageio.ImageIO.write(eu.amadeos.apps.imageeditor.service.TestPictures.quarters(50, 50), "png",
                second.toFile());
        view.openAnswer = Optional.of(second);
        view.unsavedAnswer = MainView.Unsaved.CANCEL;
        presenter.open();
        assertEquals(300, view.image.getWidth());
        view.unsavedAnswer = MainView.Unsaved.DISCARD;
        presenter.open(List.of(second));
        assertEquals(50, view.image.getWidth());
        assertEquals(Optional.empty(), view.undo);
    }

    @Test
    void copyZoomAndAbout() {
        open();
        presenter.copyImage();
        assertEquals(400, view.copied.getWidth());
        presenter.zoom(ZoomCommand.ACTUAL);
        assertEquals(List.of(ZoomCommand.ACTUAL), view.zooms);
        presenter.scaleChanged(0.5);
        assertTrue(view.status.endsWith("Zoom 50 %"), view.status);
        presenter.about();
        assertTrue(view.aboutShown);
    }

    @Test
    void nothingHappensWithoutAPicture() {
        presenter.start(List.of());
        presenter.rotateLeft();
        presenter.selectTool(Tool.CROP);
        presenter.save();
        presenter.undo();
        presenter.copyImage();
        assertEquals(Tool.NONE, view.tool);
        assertNull(view.copied);
        assertEquals(List.of(), view.errors);
    }

    @Test
    void cwebpMissingIsExplained() throws Exception {
        MainPresenter noCwebp = fixture.presenter(view, command -> {
            throw new eu.amadeos.commons.process.ProgramMissingException(command.getFirst(),
                    new java.io.IOException("missing"));
        });
        noCwebp.start(List.of(lake));
        view.saveAnswer = Optional.of(new SavePanel.Request(dir.resolve("x.webp"), SaveOptions.defaults(ImageFormat.WEBP)));
        noCwebp.saveAs();
        assertEquals(List.of(Fixture.MSG.get("error.cwebp")), view.errors);
    }
}
