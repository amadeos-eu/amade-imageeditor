package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.LineStyle;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Brush strokes, shapes and fills on the pixels of a layer, in plain Java2D. Like {@link ImageOperations}, each
 * method returns new pixels and leaves its input as it is. Points are in the layer's own pixels.
 */
public final class Painter {
    private Painter() {
    }

    // --8<-- [start:stroke]
    /**
     * Paints a stroke: dabs of the brush along the line through the dots, close enough to look like one line.
     * Where dabs overlap the stronger one counts, not the sum, so a half-transparent brush gives an even line.
     * With {@code erase} on a layer with transparency, the stroke takes the pixels away instead.
     */
    public static BufferedImage stroke(BufferedImage image, List<Dot> dots, Brush brush, Color colour, boolean erase) {
        Rectangle area = strokeBounds(dots, brush.size()).intersection(
                new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        BufferedImage result = ImageOperations.copy(image);
        if (area.isEmpty()) return result;
        float[] mask = strokeMask(dots, brush, area);
        BufferedImage stamp = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_ARGB);
        int rgb = colour.getRGB() & 0xffffff;
        double cover = brush.opacity() / 100.0 * colour.getAlpha() / 255.0;
        int[] row = new int[area.width];
        for (int y = 0; y < area.height; y++) {
            for (int x = 0; x < area.width; x++) {
                int a = (int) Math.round(mask[y * area.width + x] * cover * 255);
                row[x] = a << 24 | rgb;
            }
            stamp.setRGB(0, y, area.width, 1, row, 0, area.width);
        }
        Graphics2D g = result.createGraphics();
        if (erase && ImageOperations.hasAlpha(image)) g.setComposite(AlphaComposite.DstOut);
        g.drawImage(stamp, area.x, area.y, null);
        g.dispose();
        return result;
    }

    /** How strongly the stroke covers each pixel of {@code area}, 0 to 1, row by row. */
    static float[] strokeMask(List<Dot> dots, Brush brush, Rectangle area) {
        float[] mask = new float[area.width * area.height];
        double radius = brush.size() / 2.0;
        double spacing = Math.max(0.5, brush.size() / 10.0);
        Dot previous = dots.getFirst();
        dab(mask, area, previous.x(), previous.y(), radius, brush.hardness());
        for (Dot dot : dots.subList(1, dots.size())) {
            double dx = dot.x() - previous.x(), dy = dot.y() - previous.y();
            double length = Math.hypot(dx, dy);
            int steps = (int) Math.ceil(length / spacing);
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                dab(mask, area, previous.x() + dx * t, previous.y() + dy * t, radius, brush.hardness());
            }
            previous = dot;
        }
        return mask;
    }
    // --8<-- [end:stroke]

    /**
     * One round dab: fully covering out to {@code hardness} percent of the radius, then fading to nothing at the
     * edge; even a hard brush fades over one pixel, so its edge is smooth.
     */
    private static void dab(float[] mask, Rectangle area, double cx, double cy, double radius, int hardness) {
        double outer = radius + 0.5;
        double inner = Math.min(radius * hardness / 100.0, radius - 0.5);
        int x0 = Math.max(area.x, (int) Math.floor(cx - outer)), x1 = Math.min(area.x + area.width - 1, (int) Math.ceil(cx + outer));
        int y0 = Math.max(area.y, (int) Math.floor(cy - outer)), y1 = Math.min(area.y + area.height - 1, (int) Math.ceil(cy + outer));
        for (int y = y0; y <= y1; y++) {
            double py = y + 0.5 - cy;
            for (int x = x0; x <= x1; x++) {
                double d = Math.hypot(x + 0.5 - cx, py);
                if (d >= outer) continue;
                float a = d <= inner ? 1f : (float) ((outer - d) / (outer - Math.max(0, inner)));
                int i = (y - area.y) * area.width + (x - area.x);
                if (a > mask[i]) mask[i] = Math.min(1f, a);
            }
        }
    }

    /** The pixels a stroke can touch. */
    public static Rectangle strokeBounds(List<Dot> dots, int size) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Dot d : dots) {
            minX = Math.min(minX, d.x());
            minY = Math.min(minY, d.y());
            maxX = Math.max(maxX, d.x());
            maxY = Math.max(maxY, d.y());
        }
        double r = size / 2.0 + 2;
        int x = (int) Math.floor(minX - r), y = (int) Math.floor(minY - r);
        return new Rectangle(x, y, (int) Math.ceil(maxX + r) - x, (int) Math.ceil(maxY + r) - y);
    }

    // --8<-- [start:shape]
    /** Draws a shape onto a copy of the pixels; {@code fill} null leaves closed shapes empty. */
    public static BufferedImage shape(BufferedImage image, ShapeKind kind, List<Dot> dots, LineStyle line,
                                      Color colour, Color fill) {
        BufferedImage result = ImageOperations.copy(image);
        Graphics2D g = result.createGraphics();
        drawShape(g, kind, dots, line, colour, fill);
        g.dispose();
        return result;
    }

    /**
     * Draws a shape with any graphics: the final pixels, and the canvas while the shape is being drawn, so that
     * what the mouse draws is exactly what the step paints.
     */
    public static void drawShape(Graphics2D g, ShapeKind kind, List<Dot> dots, LineStyle line, Color colour,
                                 Color fill) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = line.width();
        if (kind == ShapeKind.ARROW) {
            Dot from = dots.get(0), to = dots.get(1);
            double angle = Math.atan2(to.y() - from.y(), to.x() - from.x());
            double head = arrowHead(w), length = Math.hypot(to.x() - from.x(), to.y() - from.y());
            // The line stops inside the head, so its round end does not stick out of the point.
            double stop = Math.min(length, head * 0.7);
            g.setColor(colour);
            g.setStroke(stroke(line, BasicStroke.CAP_ROUND));
            g.draw(new Line2D.Double(from.x(), from.y(), to.x() - Math.cos(angle) * stop, to.y() - Math.sin(angle) * stop));
            Path2D tip = new Path2D.Double();
            tip.moveTo(to.x(), to.y());
            tip.lineTo(to.x() - head * Math.cos(angle - 0.45), to.y() - head * Math.sin(angle - 0.45));
            tip.lineTo(to.x() - head * Math.cos(angle + 0.45), to.y() - head * Math.sin(angle + 0.45));
            tip.closePath();
            g.fill(tip);
            return;
        }
        Shape outline = outline(kind, dots, w);
        if (fill != null && kind.closed()) {
            g.setColor(fill);
            g.fill(outline);
        }
        g.setColor(colour);
        g.setStroke(stroke(line, kind.closed() ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_ROUND));
        g.draw(outline);
    }
    // --8<-- [end:shape]

    /** The length of an arrow's head for a line this wide. */
    static double arrowHead(int width) {
        return 3.5 * width + 10;
    }

    /** The geometry of a shape (for the arrow, its line). */
    public static Shape outline(ShapeKind kind, List<Dot> dots, int width) {
        Dot a = dots.getFirst(), b = dots.get(Math.min(1, dots.size() - 1));
        Rectangle2D box = new Rectangle2D.Double(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.abs(b.x() - a.x()), Math.abs(b.y() - a.y()));
        return switch (kind) {
            case LINE, ARROW -> new Line2D.Double(a.x(), a.y(), b.x(), b.y());
            case RECTANGLE -> box;
            case ELLIPSE -> new Ellipse2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight());
            case ROUNDED -> {
                double r = Math.max(width * 2, Math.min(box.getWidth(), box.getHeight()) / 4);
                yield new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), r, r);
            }
            case FREEHAND -> {
                Path2D path = new Path2D.Double();
                path.moveTo(a.x(), a.y());
                for (Dot d : dots.subList(1, dots.size())) path.lineTo(d.x(), d.y());
                if (dots.size() == 1) path.lineTo(a.x() + 0.01, a.y());
                yield path;
            }
        };
    }

    private static BasicStroke stroke(LineStyle line, int cap) {
        float w = line.width();
        return switch (line.dash()) {
            case SOLID -> new BasicStroke(w, cap, BasicStroke.JOIN_ROUND);
            case DASHED -> new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{3 * w, 2 * w}, 0);
            case DOTTED -> new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{0.01f, 2 * w}, 0);
        };
    }

    // --8<-- [start:fill]
    /**
     * Fills the area around ({@code x}, {@code y}) whose colours lie within {@code tolerance} of that point's
     * colour, in every channel including transparency. A scanline fill: whole runs of a row at a time, with a
     * stack of rows still to look at instead of recursion.
     */
    public static BufferedImage fill(BufferedImage image, int x, int y, Color colour, int tolerance) {
        BufferedImage result = ImageOperations.copy(image);
        int w = image.getWidth(), h = image.getHeight();
        if (x < 0 || y < 0 || x >= w || y >= h) return result;
        boolean alpha = ImageOperations.hasAlpha(result);
        int[] pixels = result.getRGB(0, 0, w, h, null, 0, w);
        int seed = pixels[y * w + x];
        if (!alpha) seed |= 0xff000000;
        int paint = alpha ? colour.getRGB() : colour.getRGB() | 0xff000000;
        boolean[] done = new boolean[w * h];
        int[] stack = new int[64];
        int top = 0;
        stack[top++] = y * w + x;
        while (top > 0) {
            int p = stack[--top];
            int py = p / w, px = p % w;
            if (done[p] || !near(pixels[p], seed, tolerance, alpha)) continue;
            int left = px, right = px;
            while (left > 0 && !done[py * w + left - 1] && near(pixels[py * w + left - 1], seed, tolerance, alpha)) left--;
            while (right < w - 1 && !done[py * w + right + 1] && near(pixels[py * w + right + 1], seed, tolerance, alpha)) right++;
            for (int i = left; i <= right; i++) done[py * w + i] = true;
            for (int ny = py - 1; ny <= py + 1; ny += 2) {
                if (ny < 0 || ny >= h) continue;
                boolean inRun = false;
                for (int i = left; i <= right; i++) {
                    int q = ny * w + i;
                    boolean open = !done[q] && near(pixels[q], seed, tolerance, alpha);
                    if (open && !inRun) {
                        if (top == stack.length) stack = java.util.Arrays.copyOf(stack, top * 2);
                        stack[top++] = q;
                    }
                    inRun = open;
                }
            }
        }
        for (int i = 0; i < pixels.length; i++) if (done[i]) pixels[i] = paint;
        result.setRGB(0, 0, w, h, pixels, 0, w);
        return result;
    }

    private static boolean near(int argb, int seed, int tolerance, boolean alpha) {
        int a = alpha ? argb >>> 24 : 255, sa = seed >>> 24;
        // Fully transparent pixels count as alike, whatever colour they hide.
        if (a == 0 && sa == 0) return true;
        return Math.abs(a - sa) <= tolerance
                && Math.abs((argb >> 16 & 0xff) - (seed >> 16 & 0xff)) <= tolerance
                && Math.abs((argb >> 8 & 0xff) - (seed >> 8 & 0xff)) <= tolerance
                && Math.abs((argb & 0xff) - (seed & 0xff)) <= tolerance;
    }
    // --8<-- [end:fill]
}
