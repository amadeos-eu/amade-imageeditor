package eu.amadeos.apps.imageeditor.domain;

/**
 * The brush of the Brush and Eraser tools: its diameter in picture pixels, how hard its edge is (100 is a sharp
 * edge, 0 fades from the middle out) and how much it covers (100 paints the colour fully).
 */
public record Brush(int size, int hardness, int opacity) {
    public static final int MAX_SIZE = 500;
    public static final Brush DEFAULT = new Brush(24, 60, 100);

    public Brush {
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("brush size out of range: " + size);
        if (hardness < 0 || hardness > 100) throw new IllegalArgumentException("hardness out of range: " + hardness);
        if (opacity < 1 || opacity > 100) throw new IllegalArgumentException("opacity out of range: " + opacity);
    }

    public Brush withSize(int newSize) {
        return new Brush(Math.clamp(newSize, 1, MAX_SIZE), hardness, opacity);
    }
}
