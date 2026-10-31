package eu.amadeos.apps.imageeditor.domain;

import java.util.List;

/**
 * The shape the crop frame keeps: none ({@link #FREE}), that of the whole picture ({@link #ORIGINAL}) or width to
 * height, such as 3:2. The ratios are given landscape; {@link #portrait} turns them.
 */
public record AspectRatio(String key, int width, int height) {
    public static final AspectRatio FREE = new AspectRatio("free", 0, 0);
    public static final AspectRatio ORIGINAL = new AspectRatio("original", 0, 0);
    public static final AspectRatio SQUARE = new AspectRatio("1:1", 1, 1);

    /** The choices of the crop panel, without "Custom". */
    public static final List<AspectRatio> PRESETS = List.of(FREE, ORIGINAL, SQUARE, of(4, 3), of(3, 2), of(16, 9),
            of(5, 4));

    public AspectRatio {
        if (width < 0 || height < 0) throw new IllegalArgumentException("negative ratio");
    }

    public static AspectRatio of(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("ratio needs two positive numbers");
        return new AspectRatio(width + ":" + height, width, height);
    }

    public boolean free() {
        return this.equals(FREE);
    }

    /**
     * Width divided by height for a picture of the given size, landscape or portrait as asked; 0 for
     * {@link #FREE}. A square stays a square.
     */
    public double value(int pictureWidth, int pictureHeight, boolean portrait) {
        if (free()) return 0;
        double w, h;
        if (equals(ORIGINAL)) {
            w = pictureWidth;
            h = pictureHeight;
            // "Original" means the picture's own shape; portrait swaps it like the others.
            if (portrait != (h > w)) {
                double t = w;
                w = h;
                h = t;
            }
            return w / h;
        }
        w = Math.max(width, height);
        h = Math.min(width, height);
        return portrait ? h / w : w / h;
    }
}
