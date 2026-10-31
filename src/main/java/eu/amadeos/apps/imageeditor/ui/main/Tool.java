package eu.amadeos.apps.imageeditor.ui.main;

/**
 * The tool whose panel is open at the right; {@link #NONE} shows the picture only. Crop, Straighten and Adjust
 * change the picture when they are applied and then close; the painting tools stay open and make a step with every
 * stroke, shape, fill or text.
 */
public enum Tool {
    NONE, CROP, STRAIGHTEN, ADJUST, MOVE, BRUSH, ERASER, SHAPES, TEXT, FILL, PICK;

    /** True for the painting tools, which stay open after each step. */
    public boolean paints() {
        return ordinal() >= MOVE.ordinal();
    }
}
