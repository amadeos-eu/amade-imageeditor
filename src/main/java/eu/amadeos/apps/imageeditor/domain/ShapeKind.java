package eu.amadeos.apps.imageeditor.domain;

/** The shapes of the Shapes tool. All but {@link #FREEHAND} are drawn from one corner (or end) to the other. */
public enum ShapeKind {
    LINE, ARROW, RECTANGLE, ELLIPSE, ROUNDED, FREEHAND;

    /** True if the shape has an inside that can be filled. */
    public boolean closed() {
        return this == RECTANGLE || this == ELLIPSE || this == ROUNDED;
    }

    /** True if the shape is drawn by dragging from one point to another, with a handle at each. */
    public boolean twoPoints() {
        return this != FREEHAND;
    }
}
