package eu.amadeos.apps.imageeditor.domain;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/**
 * One step of the editing history. The steps are plain values; {@code PictureOperations} applies them to a
 * picture, so that a step can be applied again when a remembered picture was dropped to save memory.
 *
 * <p>Layers are named by their place from the bottom (0 is the bottom layer) in the picture the step is applied
 * to.
 */
public sealed interface Edit {
    /** The key of the step's name in the texts, for "Undo Crop" and the like. */
    String key();

    /**
     * The steps of step 1 that work on pixels alone: {@code ImageOperations} applies them to one picture.
     * Crop, Turn, Straighten and Resize change the whole picture with all its layers; Adjust and Auto Levels
     * change one layer.
     */
    sealed interface PixelEdit extends Edit {
    }

    /**
     * Where painting goes: on the layer itself, or, if {@code newLayer} names one, on a new empty layer of that
     * name just above it (so that painting on a photo leaves the photo as it was).
     */
    record Target(int layer, String newLayer) {
        public static Target on(int layer) {
            return new Target(layer, null);
        }

        public static Target above(int layer, String name) {
            return new Target(layer, name);
        }

        public boolean makesLayer() {
            return newLayer != null;
        }
    }
    /** Cuts out a rectangle, in pixels of the picture before this step. */
    record Crop(Rectangle area) implements PixelEdit {
        public Crop {
            area = new Rectangle(area);
            if (area.width <= 0 || area.height <= 0) throw new IllegalArgumentException("empty crop");
        }

        @Override
        public Rectangle area() {
            return new Rectangle(area);
        }

        @Override
        public String key() {
            return "edit.crop";
        }
    }

    /** Quarter turns and flips: lossless, and for JPEGs saved with jpegtran. */
    record Turn(Orientation orientation) implements PixelEdit {
        @Override
        public String key() {
            return switch (orientation) {
                case ROTATE_90 -> "edit.rotate-right";
                case ROTATE_270 -> "edit.rotate-left";
                case MIRROR -> "edit.flip-horizontal";
                case FLIP -> "edit.flip-vertical";
                default -> "edit.turn";
            };
        }
    }

    /**
     * Turns by a small angle (degrees, positive clockwise, at most 45 either way). With {@code crop} the result
     * is the largest upright rectangle of the turned picture, so no empty corners show; otherwise the picture
     * keeps its size and the corners are transparent (white in formats without transparency).
     */
    record Straighten(double degrees, boolean crop) implements PixelEdit {
        public static final double LIMIT = 45;

        public Straighten {
            if (!(Math.abs(degrees) <= LIMIT)) throw new IllegalArgumentException("angle out of range: " + degrees);
        }

        @Override
        public String key() {
            return "edit.straighten";
        }
    }

    record Resize(int width, int height, ResizeQuality quality) implements PixelEdit {
        /** The largest side the editor makes, to keep memory in bounds. */
        public static final int LIMIT = 30000;

        public Resize {
            if (width <= 0 || height <= 0 || width > LIMIT || height > LIMIT) {
                throw new IllegalArgumentException("size out of range: " + width + " x " + height);
            }
        }

        @Override
        public String key() {
            return "edit.resize";
        }
    }

    /** Brightness, contrast and colours of one layer. */
    record Adjust(Adjustments adjustments, int layer) implements PixelEdit {
        /** Of the bottom layer, the photo. */
        public Adjust(Adjustments adjustments) {
            this(adjustments, 0);
        }

        @Override
        public String key() {
            return "edit.adjust";
        }
    }

    /** Stretches each colour channel so that its darkest and brightest values reach black and white. */
    record AutoLevels(int layer) implements PixelEdit {
        /** Of the bottom layer, the photo. */
        public AutoLevels() {
            this(0);
        }

        @Override
        public String key() {
            return "edit.auto-levels";
        }
    }

    // ------------------------------------------------------------------------------------------------ painting

    /**
     * A brush stroke through {@code dots} (picture pixels). With {@code erase} it takes paint away: on a layer
     * with transparency the pixels become transparent, on a photo they get {@code colour}.
     */
    record Stroke(Target target, List<Dot> dots, Brush brush, Color colour, boolean erase) implements Edit {
        public Stroke {
            dots = List.copyOf(dots);
            if (dots.isEmpty()) throw new IllegalArgumentException("stroke without dots");
            Objects.requireNonNull(target);
            Objects.requireNonNull(brush);
            Objects.requireNonNull(colour);
        }

        @Override
        public String key() {
            return erase ? "edit.erase" : "edit.brush";
        }
    }

    /**
     * A line, arrow, rectangle, ellipse or rounded box from the first dot to the second, or a freehand line
     * through all dots; outlined in {@code colour} and filled with {@code fill} if that is not null.
     */
    record Shape(Target target, ShapeKind kind, List<Dot> dots, LineStyle line, Color colour, Color fill)
            implements Edit {
        public Shape {
            dots = List.copyOf(dots);
            if (dots.size() < (kind.twoPoints() ? 2 : 1)) throw new IllegalArgumentException("shape without points");
            Objects.requireNonNull(target);
            Objects.requireNonNull(line);
            Objects.requireNonNull(colour);
        }

        @Override
        public String key() {
            return "edit.shape." + kind.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Fills the area around a point that has (nearly) the colour of that point. {@code tolerance} is how far,
     * 0 to 255 in each channel, a colour may lie from the point's and still be filled.
     */
    record Fill(int layer, int x, int y, Color colour, int tolerance) implements Edit {
        public Fill {
            if (tolerance < 0 || tolerance > 255) throw new IllegalArgumentException("tolerance out of range");
            Objects.requireNonNull(colour);
        }

        @Override
        public String key() {
            return "edit.fill";
        }
    }

    /** A new text layer above {@code layer}, or ({@code replace}) the text of the text layer {@code layer}. */
    record Text(int layer, boolean replace, TextBox box) implements Edit {
        public Text {
            Objects.requireNonNull(box);
        }

        @Override
        public String key() {
            return replace ? "edit.text.change" : "edit.text";
        }
    }

    // ------------------------------------------------------------------------------------------------ layers

    /** An empty layer as large as the picture, just above {@code above}. */
    record NewLayer(int above, String name) implements Edit {
        @Override
        public String key() {
            return "edit.layer.new";
        }
    }

    /**
     * A picture (pasted, or from a file) as a new layer at the top, in the middle of the picture. The picture is
     * kept by this step, so the step must not be changed later.
     */
    record AddPicture(BufferedImage image, String name) implements Edit {
        public AddPicture {
            Objects.requireNonNull(image);
            Objects.requireNonNull(name);
        }

        @Override
        public String key() {
            return "edit.layer.add-picture";
        }
    }

    record DuplicateLayer(int layer, String name) implements Edit {
        @Override
        public String key() {
            return "edit.layer.duplicate";
        }
    }

    record DeleteLayer(int layer) implements Edit {
        @Override
        public String key() {
            return "edit.layer.delete";
        }
    }

    /** Moves a layer up or down the stack to place {@code to}. */
    record OrderLayer(int layer, int to) implements Edit {
        @Override
        public String key() {
            return to > layer ? "edit.layer.up" : "edit.layer.down";
        }
    }

    /** Name, visibility and opacity of a layer. */
    record LayerProperties(int layer, String name, boolean visible, int opacity) implements Edit {
        public LayerProperties {
            Objects.requireNonNull(name);
            if (opacity < 0 || opacity > 100) throw new IllegalArgumentException("opacity out of range");
        }

        @Override
        public String key() {
            return "edit.layer.properties";
        }
    }

    /** Shifts a layer by {@code dx}, {@code dy} picture pixels. */
    record MoveLayer(int layer, int dx, int dy) implements Edit {
        @Override
        public String key() {
            return "edit.layer.move";
        }
    }

    /** Lays a layer onto the one below it; the two become one. */
    record MergeDown(int layer) implements Edit {
        @Override
        public String key() {
            return "edit.layer.merge-down";
        }
    }

    /** All visible layers become one. */
    record Flatten() implements Edit {
        @Override
        public String key() {
            return "edit.layer.flatten";
        }
    }
}
