package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {
    /** A fake step: each edit makes the picture one pixel narrower, and counts how often it ran. */
    private final List<Edit> applied = new ArrayList<>();
    private final BiFunction<Picture, Edit, Picture> narrower = (picture, edit) -> {
        applied.add(edit);
        return Picture.of(new BufferedImage(picture.width() - 1, picture.height(), BufferedImage.TYPE_INT_RGB),
                "Background");
    };
    private final Picture original = Picture.of(new BufferedImage(10, 5, BufferedImage.TYPE_INT_RGB), "Background");

    private static Edit crop() {
        return new Edit.Crop(new Rectangle(0, 0, 2, 2));
    }

    @Test
    void startsUnmodifiedWithTheOriginal() {
        History h = new History(original, narrower);
        assertSame(original, h.current());
        assertFalse(h.modified());
        assertFalse(h.canUndo());
        assertFalse(h.canRedo());
        assertEquals(Optional.of(Orientation.NORMAL), h.onlyTurns());
    }

    @Test
    void pushUndoRedo() {
        History h = new History(original, narrower);
        h.push(crop());
        h.push(new Edit.AutoLevels());
        assertEquals(8, h.current().width());
        assertTrue(h.modified());
        assertEquals("edit.auto-levels", h.undoable().orElseThrow().key());
        assertEquals(9, h.undo().width());
        assertEquals("edit.auto-levels", h.redoable().orElseThrow().key());
        assertEquals(10, h.undo().width());
        assertSame(original, h.current());
        assertFalse(h.modified());
        assertEquals(9, h.redo().width());
        assertEquals(8, h.redo().width());
        assertEquals(2, applied.size(), "remembered pictures are used again");
    }

    @Test
    void aNewStepDropsTheRedoSteps() {
        History h = new History(original, narrower);
        h.push(crop());
        h.push(crop());
        h.undo();
        h.push(new Edit.AutoLevels());
        assertFalse(h.canRedo());
        assertEquals(2, h.edits().size());
        assertEquals(new Edit.AutoLevels(), h.edits().get(1));
    }

    @Test
    void droppedPicturesAreMadeAgainFromTheOriginal() {
        History h = new History(original, narrower);
        h.push(crop());
        h.push(crop());
        h.push(crop());
        h.forget();
        applied.clear();
        assertEquals(8, h.undo().width());
        assertEquals(2, applied.size(), "two steps applied again to the original");
        applied.clear();
        assertEquals(7, h.redo().width());
        assertEquals(1, applied.size(), "from the picture just made");
    }

    @Test
    void savedStateFollowsUndo() {
        History h = new History(original, narrower);
        h.push(crop());
        h.markSaved();
        assertFalse(h.modified());
        h.undo();
        assertTrue(h.modified());
        h.redo();
        assertFalse(h.modified());
        h.undo();
        h.push(new Edit.AutoLevels());
        h.undo();
        assertTrue(h.modified(), "the saved step was replaced");
    }

    @Test
    void onlyTurnsCombinesQuarterTurns() {
        History h = new History(original, narrower);
        h.push(new Edit.Turn(Orientation.ROTATE_90));
        h.push(new Edit.Turn(Orientation.ROTATE_90));
        assertEquals(Optional.of(Orientation.ROTATE_180), h.onlyTurns());
        h.push(new Edit.Turn(Orientation.MIRROR));
        assertEquals(Optional.of(Orientation.ROTATE_180.then(Orientation.MIRROR)), h.onlyTurns());
        h.push(crop());
        assertEquals(Optional.empty(), h.onlyTurns());
        h.undo();
        assertTrue(h.onlyTurns().isPresent());
    }

    @Test
    void undoWithoutStepsFails() {
        History h = new History(original, narrower);
        assertThrows(IllegalStateException.class, h::undo);
        assertThrows(IllegalStateException.class, h::redo);
    }

    @Test
    void editsCheckTheirValues() {
        assertThrows(IllegalArgumentException.class, () -> new Edit.Crop(new Rectangle(0, 0, 0, 5)));
        assertThrows(IllegalArgumentException.class, () -> new Edit.Straighten(46, true));
        assertThrows(IllegalArgumentException.class, () -> new Edit.Straighten(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> new Edit.Resize(0, 10, ResizeQuality.BEST));
        assertThrows(IllegalArgumentException.class, () -> new Edit.Resize(10, 40000, ResizeQuality.BEST));
    }

    @Test
    void cropKeepsItsOwnRectangle() {
        Rectangle r = new Rectangle(1, 2, 3, 4);
        Edit.Crop crop = new Edit.Crop(r);
        r.width = 99;
        assertEquals(3, crop.area().width);
        crop.area().width = 77;
        assertEquals(3, crop.area().width);
    }

    @Test
    void stepNames() {
        assertEquals("edit.rotate-right", new Edit.Turn(Orientation.ROTATE_90).key());
        assertEquals("edit.rotate-left", new Edit.Turn(Orientation.ROTATE_270).key());
        assertEquals("edit.flip-horizontal", new Edit.Turn(Orientation.MIRROR).key());
        assertEquals("edit.flip-vertical", new Edit.Turn(Orientation.FLIP).key());
        assertEquals("edit.turn", new Edit.Turn(Orientation.ROTATE_180).key());
        assertEquals("edit.straighten", new Edit.Straighten(1, true).key());
        assertEquals("edit.resize", new Edit.Resize(1, 1, ResizeQuality.FAST).key());
        assertEquals("edit.adjust", new Edit.Adjust(Adjustments.NONE).key());
    }
}
