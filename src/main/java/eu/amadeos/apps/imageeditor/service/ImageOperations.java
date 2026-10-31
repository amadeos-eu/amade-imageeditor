package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * The editing steps in plain Java2D. Every operation returns a new picture and leaves its input as it is, so
 * that the history can keep both. Results are {@code TYPE_INT_RGB}, or {@code TYPE_INT_ARGB} when the input has
 * transparency or the step makes some (straightening without cropping).
 */
public final class ImageOperations {
    private ImageOperations() {
    }

    // --8<-- [start:apply]
    public static BufferedImage apply(BufferedImage image, Edit edit) {
        return switch (edit) {
            case Edit.Crop crop -> crop(image, crop.area());
            case Edit.Turn turn -> turn(image, turn.orientation());
            case Edit.Straighten s -> straighten(image, s.degrees(), s.crop());
            case Edit.Resize r -> resize(image, r.width(), r.height(), r.quality());
            case Edit.Adjust a -> adjust(image, a.adjustments());
            case Edit.AutoLevels ignored -> autoLevels(image);
        };
    }
    // --8<-- [end:apply]

    public static BufferedImage crop(BufferedImage image, Rectangle area) {
        Rectangle inside = area.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        if (inside.isEmpty()) throw new IllegalArgumentException("crop outside the picture: " + area);
        BufferedImage result = blank(inside.width, inside.height, hasAlpha(image));
        Graphics2D g = result.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, -inside.x, -inside.y, null);
        g.dispose();
        return result;
    }

    // --8<-- [start:turn]
    /** Quarter turns and flips, pixel for pixel (nearest neighbour, so nothing is blurred). */
    public static BufferedImage turn(BufferedImage image, Orientation orientation) {
        int w = image.getWidth(), h = image.getHeight();
        boolean swap = orientation.swapsSides();
        BufferedImage result = blank(swap ? h : w, swap ? w : h, hasAlpha(image));
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, affine(orientation, w, h), null);
        g.dispose();
        return result;
    }
    // --8<-- [end:turn]

    // --8<-- [start:straighten]
    /**
     * Turns by a small angle around the middle. With {@code crop} the result is the largest rectangle of the
     * picture's own shape that lies wholly inside the turned picture; otherwise the size stays and the corners
     * become transparent.
     */
    public static BufferedImage straighten(BufferedImage image, double degrees, boolean crop) {
        if (degrees == 0) return copy(image);
        int w = image.getWidth(), h = image.getHeight();
        Rectangle keep = crop ? inscribed(w, h, degrees) : new Rectangle(0, 0, w, h);
        BufferedImage result = blank(keep.width, keep.height, !crop || hasAlpha(image));
        Graphics2D g = result.createGraphics();
        quality(g, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.translate(-keep.x, -keep.y);
        g.rotate(Math.toRadians(degrees), w / 2.0, h / 2.0);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    /**
     * The largest rectangle with the picture's aspect ratio, centred, that fits inside the picture turned by
     * {@code degrees}, in the coordinates of the unturned picture.
     */
    public static Rectangle inscribed(int width, int height, double degrees) {
        double a = Math.toRadians(Math.abs(degrees));
        double sin = Math.sin(a), cos = Math.cos(a);
        // A centred w × h rectangle (same ratio r = width/height) fits if its corners stay inside the turned
        // picture: w cos + h sin <= width and w sin + h cos <= height, with w = s·width, h = s·height.
        double s = Math.min(width / (width * cos + height * sin), height / (width * sin + height * cos));
        int kw = Math.max(1, (int) Math.floor(width * s)), kh = Math.max(1, (int) Math.floor(height * s));
        return new Rectangle((width - kw) / 2, (height - kh) / 2, kw, kh);
    }
    // --8<-- [end:straighten]

    // --8<-- [start:resize]
    public static BufferedImage resize(BufferedImage image, int width, int height, ResizeQuality quality) {
        boolean alpha = hasAlpha(image);
        return switch (quality) {
            case PIXELS -> scaleOnce(image, width, height, alpha, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            case FAST -> scaleOnce(image, width, height, alpha, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            case BEST -> {
                // Shrinking by more than half in one bicubic step skips pixels and looks grainy, so halve first.
                BufferedImage current = image;
                int w = image.getWidth(), h = image.getHeight();
                while (w / 2 >= width && h / 2 >= height) {
                    w /= 2;
                    h /= 2;
                    current = scaleOnce(current, w, h, alpha, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                yield scaleOnce(current, width, height, alpha, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
        };
    }
    // --8<-- [end:resize]

    private static BufferedImage scaleOnce(BufferedImage image, int width, int height, boolean alpha, Object hint) {
        BufferedImage result = blank(width, height, alpha);
        Graphics2D g = result.createGraphics();
        quality(g, hint);
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return result;
    }

    // --8<-- [start:adjust]
    /**
     * Brightness, contrast, warmth and saturation, in that order, then grayscale. Brightness and contrast work
     * through a table per channel; saturation pulls each pixel towards or away from its own gray.
     */
    public static BufferedImage adjust(BufferedImage image, Adjustments a) {
        if (a.neutral()) return copy(image);
        int[][] tables = tables(a);
        double saturation = a.grayscale() ? 0 : 1 + a.saturation() / 100.0;
        int w = image.getWidth(), h = image.getHeight();
        boolean alpha = hasAlpha(image);
        BufferedImage result = blank(w, h, alpha);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) row[x] = adjustPixel(row[x], tables, saturation);
            result.setRGB(0, y, w, 1, row, 0, w);
        }
        return result;
    }

    static int adjustPixel(int argb, int[][] tables, double saturation) {
        int r = tables[0][argb >> 16 & 0xff];
        int g = tables[1][argb >> 8 & 0xff];
        int b = tables[2][argb & 0xff];
        if (saturation != 1) {
            double gray = 0.299 * r + 0.587 * g + 0.114 * b;
            r = clamp(gray + (r - gray) * saturation);
            g = clamp(gray + (g - gray) * saturation);
            b = clamp(gray + (b - gray) * saturation);
        }
        return argb & 0xff000000 | r << 16 | g << 8 | b;
    }

    /** One table per channel (red, green, blue) for brightness, contrast and warmth. */
    static int[][] tables(Adjustments a) {
        double offset = a.brightness() * 1.27;
        // Up to three times the contrast at +100, flat gray at -100.
        double factor = a.contrast() >= 0 ? 1 + a.contrast() / 50.0 : 1 + a.contrast() / 100.0;
        double warm = a.warmth() * 0.3;
        int[][] tables = new int[3][256];
        for (int v = 0; v < 256; v++) {
            double base = (v + offset - 127.5) * factor + 127.5;
            tables[0][v] = clamp(base + warm);
            tables[1][v] = clamp(base);
            tables[2][v] = clamp(base - warm);
        }
        return tables;
    }
    // --8<-- [end:adjust]

    // --8<-- [start:auto-levels]
    /**
     * Stretches each channel from its darkest to its brightest value; the darkest and brightest half percent
     * are left out, so that a few black or white specks do not hold the stretch back.
     */
    public static BufferedImage autoLevels(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        long[][] histogram = new long[3][256];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int p : row) {
                histogram[0][p >> 16 & 0xff]++;
                histogram[1][p >> 8 & 0xff]++;
                histogram[2][p & 0xff]++;
            }
        }
        long clip = (long) w * h / 200;
        int[][] tables = new int[3][256];
        for (int c = 0; c < 3; c++) {
            int low = 0, high = 255;
            long sum = 0;
            while (low < 255 && (sum += histogram[c][low]) <= clip) low++;
            sum = 0;
            while (high > 0 && (sum += histogram[c][high]) <= clip) high--;
            for (int v = 0; v < 256; v++) {
                tables[c][v] = high <= low ? v : clamp((v - low) * 255.0 / (high - low));
            }
        }
        BufferedImage result = blank(w, h, hasAlpha(image));
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) row[x] = adjustPixel(row[x], tables, 1);
            result.setRGB(0, y, w, 1, row, 0, w);
        }
        return result;
    }
    // --8<-- [end:auto-levels]

    /** A copy without transparency: transparent pixels on white, for JPEG and BMP. */
    public static BufferedImage flatten(BufferedImage image) {
        if (!hasAlpha(image) && image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage result = blank(image.getWidth(), image.getHeight(), false);
        Graphics2D g = result.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, result.getWidth(), result.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    /** The picture in {@code TYPE_INT_RGB} or {@code TYPE_INT_ARGB}, which every step and writer handles. */
    public static BufferedImage normalize(BufferedImage image) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) return image;
        return copy(image);
    }

    public static boolean hasAlpha(BufferedImage image) {
        return image.getColorModel().hasAlpha();
    }

    static BufferedImage copy(BufferedImage image) {
        BufferedImage result = blank(image.getWidth(), image.getHeight(), hasAlpha(image));
        Graphics2D g = result.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return result;
    }

    private static BufferedImage blank(int width, int height, boolean alpha) {
        return new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    private static void quality(Graphics2D g, Object interpolation) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static int clamp(double v) {
        return (int) Math.clamp(Math.round(v), 0, 255);
    }

    /**
     * Maps a picture of {@code width} × {@code height} pixels, turned by {@code orientation}, onto the rectangle
     * from (0, 0) that it covers afterwards (width and height swapped for quarter turns).
     */
    public static AffineTransform affine(Orientation orientation, int width, int height) {
        int[] m = orientation.matrix();
        double tw = orientation.swapsSides() ? height : width, th = orientation.swapsSides() ? width : height;
        double cx = width / 2.0, cy = height / 2.0;
        return new AffineTransform(m[0], m[2], m[1], m[3], tw / 2 - (m[0] * cx + m[1] * cy),
                th / 2 - (m[2] * cx + m[3] * cy));
    }
}
