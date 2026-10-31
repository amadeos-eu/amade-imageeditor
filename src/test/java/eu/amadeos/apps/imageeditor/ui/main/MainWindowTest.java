package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.OnEdt;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with a test picture: toolbar, menus, tool panel, keys. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private MainWindow window;
    private MainPresenter presenter;

    private void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Fixture fixture = new Fixture(dir);
        Path picture = fixture.picture();
        OnEdt.run(() -> {
            window = new MainWindow(Fixture.MSG, null);
            presenter = fixture.presenter(window);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(picture));
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    private void key(int code) throws Exception {
        OnEdt.run(() -> {
            var root = window.getRootPane();
            Object name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0));
            root.getActionMap().get(name).actionPerformed(new ActionEvent(root, 0, ""));
        });
    }

    @Test
    void showsThePictureAndItsSize() throws Exception {
        open();
        OnEdt.run(() -> {
            assertTrue(window.hasPicture());
            assertEquals("lake.png - Image Editor", window.getTitle());
            assertTrue(window.statusText().startsWith("400 × 300"), window.statusText());
            assertTrue(window.toolAction("image.crop").isEnabled());
            assertFalse(window.toolAction("edit.undo").isEnabled(), "nothing to undo yet");
        });
    }

    @Test
    void cropWithARatioAndEnter() throws Exception {
        open();
        OnEdt.run(() -> {
            window.toolButton(Tool.CROP).doClick(0);
            assertEquals(Tool.CROP, window.toolPanel().tool());
            assertTrue(window.menu().item("menu.image.crop").isSelected());
            window.toolPanel().ratioButton(AspectRatio.SQUARE).doClick(0);
            assertEquals(window.canvas().crop().width, window.canvas().crop().height);
        });
        key(KeyEvent.VK_ENTER);
        OnEdt.run(() -> {
            assertEquals(Tool.NONE, presenter.tool());
            assertTrue(window.statusText().startsWith("300 × 300"), window.statusText());
            assertTrue(window.toolAction("edit.undo").isEnabled());
            assertTrue(window.menu().item("menu.edit.undo").getText().contains("Crop"),
                    window.menu().item("menu.edit.undo").getText());
            window.toolAction("edit.undo").actionPerformed(null);
            assertTrue(window.statusText().startsWith("400 × 300"), window.statusText());
        });
    }

    @Test
    void escapeLeavesTheToolUnchanged() throws Exception {
        open();
        OnEdt.run(() -> {
            window.menu().item("menu.image.straighten").doClick(0);
            window.toolPanel().angleSlider().setValue(window.toolPanel().angleSlider().getValue() + 50);
            assertTrue(presenter.degrees() != 0);
        });
        key(KeyEvent.VK_ESCAPE);
        OnEdt.run(() -> {
            assertEquals(Tool.NONE, presenter.tool());
            assertFalse(presenter.history().canUndo());
        });
    }

    @Test
    void rotatesFromTheToolbar() throws Exception {
        open();
        OnEdt.run(() -> {
            window.toolAction("image.rotate-right").actionPerformed(null);
            assertTrue(window.statusText().startsWith("300 × 400"), window.statusText());
        });
    }

    private void canvasKey(int code) throws Exception {
        OnEdt.run(() -> {
            var canvas = window.canvas();
            Object name = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(code, 0));
            canvas.getActionMap().get(name).actionPerformed(new ActionEvent(canvas, 0, ""));
        });
    }

    private void drag(int x1, int y1, int x2, int y2) throws Exception {
        OnEdt.run(() -> {
            var c = window.canvas();
            long t = System.currentTimeMillis();
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK, x1, y1, 1,
                    false, MouseEvent.BUTTON1));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + 10, InputEvent.BUTTON1_DOWN_MASK,
                    (x1 + x2) / 2, (y1 + y2) / 2, 0, false, MouseEvent.NOBUTTON));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, t + 20, InputEvent.BUTTON1_DOWN_MASK, x2, y2,
                    0, false, MouseEvent.NOBUTTON));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 30, 0, x2, y2, 1, false,
                    MouseEvent.BUTTON1));
        });
    }

    @Test
    void paintsWithTheBrushOnANewLayer() throws Exception {
        open();
        canvasKey(KeyEvent.VK_B);
        OnEdt.run(() -> {
            assertEquals(Tool.BRUSH, presenter.tool());
            assertTrue(window.toolButton(Tool.BRUSH).isSelected());
            assertTrue(window.menu().item("menu.tools.brush").isSelected());
            assertEquals(1, window.layersPanel().layerList().getModel().getSize());
        });
        int cx = window.canvas().getWidth() / 2, cy = window.canvas().getHeight() / 2;
        drag(cx - 40, cy, cx + 40, cy);
        OnEdt.run(() -> {
            assertEquals(2, window.layersPanel().layerList().getModel().getSize(), "a Painting layer");
            assertTrue(window.menu().item("menu.edit.undo").getText().contains("Brush"),
                    window.menu().item("menu.edit.undo").getText());
            assertTrue(window.menu().item("menu.layer.flatten").isEnabled());
            window.menu().item("menu.layer.flatten").doClick(0);
            assertEquals(1, window.layersPanel().layerList().getModel().getSize());
        });
        canvasKey(KeyEvent.VK_CLOSE_BRACKET);
        OnEdt.run(() -> assertTrue(presenter.paint().brush().size() > 24));
    }

    @Test
    void textToolShowsItsPanel() throws Exception {
        open();
        OnEdt.run(() -> {
            window.toolButton(Tool.TEXT).doClick(0);
            assertEquals(Tool.TEXT, window.toolPanel().tool());
            presenter.clicked(new eu.amadeos.apps.imageeditor.domain.Dot(200, 150), false);
            window.toolPanel().paint().textArea().setText("Hello");
            presenter.finishText();
            assertEquals(2, window.layersPanel().layerList().getModel().getSize());
        });
    }
}
