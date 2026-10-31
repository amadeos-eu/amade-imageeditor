package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.apps.imageeditor.domain.ShapeFill;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import eu.amadeos.apps.imageeditor.service.ImageLoader;
import eu.amadeos.apps.imageeditor.service.ImageSaver;
import eu.amadeos.apps.imageeditor.service.OpenRaster;
import eu.amadeos.apps.imageeditor.service.TestPictures;
import eu.amadeos.commons.process.CommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Painting, text and layers through the presenter, with a fake window. */
class PaintingPresenterTest {
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
        presenter.start(List.of(lake));
    }

    private List<String> names() {
        return view.layers.stream().map(Layer::name).toList();
    }

    @Test
    void theFirstStrokeGoesOnANewPaintingLayer() {
        presenter.selectTool(Tool.BRUSH);
        assertEquals(Tool.BRUSH, view.tool);
        presenter.painted(List.of(new Dot(50, 50), new Dot(150, 50)), false);
        assertEquals(List.of("Background", "Painting"), names());
        assertEquals(1, view.activeLayer);
        assertEquals(0xff000000, view.image.getRGB(100, 50));
        assertEquals("Brush Stroke", view.undo.orElseThrow());
        // The next stroke goes on the same layer.
        presenter.painted(List.of(new Dot(50, 60)), true);
        assertEquals(2, view.layers.size());
        assertEquals(0xffffffff, view.image.getRGB(50, 60), "right button paints the second colour");
        presenter.undo();
        presenter.undo();
        assertEquals(List.of("Background"), names());
        assertEquals(0xffff0000, view.image.getRGB(100, 50));
    }

    @Test
    void withoutTheOptionStrokesGoOnThePhoto() {
        presenter.setPaintOnNewLayer(false);
        presenter.selectTool(Tool.BRUSH);
        presenter.painted(List.of(new Dot(50, 50)), false);
        assertEquals(List.of("Background"), names());
        assertEquals(0xff000000, view.image.getRGB(50, 50));
        assertFalse(fixture.preferences.paintOnNewLayer());
    }

    @Test
    void strokesThatComeWhileBusyWaitTheirTurn() throws Exception {
        Deque<Runnable> later = new ArrayDeque<>();
        FakeView slowView = new FakeView();
        MainPresenter slow = new MainPresenter(slowView, Fixture.MSG, null, new MainPresenter.Services(
                fixture.preferences, new ImageLoader(), new ImageSaver(CommandRunner.system()), later::add,
                Runnable::run, dir, () -> {
        }));
        slow.start(List.of(lake));
        while (!later.isEmpty()) later.poll().run();
        slow.selectTool(Tool.BRUSH);
        slow.painted(List.of(new Dot(50, 50)), false);
        slow.painted(List.of(new Dot(250, 50)), false);
        assertEquals(1, later.size(), "the second stroke waits");
        slow.selectTool(Tool.SHAPES);
        assertEquals(Tool.SHAPES, slow.tool(), "painting tools change while a stroke is applied");
        slow.selectTool(Tool.CROP);
        assertEquals(Tool.SHAPES, slow.tool(), "Crop waits for the picture");
        assertEquals(Tool.SHAPES, slowView.tool);
        slow.selectTool(Tool.TEXT);
        slow.clicked(new Dot(200, 150), false);
        slow.setText("Top");
        slow.finishText();
        while (!later.isEmpty()) later.poll().run();
        assertEquals(3, slow.history().edits().size());
        assertEquals(List.of("Background", "Painting", "Top"), slowView.layers.stream().map(Layer::name).toList(),
                "both strokes on one new layer, the text above it");
        assertEquals(0xff000000, slowView.image.getRGB(250, 50));
    }

    @Test
    void aShapeKeepsItsHandlesUntilEnter() {
        presenter.selectTool(Tool.SHAPES);
        presenter.setShape(ShapeKind.RECTANGLE);
        presenter.setShapeFill(ShapeFill.SECONDARY);
        presenter.shapeChanged(List.of(new Dot(20, 20), new Dot(120, 120)), false);
        assertNotNull(view.pendingShape);
        assertFalse(presenter.history().canUndo());
        presenter.shapeChanged(List.of(new Dot(20, 20), new Dot(180, 120)), false);
        presenter.enter();
        assertNull(presenter.pendingShape());
        assertEquals("Rectangle", view.undo.orElseThrow());
        assertEquals(0xffffffff, view.image.getRGB(100, 70), "filled with the second colour");
        assertEquals(Tool.SHAPES, presenter.tool(), "the tool stays open for the next shape");
    }

    @Test
    void escapeDropsTheShape() {
        presenter.selectTool(Tool.SHAPES);
        presenter.shapeChanged(List.of(new Dot(20, 20), new Dot(120, 120)), false);
        presenter.escape();
        assertNull(presenter.pendingShape());
        assertFalse(presenter.history().canUndo());
        assertEquals(Tool.SHAPES, presenter.tool());
    }

    @Test
    void fillAndPick() {
        presenter.selectTool(Tool.FILL);
        presenter.setColour(Color.YELLOW, false);
        presenter.clicked(new Dot(10, 10), false);
        assertEquals(0xffffff00, view.image.getRGB(150, 100));
        assertEquals(0xff00ff00, view.image.getRGB(250, 100));
        presenter.selectTool(Tool.PICK);
        presenter.clicked(new Dot(250, 10), true);
        assertEquals(Color.GREEN, presenter.paint().secondary());
        assertEquals(Color.GREEN, view.paint.secondary());
        presenter.swapColours();
        assertEquals(Color.GREEN, presenter.paint().primary());
        presenter.defaultColours();
        assertEquals(Color.BLACK, presenter.paint().primary());
    }

    @Test
    void paintSettingsAreRemembered() {
        presenter.selectTool(Tool.BRUSH);
        presenter.growBrush(1);
        presenter.setColour(new Color(0x336699), false);
        int size = presenter.paint().brush().size();
        assertEquals(size, fixture.preferences.paintSettings(presenter.paint()).brush().size());
        FakeView again = new FakeView();
        MainPresenter next = fixture.presenter(again);
        next.start(List.of(lake));
        assertEquals(new Color(0x336699), next.paint().primary());
        assertEquals(size, next.paint().brush().size());
    }

    @Test
    void textIsTypedPlacedAndEditedAgain() {
        presenter.selectTool(Tool.TEXT);
        presenter.clicked(new Dot(200, 150), false);
        assertNotNull(view.text);
        presenter.setText("Lake");
        assertNotNull(view.overlay, "the text shows while typed");
        presenter.textDragged(10, 0);
        presenter.finishText();
        assertEquals(List.of("Background", "Lake"), names());
        assertEquals(210, view.layers.get(1).text().orElseThrow().centreX(), 1e-9);
        // A click on the text opens it again.
        presenter.clicked(new Dot(210, 150), false);
        assertEquals("Lake", presenter.draft().text());
        presenter.setText("Lake Constance");
        presenter.enter();
        assertEquals(List.of("Background", "Lake Constance"), names());
        assertEquals(2, presenter.history().edits().size());
    }

    @Test
    void paintingOnTextGoesOnANewLayer() {
        presenter.selectTool(Tool.TEXT);
        presenter.clicked(new Dot(200, 150), false);
        presenter.setText("Lake");
        presenter.finishText();
        presenter.selectTool(Tool.BRUSH);
        presenter.painted(List.of(new Dot(50, 50)), false);
        assertEquals(List.of("Background", "Lake", "Painting"), names());
        assertTrue(view.layers.get(1).isText(), "the text stays editable");
    }

    @Test
    void emptyTextMakesNoStep() {
        presenter.selectTool(Tool.TEXT);
        presenter.clicked(new Dot(200, 150), false);
        presenter.finishText();
        assertFalse(presenter.history().canUndo());
    }

    @Test
    void layerCommands() {
        presenter.newLayer();
        assertEquals(List.of("Background", "Layer 2"), names());
        presenter.duplicateLayer();
        assertEquals(List.of("Background", "Layer 2", "Layer 2 copy"), names());
        assertEquals(2, view.activeLayer);
        presenter.layerDown();
        assertEquals(List.of("Background", "Layer 2 copy", "Layer 2"), names());
        assertEquals(1, view.activeLayer);
        view.nameAnswer = Optional.of("Sky");
        presenter.renameLayer();
        assertEquals("Sky", names().get(1));
        presenter.setLayerOpacity(1, 50);
        presenter.setLayerVisible(1, false);
        assertEquals(50, view.layers.get(1).opacity());
        assertFalse(view.layers.get(1).visible());
        presenter.deleteLayer();
        assertEquals(List.of("Background", "Layer 2"), names());
        assertEquals(0, view.activeLayer);
        presenter.flatten();
        assertEquals(List.of("Background"), names());
        presenter.deleteLayer();
        assertEquals(1, view.layers.size(), "the last layer stays");
    }

    @Test
    void moveToolMovesTheActiveLayer() {
        presenter.newLayer();
        presenter.selectTool(Tool.BRUSH);
        presenter.painted(List.of(new Dot(50, 50)), false);
        presenter.selectTool(Tool.MOVE);
        presenter.layerDragged(20, 0, false);
        assertNotNull(view.overlay);
        presenter.layerDragged(100, 10, true);
        assertNull(view.overlay);
        assertEquals(0xff000000, view.image.getRGB(150, 60));
        presenter.nudge(1, 0);
        assertEquals(101, view.layers.get(1).x());
    }

    @Test
    void pasteAsLayer() {
        presenter.pasteAsLayer();
        assertEquals(1, view.infos.size());
        view.pasteAnswer = Optional.of(TestPictures.filled(40, 20, 0x123456));
        presenter.pasteAsLayer();
        assertEquals(List.of("Background", "Pasted picture"), names());
        assertEquals(0xff123456, view.image.getRGB(200, 150));
    }

    @Test
    void savingLayersToPngAsksAndCanKeepAnOpenRasterCopy() throws Exception {
        presenter.selectTool(Tool.BRUSH);
        presenter.painted(List.of(new Dot(50, 50)), false);
        view.layersAnswer = MainView.Layers.MERGED_AND_LAYERS;
        presenter.save();
        assertEquals(1, view.layersAsked);
        assertFalse(presenter.history().modified());
        assertEquals(lake, presenter.file(), "the picture stays the PNG");
        assertEquals(0xff000000, ImageIO.read(lake.toFile()).getRGB(50, 50));
        Path ora = dir.resolve("lake.ora");
        assertTrue(Files.exists(ora));
        assertEquals(2, OpenRaster.read(ora).size());
        // The answer counts for this file.
        presenter.painted(List.of(new Dot(60, 60)), false);
        presenter.save();
        assertEquals(1, view.layersAsked);
    }

    @Test
    void saveAsOffersOpenRasterForLayers() throws Exception {
        presenter.newLayer();
        Path target = dir.resolve("lake.ora");
        view.saveAnswer = Optional.of(new SavePanel.Request(target, SaveOptions.defaults(ImageFormat.ORA)));
        presenter.saveAs();
        assertTrue(view.saveLayered);
        assertEquals(target, view.saveSuggested);
        assertEquals(target, presenter.file());
        // Opened again, the layers are there.
        FakeView again = new FakeView();
        fixture.presenter(again).start(List.of(target));
        assertEquals(2, again.layers.size());
        assertTrue(again.title.startsWith("lake.ora"), again.title);
    }

    @Test
    void layersPanelAndToolsNeedAPicture() {
        presenter.toggleLayersPanel();
        boolean first = view.layersPanel;
        presenter.toggleLayersPanel();
        assertEquals(!first, view.layersPanel);
        FakeView empty = new FakeView();
        MainPresenter none = fixture.presenter(empty);
        none.start(List.of());
        none.newLayer();
        none.painted(List.of(new Dot(1, 1)), false);
        assertTrue(empty.layers.isEmpty());
    }

    @Test
    void anEditAppliesToTheActiveLayer() {
        presenter.newLayer();
        presenter.selectLayer(0);
        presenter.autoLevels();
        Edit last = presenter.history().edits().getLast();
        assertEquals(new Edit.AutoLevels(0), last);
    }
}
