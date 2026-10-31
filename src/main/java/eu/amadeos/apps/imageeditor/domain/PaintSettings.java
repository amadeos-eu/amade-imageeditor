package eu.amadeos.apps.imageeditor.domain;


import java.awt.Color;

/**
 * The settings of the painting tools as the presenter keeps them: the two colours (left and right mouse button),
 * the brush, the shape with its outline and fill, and the tolerance of the fill.
 */
public record PaintSettings(Color primary, Color secondary, Brush brush, Brush eraser, ShapeKind shape,
                            LineStyle line, ShapeFill fill, int tolerance) {
    public static final PaintSettings DEFAULT = new PaintSettings(Color.BLACK, Color.WHITE, Brush.DEFAULT,
            new Brush(40, 80, 100), ShapeKind.ARROW, LineStyle.DEFAULT, ShapeFill.NONE, 32);

    /** The colour of the mouse button: the second (right button) or the first. */
    public Color colour(boolean second) {
        return second ? secondary : primary;
    }

    /** The colour a closed shape is filled with, or null. */
    public Color fillColour(boolean second) {
        return switch (fill) {
            case NONE -> null;
            case SECONDARY -> colour(!second);
            case PRIMARY -> colour(second);
        };
    }

    public PaintSettings withColours(Color newPrimary, Color newSecondary) {
        return new PaintSettings(newPrimary, newSecondary, brush, eraser, shape, line, fill, tolerance);
    }

    public PaintSettings withBrush(Brush newBrush) {
        return new PaintSettings(primary, secondary, newBrush, eraser, shape, line, fill, tolerance);
    }

    public PaintSettings withEraser(Brush newEraser) {
        return new PaintSettings(primary, secondary, brush, newEraser, shape, line, fill, tolerance);
    }

    public PaintSettings withShape(ShapeKind newShape) {
        return new PaintSettings(primary, secondary, brush, eraser, newShape, line, fill, tolerance);
    }

    public PaintSettings withLine(LineStyle newLine) {
        return new PaintSettings(primary, secondary, brush, eraser, shape, newLine, fill, tolerance);
    }

    public PaintSettings withFill(ShapeFill newFill) {
        return new PaintSettings(primary, secondary, brush, eraser, shape, line, newFill, tolerance);
    }

    public PaintSettings withTolerance(int newTolerance) {
        return new PaintSettings(primary, secondary, brush, eraser, shape, line, fill, Math.clamp(newTolerance, 0, 255));
    }
}
