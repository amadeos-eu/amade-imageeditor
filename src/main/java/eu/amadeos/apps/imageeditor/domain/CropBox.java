package eu.amadeos.apps.imageeditor.domain;

import java.awt.Rectangle;

/**
 * The geometry of the crop frame: a rectangle inside the picture that keeps a ratio (width / height, 0 for any)
 * while it is dragged by one of its eight handles, moved, or drawn anew. All sizes are picture pixels.
 */
public final class CropBox {
    /** Where the mouse took hold of the frame. */
    public enum Handle {
        NORTH_WEST(-1, -1), NORTH(0, -1), NORTH_EAST(1, -1), EAST(1, 0), SOUTH_EAST(1, 1), SOUTH(0, 1),
        SOUTH_WEST(-1, 1), WEST(-1, 0), MOVE(0, 0);

        final int dx, dy;

        Handle(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** The smallest frame, in picture pixels. */
    public static final int MIN = 8;

    private CropBox() {
    }

    /**
     * The largest frame of the ratio, centred in the picture, at most {@code fraction} of its size (1 for all).
     */
    public static Rectangle centred(int imageWidth, int imageHeight, double ratio, double fraction) {
        double w = imageWidth * fraction, h = imageHeight * fraction;
        if (ratio > 0) {
            if (w / h > ratio) w = h * ratio;
            else h = w / ratio;
        }
        int iw = Math.max(1, (int) Math.round(w)), ih = Math.max(1, (int) Math.round(h));
        return new Rectangle((imageWidth - iw) / 2, (imageHeight - ih) / 2, iw, ih);
    }

    /**
     * The frame with the given ratio, as large as possible around the centre of {@code box} and no larger than
     * it was; used when another ratio is chosen.
     */
    public static Rectangle withRatio(Rectangle box, double ratio, int imageWidth, int imageHeight) {
        if (ratio <= 0) return clamp(box, imageWidth, imageHeight);
        double cx = box.getCenterX(), cy = box.getCenterY();
        double area = (double) box.width * box.height;
        double w = Math.sqrt(area * ratio), h = w / ratio;
        double scale = Math.min(1, Math.min(imageWidth / w, imageHeight / h));
        w *= scale;
        h *= scale;
        double x = Math.clamp(cx - w / 2, 0, imageWidth - w);
        double y = Math.clamp(cy - h / 2, 0, imageHeight - h);
        return round(x, y, w, h, imageWidth, imageHeight);
    }

    /**
     * Drags a handle of {@code start} by (dx, dy) picture pixels. Corners keep the ratio by following the larger
     * movement; edges grow the other side around the centre line; {@link Handle#MOVE} shifts the frame.
     */
    public static Rectangle drag(Rectangle start, Handle handle, double dx, double dy, double ratio,
                                 int imageWidth, int imageHeight) {
        if (handle == Handle.MOVE) {
            int x = (int) Math.round(Math.clamp(start.x + dx, 0, imageWidth - start.width));
            int y = (int) Math.round(Math.clamp(start.y + dy, 0, imageHeight - start.height));
            return new Rectangle(x, y, start.width, start.height);
        }
        double left = start.x, top = start.y, right = start.x + start.width, bottom = start.y + start.height;
        if (handle.dx < 0) left = Math.clamp(left + dx, 0, right - MIN);
        if (handle.dx > 0) right = Math.clamp(right + dx, left + MIN, imageWidth);
        if (handle.dy < 0) top = Math.clamp(top + dy, 0, bottom - MIN);
        if (handle.dy > 0) bottom = Math.clamp(bottom + dy, top + MIN, imageHeight);
        if (ratio <= 0) return round(left, top, right - left, bottom - top, imageWidth, imageHeight);

        double w = right - left, h = bottom - top;
        // The fixed point: the opposite corner, or for an edge the middle of the opposite edge.
        double fx = handle.dx < 0 ? start.x + start.width : handle.dx > 0 ? start.x : start.getCenterX();
        double fy = handle.dy < 0 ? start.y + start.height : handle.dy > 0 ? start.y : start.getCenterY();
        if (handle.dx == 0) w = h * ratio;
        else if (handle.dy == 0) h = w / ratio;
        else if (w / h > ratio) h = w / ratio;
        else w = h * ratio;
        // Room around the fixed point, in each direction the frame grows.
        double maxW = handle.dx < 0 ? fx : handle.dx > 0 ? imageWidth - fx : 2 * Math.min(fx, imageWidth - fx);
        double maxH = handle.dy < 0 ? fy : handle.dy > 0 ? imageHeight - fy : 2 * Math.min(fy, imageHeight - fy);
        double scale = Math.min(1, Math.min(maxW / w, maxH / h));
        w *= scale;
        h *= scale;
        double x = handle.dx < 0 ? fx - w : handle.dx > 0 ? fx : fx - w / 2;
        double y = handle.dy < 0 ? fy - h : handle.dy > 0 ? fy : fy - h / 2;
        return round(x, y, w, h, imageWidth, imageHeight);
    }

    /** A new frame drawn from one corner to the other, in any direction. */
    public static Rectangle draw(double x1, double y1, double x2, double y2, double ratio,
                                 int imageWidth, int imageHeight) {
        x1 = Math.clamp(x1, 0, imageWidth);
        y1 = Math.clamp(y1, 0, imageHeight);
        Handle handle = x2 >= x1
                ? (y2 >= y1 ? Handle.SOUTH_EAST : Handle.NORTH_EAST)
                : (y2 >= y1 ? Handle.SOUTH_WEST : Handle.NORTH_WEST);
        Rectangle seed = new Rectangle((int) Math.round(x1), (int) Math.round(y1), 0, 0);
        return dragFromPoint(seed, handle, x2 - x1, y2 - y1, ratio, imageWidth, imageHeight);
    }

    private static Rectangle dragFromPoint(Rectangle seed, Handle handle, double dx, double dy, double ratio,
                                           int imageWidth, int imageHeight) {
        double w = Math.max(MIN, Math.abs(dx)), h = Math.max(MIN, Math.abs(dy));
        if (ratio > 0) {
            if (w / h > ratio) h = w / ratio;
            else w = h * ratio;
        }
        double maxW = handle.dx < 0 ? seed.x : imageWidth - seed.x;
        double maxH = handle.dy < 0 ? seed.y : imageHeight - seed.y;
        double scale = Math.min(1, Math.min(maxW / w, maxH / h));
        if (scale <= 0) return clamp(new Rectangle(seed.x, seed.y, MIN, MIN), imageWidth, imageHeight);
        w *= scale;
        h *= scale;
        double x = handle.dx < 0 ? seed.x - w : seed.x;
        double y = handle.dy < 0 ? seed.y - h : seed.y;
        return round(x, y, w, h, imageWidth, imageHeight);
    }

    /** The number fields of the crop panel. */
    public enum Field { LEFT, TOP, WIDTH, HEIGHT }

    /**
     * The frame after one of its numbers was typed in: a new width or height pulls the other along when a ratio
     * is set; a new left or top moves the frame. The result lies inside the picture.
     */
    public static Rectangle typed(Rectangle box, Field field, int value, double ratio, int imageWidth,
                                  int imageHeight) {
        double x = box.x, y = box.y, w = box.width, h = box.height;
        switch (field) {
            case LEFT -> x = value;
            case TOP -> y = value;
            case WIDTH -> {
                w = Math.clamp(value, 1, imageWidth);
                if (ratio > 0) h = w / ratio;
            }
            case HEIGHT -> {
                h = Math.clamp(value, 1, imageHeight);
                if (ratio > 0) w = h * ratio;
            }
        }
        if (ratio > 0) {
            double scale = Math.min(1, Math.min(imageWidth / w, imageHeight / h));
            w *= scale;
            h *= scale;
        }
        w = Math.min(w, imageWidth);
        h = Math.min(h, imageHeight);
        x = Math.clamp(x, 0, imageWidth - w);
        y = Math.clamp(y, 0, imageHeight - h);
        return round(x, y, w, h, imageWidth, imageHeight);
    }

    /** The frame moved and shrunk until it lies inside the picture, at least one pixel. */
    public static Rectangle clamp(Rectangle box, int imageWidth, int imageHeight) {
        int w = Math.clamp(box.width, 1, imageWidth), h = Math.clamp(box.height, 1, imageHeight);
        int x = Math.clamp(box.x, 0, imageWidth - w), y = Math.clamp(box.y, 0, imageHeight - h);
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle round(double x, double y, double w, double h, int imageWidth, int imageHeight) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        int iw = (int) Math.round(w), ih = (int) Math.round(h);
        return clamp(new Rectangle(ix, iy, iw, ih), imageWidth, imageHeight);
    }
}
