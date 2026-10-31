package eu.amadeos.apps.imageeditor.domain;

import java.awt.Rectangle;

/**
 * One step of the editing history. The steps are plain values; {@code ImageOperations} applies them to pixels,
 * so that a step can be applied again to the original when a remembered picture was dropped to save memory.
 */
public sealed interface Edit {
    /** The key of the step's name in the texts, for "Undo Crop" and the like. */
    String key();

    /** Cuts out a rectangle, in pixels of the picture before this step. */
    record Crop(Rectangle area) implements Edit {
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
    record Turn(Orientation orientation) implements Edit {
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
    record Straighten(double degrees, boolean crop) implements Edit {
        public static final double LIMIT = 45;

        public Straighten {
            if (!(Math.abs(degrees) <= LIMIT)) throw new IllegalArgumentException("angle out of range: " + degrees);
        }

        @Override
        public String key() {
            return "edit.straighten";
        }
    }

    record Resize(int width, int height, ResizeQuality quality) implements Edit {
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

    record Adjust(Adjustments adjustments) implements Edit {
        @Override
        public String key() {
            return "edit.adjust";
        }
    }

    /** Stretches each colour channel so that its darkest and brightest values reach black and white. */
    record AutoLevels() implements Edit {
        @Override
        public String key() {
            return "edit.auto-levels";
        }
    }
}
