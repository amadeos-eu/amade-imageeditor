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
import java.awt.event.KeyEvent;
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
}
