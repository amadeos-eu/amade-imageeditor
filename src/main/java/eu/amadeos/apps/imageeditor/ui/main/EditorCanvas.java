package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.PaintSettings;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import eu.amadeos.apps.imageeditor.domain.ZoomSteps;
import eu.amadeos.apps.imageeditor.service.ImageOperations;
import eu.amadeos.apps.imageeditor.service.Painter;
import eu.amadeos.apps.imageeditor.service.TextRenderer;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Shows the picture being edited on a dark background, fitted or zoomed, with what the open tool draws over it:
 * the crop frame with its handles, the turned picture and its grid while straightening, the adjusted picture,
 * optionally split against the original, the brush stroke being painted, the shape with its handles, the frame of
 * the text being typed. The canvas only shows and reports; the presenter decides.
 */
public final class EditorCanvas extends JComponent {
    /** What the canvas reports. */
    public interface Listener {
        /** The crop frame was dragged, moved or drawn anew (picture pixels). */
        void cropChanged(Rectangle box);

        /** A line was drawn along something that should be level: straighten by this many degrees. */
        void levelLine(double degrees);

        /** The shown scale changed, for the status bar. */
        void scaleChanged(double scale);

        /** A brush or eraser stroke was painted through these points; {@code second} for the right button. */
        void painted(List<Dot> dots, boolean second);

        /** A shape was drawn or its handles moved; a freehand shape is reported once, when it is finished. */
        void shapeChanged(List<Dot> dots, boolean second);

        /** The shape with handles is finished: a new one begins elsewhere. */
        void shapeDone();

        /** A click with the Fill, Pick or Text tool. */
        void clicked(Dot at, boolean second);

        /** Alt+click with a painting tool: take the colour under the pointer. */
        void picked(Dot at, boolean second);

        /** The Move tool drags the layer this far (picture pixels); {@code done} when the button is released. */
        void layerDragged(int dx, int dy, boolean done);

        /** The open text's frame was dragged this far (picture pixels). */
        void textDragged(double dx, double dy);
    }

    private static final Color BACKGROUND = new Color(0x2b2b2b);
    private static final Color DIM = new Color(0, 0, 0, 140);
    private static final int HANDLE = 9;

    private BufferedImage image;
    private String message;
    private Listener listener;
    private boolean fit = true;
    private double factor = 1;
    private double centreX, centreY;
    private double reportedScale = -1;

    private Tool tool = Tool.NONE;
    private Rectangle crop;
    private double cropRatio;
    private Guides guides = Guides.THIRDS;
    private double degrees;
    private boolean straightenCrops = true;
    private UnaryOperator<BufferedImage> look;
    /** Where the original/adjusted split is, as a fraction of the shown picture's width; negative for none. */
    private double split = -1;
    private String originalLabel = "Original", adjustedLabel = "Adjusted";

    private BufferedImage scaled;
    private double scaledFor = -1;
    private BufferedImage looked;
    private UnaryOperator<BufferedImage> lookedWith;
    private final Timer rescale = new Timer(150, e -> repaint());

    /** The line being drawn while straightening, in screen pixels. */
    private Line2D.Double levelLine;

    private PaintSettings paint = PaintSettings.DEFAULT;
    private List<Dot> pendingShape;
    private boolean pendingSecond;
    private TextBox text;
    private Overlay overlay;
    /** The stroke being painted, in picture pixels. */
    private List<Dot> stroke;
    private boolean strokeSecond;
    /** Strokes painted but not yet in the picture: they stay on screen until the painted picture comes. */
    private final List<List<Dot>> waiting = new ArrayList<>();
    private final List<Boolean> waitingSecond = new ArrayList<>();
    /** The end of the last stroke, for Shift+click straight lines. */
    private Dot lastDot;
    /** Where the pointer is, for the brush outline; null outside. */
    private Point pointer;

    public EditorCanvas() {
        setOpaque(true);
        setBackground(BACKGROUND);
        setFocusable(true);
        setPreferredSize(new Dimension(700, 480));
        rescale.setRepeats(false);
        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                clampCentre();
                rescale.restart();
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** A new or edited picture, fitted into the view. */
    public void show(BufferedImage picture) {
        image = picture;
        message = null;
        scaled = null;
        looked = null;
        fit = true;
        centreX = picture.getWidth() / 2.0;
        centreY = picture.getHeight() / 2.0;
        repaint();
        reportScale();
    }

    /**
     * A changed picture: one of the same size keeps the zoom and place, so that painting does not jump; another
     * size is fitted like a new picture.
     */
    public void update(BufferedImage picture) {
        if (image == null || picture.getWidth() != image.getWidth() || picture.getHeight() != image.getHeight()) {
            show(picture);
            return;
        }
        image = picture;
        message = null;
        scaled = null;
        looked = null;
        waiting.clear();
        waitingSecond.clear();
        repaint();
    }

    public void showMessage(String text) {
        image = null;
        scaled = null;
        looked = null;
        message = text;
        repaint();
    }

    public BufferedImage image() {
        return image;
    }

    /** The open tool and what it draws; null values leave the current ones. */
    public void showTool(Tool newTool) {
        tool = newTool;
        levelLine = null;
        stroke = null;
        if (tool != Tool.ADJUST) {
            look = null;
            split = -1;
            looked = null;
        }
        setCursor(cursorForTool());
        repaint();
    }

    private Cursor cursorForTool() {
        return Cursor.getPredefinedCursor(switch (tool) {
            case BRUSH, ERASER, SHAPES, FILL, PICK -> Cursor.CROSSHAIR_CURSOR;
            case TEXT -> Cursor.TEXT_CURSOR;
            case MOVE -> Cursor.MOVE_CURSOR;
            default -> Cursor.DEFAULT_CURSOR;
        });
    }

    public void showCrop(Rectangle box, double ratio, Guides newGuides) {
        crop = box == null ? null : new Rectangle(box);
        cropRatio = ratio;
        guides = newGuides;
        repaint();
    }

    public void showStraighten(double newDegrees, boolean crops) {
        degrees = newDegrees;
        straightenCrops = crops;
        repaint();
    }

    /** The adjusted look, applied to what is shown (not to the full picture, which would be slow). */
    public void showLook(UnaryOperator<BufferedImage> newLook) {
        if (newLook != look) looked = null;
        look = newLook;
        repaint();
    }

    /** Shows the original left of the split and the adjusted picture right of it; false hides the split. */
    public void showCompare(boolean on) {
        split = on ? 0.5 : -1;
        repaint();
    }

    public void showPaint(PaintSettings settings) {
        paint = settings;
        repaint();
    }

    public void showPendingShape(List<Dot> dots, boolean second) {
        pendingShape = dots == null ? null : List.copyOf(dots);
        pendingSecond = second;
        repaint();
    }

    public void showText(TextBox draft) {
        text = draft;
        repaint();
    }

    public void showOverlay(Overlay newOverlay) {
        overlay = newOverlay;
        repaint();
    }

    public void setCompareLabels(String original, String adjusted) {
        originalLabel = original;
        adjustedLabel = adjusted;
    }

    public boolean comparing() {
        return split >= 0;
    }

    // ---------------------------------------------------------------- zoom

    public void zoom(ZoomCommand command) {
        if (image == null) return;
        Point middle = new Point(getWidth() / 2, getHeight() / 2);
        switch (command) {
            case IN -> zoomAt(ZoomSteps.in(scale()), middle);
            case OUT -> zoomAt(ZoomSteps.out(scale()), middle);
            case ACTUAL -> zoomAt(1, middle);
            case FIT -> {
                fit = true;
                centreX = image.getWidth() / 2.0;
                centreY = image.getHeight() / 2.0;
                repaint();
                reportScale();
            }
        }
    }

    private void zoomAt(double newFactor, Point at) {
        double old = scale();
        double px = centreX + (at.x - getWidth() / 2.0) / old;
        double py = centreY + (at.y - getHeight() / 2.0) / old;
        fit = false;
        factor = Math.clamp(newFactor, ZoomSteps.MIN, ZoomSteps.MAX);
        centreX = px - (at.x - getWidth() / 2.0) / factor;
        centreY = py - (at.y - getHeight() / 2.0) / factor;
        clampCentre();
        repaint();
        reportScale();
    }

    /** The scale the picture is shown at; fitted pictures keep a small margin for the crop handles. */
    public double scale() {
        if (image == null) return 1;
        if (!fit) return factor;
        int margin = 2 * HANDLE;
        return ZoomSteps.fit(image.getWidth(), image.getHeight(), getWidth() - margin, getHeight() - margin, false);
    }

    private boolean scrollable() {
        return image != null && (image.getWidth() * scale() > getWidth() + 0.5
                || image.getHeight() * scale() > getHeight() + 0.5);
    }

    private void clampCentre() {
        if (image == null) return;
        double scale = scale();
        double halfW = getWidth() / 2.0 / scale, halfH = getHeight() / 2.0 / scale;
        int w = image.getWidth(), h = image.getHeight();
        centreX = w <= 2 * halfW ? w / 2.0 : Math.clamp(centreX, halfW, w - halfW);
        centreY = h <= 2 * halfH ? h / 2.0 : Math.clamp(centreY, halfH, h - halfH);
    }

    private void reportScale() {
        double scale = scale();
        if (listener != null && Math.abs(scale - reportedScale) > 1e-9) {
            reportedScale = scale;
            listener.scaleChanged(scale);
        }
    }

    // ---------------------------------------------------------------- coordinates

    private double originX() {
        return getWidth() / 2.0 - centreX * scale();
    }

    private double originY() {
        return getHeight() / 2.0 - centreY * scale();
    }

    /** A rectangle of picture pixels on the screen. */
    Rectangle2D toScreen(Rectangle box) {
        double s = scale();
        return new Rectangle2D.Double(originX() + box.x * s, originY() + box.y * s, box.width * s, box.height * s);
    }

    double imageX(double screenX) {
        return (screenX - originX()) / scale();
    }

    double imageY(double screenY) {
        return (screenY - originY()) / scale();
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (image == null) {
            paintMessage(g);
            g.dispose();
            return;
        }
        if (fit) clampCentre();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = scale();
        Rectangle2D shown = toScreen(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        switch (tool) {
            case STRAIGHTEN -> paintStraighten(g, shown);
            case ADJUST -> paintAdjust(g, shown);
            default -> {
                paintChecks(g, shown);
                paintPicture(g, shown, false);
            }
        }
        if (tool == Tool.CROP && crop != null) paintCrop(g, shown);
        if (tool.paints()) paintPainting(g);
        g.dispose();
        reportScale();
        if (s < 1 && scaled == null) rescale.restart();
    }

    /** A checkerboard under transparent pictures. */
    private void paintChecks(Graphics2D g, Rectangle2D shown) {
        if (!ImageOperations.hasAlpha(image)) return;
        BufferedImage tile = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D t = tile.createGraphics();
        t.setColor(new Color(0x999999));
        t.fillRect(0, 0, 16, 16);
        t.setColor(new Color(0x666666));
        t.fillRect(0, 0, 8, 8);
        t.fillRect(8, 8, 8, 8);
        t.dispose();
        Graphics2D c = (Graphics2D) g.create();
        c.setPaint(new TexturePaint(tile, new Rectangle(0, 0, 16, 16)));
        c.fill(shown);
        c.dispose();
    }

    // --8<-- [start:look]
    /** The picture, with the adjusted look if {@code adjusted}. */
    private void paintPicture(Graphics2D g, Rectangle2D shown, boolean adjusted) {
        double s = scale();
        if (s < 1) {
            BufferedImage small = scaledImage(s);
            if (small == null) {
                // Resizing: a quick picture now, the good one when the size settles.
                Graphics2D q = (Graphics2D) g.create();
                q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                q.drawImage(image, (int) Math.round(shown.getX()), (int) Math.round(shown.getY()),
                        (int) Math.round(shown.getWidth()), (int) Math.round(shown.getHeight()), null);
                q.dispose();
                return;
            }
            if (adjusted && look != null) small = lookedImage(small);
            g.drawImage(small, (int) Math.round(shown.getX()), (int) Math.round(shown.getY()), null);
            return;
        }
        // Enlarged: only the part in view, so that the look stays quick.
        Rectangle visible = new Rectangle((int) Math.floor(imageX(0)), (int) Math.floor(imageY(0)),
                (int) Math.ceil(getWidth() / s) + 2, (int) Math.ceil(getHeight() / s) + 2)
                .intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        if (visible.isEmpty()) return;
        BufferedImage part = image.getSubimage(visible.x, visible.y, visible.width, visible.height);
        if (adjusted && look != null) part = look.apply(part);
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, s >= 2
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Rectangle2D at = toScreen(visible);
        q.drawImage(part, (int) Math.round(at.getX()), (int) Math.round(at.getY()),
                (int) Math.round(at.getWidth()), (int) Math.round(at.getHeight()), null);
        q.dispose();
    }
    // --8<-- [end:look]

    private BufferedImage scaledImage(double scale) {
        if (scaled != null && Math.abs(scaledFor - scale) < 1e-6) return scaled;
        if (rescale.isRunning()) return null;
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        scaled = ImageOperations.resize(image, w, h, eu.amadeos.apps.imageeditor.domain.ResizeQuality.BEST);
        scaledFor = scale;
        looked = null;
        return scaled;
    }

    private BufferedImage lookedImage(BufferedImage small) {
        if (looked == null || lookedWith != look || looked.getWidth() != small.getWidth()) {
            looked = look.apply(small);
            lookedWith = look;
        }
        return looked;
    }

    // --8<-- [start:paint-overlay]
    /**
     * What the painting tools draw over the picture: the overlay (moved layer, typed text), the stroke being
     * painted, the pending shape with its handles, the text frame and the brush outline. Strokes and shapes are
     * drawn in picture pixels through the zoom, with the same code that paints them for real.
     */
    private void paintPainting(Graphics2D g) {
        double s = scale();
        Graphics2D p = (Graphics2D) g.create();
        p.translate(originX(), originY());
        p.scale(s, s);
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (overlay != null) overlay.paint((Graphics2D) p.create());
        for (int i = 0; i < waiting.size(); i++) paintStroke(p, waiting.get(i), waitingSecond.get(i));
        if (stroke != null && !stroke.isEmpty()) paintStroke(p, stroke, strokeSecond);
        if (pendingShape != null && tool == Tool.SHAPES) {
            Painter.drawShape(p, paint.shape(), pendingShape, paint.line(), paint.colour(pendingSecond),
                    paint.fillColour(pendingSecond));
        }
        p.dispose();
        if (pendingShape != null && tool == Tool.SHAPES && paint.shape().twoPoints()) {
            for (Dot d : pendingShape.subList(0, 2)) handle(g, screen(d));
        }
        if (text != null && tool == Tool.TEXT) {
            Point2D[] corners = TextRenderer.frame(text);
            Path2D frame = new Path2D.Double();
            for (int i = 0; i < corners.length; i++) {
                Point2D c = screen(new Dot(corners[i].getX(), corners[i].getY()));
                if (i == 0) frame.moveTo(c.getX(), c.getY());
                else frame.lineTo(c.getX(), c.getY());
            }
            frame.closePath();
            Graphics2D f = (Graphics2D) g.create();
            f.setStroke(new BasicStroke(1.5f));
            f.setColor(new Color(0, 0, 0, 160));
            f.draw(frame);
            f.setColor(Color.WHITE);
            f.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{5, 4}, 0));
            f.draw(frame);
            f.dispose();
            for (Point2D c : corners) handle(g, screen(new Dot(c.getX(), c.getY())));
        }
        if (pointer != null && (tool == Tool.BRUSH || tool == Tool.ERASER)) {
            double d = Math.max(3, (tool == Tool.ERASER ? paint.eraser() : paint.brush()).size() * s);
            Graphics2D o = (Graphics2D) g.create();
            o.setStroke(new BasicStroke(1));
            o.setColor(Color.BLACK);
            o.draw(new Ellipse2D.Double(pointer.x - d / 2 - 1, pointer.y - d / 2 - 1, d + 2, d + 2));
            o.setColor(Color.WHITE);
            o.draw(new Ellipse2D.Double(pointer.x - d / 2, pointer.y - d / 2, d, d));
            o.dispose();
        }
    }
    // --8<-- [end:paint-overlay]

    /** A stroke as it will look, roughly: the brush's width and colour, without its soft edge. */
    private void paintStroke(Graphics2D p, List<Dot> dots, boolean second) {
        boolean erase = tool == Tool.ERASER;
        var brush = erase ? paint.eraser() : paint.brush();
        Color c = erase ? new Color(128, 128, 128, 140) : paint.colour(second);
        p.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                erase ? c.getAlpha() : (int) Math.round(255 * brush.opacity() / 100.0)));
        p.setStroke(new BasicStroke(brush.size(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        p.draw(polyline(dots));
    }

    private static Path2D polyline(List<Dot> dots) {
        Path2D path = new Path2D.Double();
        path.moveTo(dots.getFirst().x(), dots.getFirst().y());
        for (Dot d : dots) path.lineTo(d.x(), d.y());
        if (dots.size() == 1) path.lineTo(dots.getFirst().x() + 0.01, dots.getFirst().y());
        return path;
    }

    private static void handle(Graphics2D g, Point2D at) {
        int x = (int) Math.round(at.getX()), y = (int) Math.round(at.getY());
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        g.fillRect(x - HANDLE / 2, y - HANDLE / 2, HANDLE, HANDLE);
        g.setColor(Color.BLACK);
        g.drawRect(x - HANDLE / 2, y - HANDLE / 2, HANDLE, HANDLE);
    }

    Point2D screen(Dot d) {
        double s = scale();
        return new Point2D.Double(originX() + d.x() * s, originY() + d.y() * s);
    }

    Dot dot(Point p) {
        return new Dot(imageX(p.x), imageY(p.y));
    }

    private void paintCrop(Graphics2D g, Rectangle2D shown) {
        Rectangle2D box = toScreen(crop);
        Area dim = new Area(shown);
        dim.subtract(new Area(box));
        g.setColor(DIM);
        g.fill(dim);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(255, 255, 255, 110));
        int lines = guides == Guides.THIRDS ? 3 : guides == Guides.GRID ? 6 : 0;
        for (int i = 1; i < lines; i++) {
            double x = box.getX() + box.getWidth() * i / lines, y = box.getY() + box.getHeight() * i / lines;
            g.draw(new Line2D.Double(x, box.getY(), x, box.getMaxY()));
            g.draw(new Line2D.Double(box.getX(), y, box.getMaxX(), y));
        }
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);
        for (Point p : handlePoints(box)) {
            g.setColor(Color.WHITE);
            g.fillRect(p.x - HANDLE / 2, p.y - HANDLE / 2, HANDLE, HANDLE);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1));
            g.drawRect(p.x - HANDLE / 2, p.y - HANDLE / 2, HANDLE, HANDLE);
        }
        label(g, crop.width + " × " + crop.height, (int) box.getMaxX() - 8, (int) box.getMaxY() - 8, true);
    }

    private void paintStraighten(Graphics2D g, Rectangle2D shown) {
        Graphics2D r = (Graphics2D) g.create();
        r.rotate(Math.toRadians(degrees), shown.getCenterX(), shown.getCenterY());
        paintPicture(r, shown, false);
        r.dispose();
        Rectangle keep = straightenCrops
                ? ImageOperations.inscribed(image.getWidth(), image.getHeight(), degrees)
                : new Rectangle(0, 0, image.getWidth(), image.getHeight());
        Rectangle2D box = toScreen(keep);
        Area dim = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        dim.subtract(new Area(box));
        g.setColor(new Color(0x2b, 0x2b, 0x2b, 170));
        g.fill(dim);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1));
        for (int i = 1; i < 8; i++) {
            double x = box.getX() + box.getWidth() * i / 8;
            g.draw(new Line2D.Double(x, box.getY(), x, box.getMaxY()));
        }
        for (int i = 1; i < 6; i++) {
            double y = box.getY() + box.getHeight() * i / 6;
            g.draw(new Line2D.Double(box.getX(), y, box.getMaxX(), y));
        }
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(box);
        if (levelLine != null) {
            g.setColor(new Color(0xffd84a));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{8, 5}, 0));
            g.draw(levelLine);
            for (double[] p : new double[][]{{levelLine.x1, levelLine.y1}, {levelLine.x2, levelLine.y2}}) {
                g.fill(new Ellipse2D.Double(p[0] - 5, p[1] - 5, 10, 10));
            }
        }
        label(g, String.format(java.util.Locale.ROOT, "%+.1f°", degrees), (int) box.getMaxX() - 8,
                (int) box.getY() + 8, false);
    }

    private void paintAdjust(Graphics2D g, Rectangle2D shown) {
        paintChecks(g, shown);
        if (split < 0) {
            paintPicture(g, shown, true);
            return;
        }
        double x = shown.getX() + shown.getWidth() * split;
        Shape left = new Rectangle2D.Double(0, 0, x, getHeight());
        Shape right = new Rectangle2D.Double(x, 0, getWidth() - x, getHeight());
        Graphics2D l = (Graphics2D) g.create();
        l.clip(left);
        paintPicture(l, shown, false);
        l.dispose();
        Graphics2D r = (Graphics2D) g.create();
        r.clip(right);
        paintPicture(r, shown, true);
        r.dispose();
        double top = Math.max(0, shown.getY()), bottom = Math.min(getHeight(), shown.getMaxY());
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.draw(new Line2D.Double(x, top, x, bottom));
        double knobY = (top + bottom) / 2;
        g.fill(new Ellipse2D.Double(x - 9, knobY - 9, 18, 18));
        g.setColor(new Color(0x444444));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Line2D.Double(x - 4, knobY, x - 1, knobY - 3));
        g.draw(new Line2D.Double(x - 4, knobY, x - 1, knobY + 3));
        g.draw(new Line2D.Double(x + 4, knobY, x + 1, knobY - 3));
        g.draw(new Line2D.Double(x + 4, knobY, x + 1, knobY + 3));
        label(g, originalLabel, (int) Math.max(8, shown.getX() + 8), (int) top + 8, false);
        label(g, adjustedLabel, (int) Math.min(getWidth() - 8, shown.getMaxX() - 8), (int) top + 8, true);
    }

    /** A white text on a dark rounded box; {@code fromRight} aligns its right edge at x. */
    private void label(Graphics2D g, String text, int x, int y, boolean fromRight) {
        Font font = (getFont() == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12) : getFont()).deriveFont(Font.BOLD);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 12, h = fm.getHeight() + 4;
        int left = fromRight ? x - w : x;
        int top = y + h > getHeight() ? getHeight() - h : y;
        if (fromRight && top == y && y > h + 8 && tool == Tool.CROP) top = y - h;
        Graphics2D c = (Graphics2D) g.create();
        c.setComposite(AlphaComposite.SrcOver);
        c.setColor(new Color(0, 0, 0, 170));
        c.fillRoundRect(left, top, w, h, 6, 6);
        c.setColor(Color.WHITE);
        c.drawString(text, left + 6, top + fm.getAscent() + 2);
        c.dispose();
    }

    private void paintMessage(Graphics2D g) {
        if (message == null || message.isEmpty()) return;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xc8c8c8));
        g.setFont(getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : getFont().deriveFont(14f));
        FontMetrics fm = g.getFontMetrics();
        String[] lines = message.split("\n");
        int y = (getHeight() - lines.length * fm.getHeight()) / 2 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
            y += fm.getHeight();
        }
    }

    private static Point[] handlePoints(Rectangle2D b) {
        int x0 = (int) Math.round(b.getX()), x1 = (int) Math.round(b.getCenterX()), x2 = (int) Math.round(b.getMaxX());
        int y0 = (int) Math.round(b.getY()), y1 = (int) Math.round(b.getCenterY()), y2 = (int) Math.round(b.getMaxY());
        // In the order of CropBox.Handle.
        return new Point[]{new Point(x0, y0), new Point(x1, y0), new Point(x2, y0), new Point(x2, y1),
                new Point(x2, y2), new Point(x1, y2), new Point(x0, y2), new Point(x0, y1)};
    }

    /** The handle at a screen point: one of the eight, MOVE inside the frame, or null outside. */
    CropBox.Handle handleAt(Point p) {
        if (crop == null) return null;
        Rectangle2D box = toScreen(crop);
        Point[] points = handlePoints(box);
        CropBox.Handle[] handles = CropBox.Handle.values();
        for (int i = 0; i < points.length; i++) {
            if (Math.abs(p.x - points[i].x) <= HANDLE && Math.abs(p.y - points[i].y) <= HANDLE) return handles[i];
        }
        return box.contains(p) ? CropBox.Handle.MOVE : null;
    }

    private static Cursor cursorFor(CropBox.Handle handle) {
        if (handle == null) return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        return Cursor.getPredefinedCursor(switch (handle) {
            case NORTH_WEST -> Cursor.NW_RESIZE_CURSOR;
            case NORTH -> Cursor.N_RESIZE_CURSOR;
            case NORTH_EAST -> Cursor.NE_RESIZE_CURSOR;
            case EAST -> Cursor.E_RESIZE_CURSOR;
            case SOUTH_EAST -> Cursor.SE_RESIZE_CURSOR;
            case SOUTH -> Cursor.S_RESIZE_CURSOR;
            case SOUTH_WEST -> Cursor.SW_RESIZE_CURSOR;
            case WEST -> Cursor.W_RESIZE_CURSOR;
            case MOVE -> Cursor.MOVE_CURSOR;
        });
    }

    /** The degrees that make a line drawn on the screen level (or upright, if it is steeper than 45°). */
    // --8<-- [start:levelling]
    static double levelling(double current, double x1, double y1, double x2, double y2) {
        double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
        // A line has no direction: bring it into -90..90, then steep lines count as vertical ones.
        if (angle > 90) angle -= 180;
        if (angle < -90) angle += 180;
        if (angle > 45) angle -= 90;
        else if (angle < -45) angle += 90;
        return Math.clamp(current - angle, -45, 45);
    }
    // --8<-- [end:levelling]

    // ---------------------------------------------------------------- mouse

    /** Where a shape's second point goes with Shift: lines in steps of 15°, boxes square. */
    // --8<-- [start:constrain]
    static Dot constrain(ShapeKind kind, Dot from, Dot to) {
        double dx = to.x() - from.x(), dy = to.y() - from.y();
        if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) {
            double length = Math.hypot(dx, dy);
            double angle = Math.round(Math.atan2(dy, dx) / Math.toRadians(15)) * Math.toRadians(15);
            return new Dot(from.x() + length * Math.cos(angle), from.y() + length * Math.sin(angle));
        }
        double side = Math.max(Math.abs(dx), Math.abs(dy));
        return new Dot(from.x() + Math.copySign(side, dx), from.y() + Math.copySign(side, dy));
    }
    // --8<-- [end:constrain]

    private final class Mouse extends MouseAdapter {
        private Point from;
        private Rectangle cropAtPress;
        private CropBox.Handle handle;
        private boolean drawing;
        private boolean panning;
        private boolean splitting;
        private boolean second;
        private boolean dragged;
        /** Shapes: which point is dragged (0 or 1), -1 for the whole shape, -2 for a new shape. */
        private int shapePoint;
        private List<Dot> shapeAtPress;
        private List<Dot> freehand;
        private boolean textDragging;
        private Dot textFrom;

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (image == null) return;
            boolean left = SwingUtilities.isLeftMouseButton(e), right = SwingUtilities.isRightMouseButton(e);
            if (SwingUtilities.isMiddleMouseButton(e) && scrollable()) {
                from = e.getPoint();
                panning = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return;
            }
            if (tool.paints() && (left || right)) {
                from = e.getPoint();
                second = right;
                dragged = false;
                pressPainting(e);
                return;
            }
            if (!left) return;
            from = e.getPoint();
            switch (tool) {
                case CROP -> {
                    handle = handleAt(e.getPoint());
                    cropAtPress = crop == null ? null : new Rectangle(crop);
                    drawing = handle == null;
                }
                case STRAIGHTEN -> levelLine = new Line2D.Double(e.getX(), e.getY(), e.getX(), e.getY());
                case ADJUST -> {
                    splitting = split >= 0 && Math.abs(e.getX() - splitX()) <= 10;
                    panning = !splitting && scrollable();
                }
                default -> panning = scrollable();
            }
            if (panning) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }

        private void pressPainting(MouseEvent e) {
            Dot at = dot(e.getPoint());
            if (e.isAltDown() && tool != Tool.MOVE && tool != Tool.TEXT) {
                if (listener != null) listener.picked(at, second);
                from = null;
                return;
            }
            switch (tool) {
                case BRUSH, ERASER -> {
                    stroke = new ArrayList<>();
                    strokeSecond = second;
                    // Shift+click: a straight line from where the last stroke ended.
                    if (e.isShiftDown() && lastDot != null) stroke.add(lastDot);
                    stroke.add(at);
                    repaint();
                }
                case SHAPES -> pressShape(e, at);
                case TEXT -> {
                    textDragging = text != null && TextRenderer.hits(text, at.x(), at.y());
                    textFrom = at;
                }
                default -> {
                }
            }
        }

        private void pressShape(MouseEvent e, Dot at) {
            freehand = null;
            if (paint.shape() == ShapeKind.FREEHAND) {
                if (pendingShape != null && listener != null) listener.shapeDone();
                freehand = new ArrayList<>(List.of(at));
                stroke = null;
                return;
            }
            shapePoint = -2;
            if (pendingShape != null) {
                for (int i = 0; i < 2; i++) {
                    if (screen(pendingShape.get(i)).distance(e.getPoint()) <= HANDLE) shapePoint = i;
                }
                if (shapePoint == -2 && Painter.outline(paint.shape(), pendingShape, paint.line().width())
                        .getBounds2D().contains(at.x(), at.y())) {
                    shapePoint = -1;
                }
                if (shapePoint == -2 && listener != null) listener.shapeDone();
            }
            shapeAtPress = shapePoint == -2 ? List.of(at, at) : pendingShape;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            pointer = e.getPoint();
            if (from == null || image == null) return;
            if (panning) {
                double s = scale();
                centreX -= (e.getX() - from.x) / s;
                centreY -= (e.getY() - from.y) / s;
                from = e.getPoint();
                clampCentre();
                repaint();
                return;
            }
            if (tool.paints()) {
                if (e.getPoint().distance(from) >= 2) dragged = true;
                dragPainting(e);
                return;
            }
            switch (tool) {
                case CROP -> {
                    Rectangle box;
                    if (drawing) {
                        box = CropBox.draw(imageX(from.x), imageY(from.y), imageX(e.getX()), imageY(e.getY()),
                                cropRatio, image.getWidth(), image.getHeight());
                    } else {
                        double s = scale();
                        box = CropBox.drag(cropAtPress, handle, (e.getX() - from.x) / s, (e.getY() - from.y) / s,
                                cropRatio, image.getWidth(), image.getHeight());
                    }
                    crop = box;
                    repaint();
                    if (listener != null) listener.cropChanged(box);
                }
                case STRAIGHTEN -> {
                    levelLine.x2 = e.getX();
                    levelLine.y2 = e.getY();
                    repaint();
                }
                case ADJUST -> {
                    if (splitting) {
                        Rectangle2D shown = toScreen(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
                        split = Math.clamp((e.getX() - shown.getX()) / shown.getWidth(), 0, 1);
                        repaint();
                    }
                }
                default -> {
                }
            }
        }

        private void dragPainting(MouseEvent e) {
            Dot at = dot(e.getPoint());
            double s = scale();
            switch (tool) {
                case BRUSH, ERASER -> {
                    if (stroke == null) return;
                    Dot last = stroke.getLast();
                    if (Math.hypot(at.x() - last.x(), at.y() - last.y()) * s >= 1) stroke.add(at);
                    repaint();
                }
                case SHAPES -> {
                    if (freehand != null) {
                        Dot last = freehand.getLast();
                        if (Math.hypot(at.x() - last.x(), at.y() - last.y()) * s >= 1) freehand.add(at);
                        stroke = null;
                        showPendingShape(freehand, second);
                        return;
                    }
                    if (shapeAtPress == null || !dragged) return;
                    Dot a = shapeAtPress.get(0), b = shapeAtPress.get(1);
                    List<Dot> moved = switch (shapePoint) {
                        case 0 -> List.of(e.isShiftDown() ? constrain(paint.shape(), b, at) : at, b);
                        case 1 -> List.of(a, e.isShiftDown() ? constrain(paint.shape(), a, at) : at);
                        case -1 -> {
                            double dx = (e.getX() - from.x) / s, dy = (e.getY() - from.y) / s;
                            yield List.of(new Dot(a.x() + dx, a.y() + dy), new Dot(b.x() + dx, b.y() + dy));
                        }
                        default -> List.of(a, e.isShiftDown() ? constrain(paint.shape(), a, at) : at);
                    };
                    if (listener != null) listener.shapeChanged(moved, shapePoint == -2 ? second : pendingSecond);
                }
                case MOVE -> {
                    if (listener != null) {
                        listener.layerDragged((int) Math.round((e.getX() - from.x) / s),
                                (int) Math.round((e.getY() - from.y) / s), false);
                    }
                }
                case TEXT -> {
                    if (textDragging && listener != null) {
                        listener.textDragged(at.x() - textFrom.x(), at.y() - textFrom.y());
                        textFrom = at;
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (tool == Tool.STRAIGHTEN && levelLine != null) {
                Line2D.Double line = levelLine;
                levelLine = null;
                repaint();
                if (line.getP1().distance(line.getP2()) >= 12 && listener != null) {
                    listener.levelLine(levelling(degrees, line.x1, line.y1, line.x2, line.y2));
                }
            }
            if (tool.paints() && from != null && !panning) releasePainting(e);
            from = null;
            panning = false;
            splitting = false;
            drawing = false;
            setCursor(tool == Tool.CROP ? cursorFor(handleAt(e.getPoint())) : cursorForTool());
        }

        private void releasePainting(MouseEvent e) {
            Dot at = dot(e.getPoint());
            double s = scale();
            switch (tool) {
                case BRUSH, ERASER -> {
                    if (stroke == null) return;
                    List<Dot> dots = List.copyOf(stroke);
                    lastDot = dots.getLast();
                    // The stroke stays on screen until the presenter shows the painted picture.
                    waiting.add(dots);
                    waitingSecond.add(strokeSecond);
                    stroke = null;
                    if (listener != null) listener.painted(dots, strokeSecond);
                }
                case SHAPES -> {
                    if (freehand != null) {
                        List<Dot> dots = List.copyOf(freehand);
                        freehand = null;
                        if (listener != null) listener.shapeChanged(dots, second);
                    }
                    shapeAtPress = null;
                }
                case MOVE -> {
                    if (listener != null) {
                        listener.layerDragged((int) Math.round((e.getX() - from.x) / s),
                                (int) Math.round((e.getY() - from.y) / s), true);
                    }
                }
                case TEXT -> {
                    if (!dragged && listener != null) listener.clicked(at, second);
                    textDragging = false;
                }
                case FILL, PICK -> {
                    if (!dragged && listener != null) listener.clicked(at, second);
                }
                default -> {
                }
            }
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (image == null) return;
            pointer = e.getPoint();
            if (tool == Tool.CROP) setCursor(cursorFor(handleAt(e.getPoint())));
            else if (tool == Tool.STRAIGHTEN) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            else if (tool == Tool.ADJUST && split >= 0 && Math.abs(e.getX() - splitX()) <= 10) {
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            } else if (tool == Tool.TEXT && text != null) {
                Dot at = dot(e.getPoint());
                setCursor(Cursor.getPredefinedCursor(TextRenderer.hits(text, at.x(), at.y())
                        ? Cursor.MOVE_CURSOR : Cursor.TEXT_CURSOR));
            } else setCursor(cursorForTool());
            if (tool == Tool.BRUSH || tool == Tool.ERASER) repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
            pointer = null;
            if (tool == Tool.BRUSH || tool == Tool.ERASER) repaint();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (image == null) return;
            int direction = Integer.signum(e.getWheelRotation());
            if (direction == 0) return;
            if (e.isControlDown() || !scrollable()) {
                zoomAt(direction < 0 ? ZoomSteps.in(scale()) : ZoomSteps.out(scale()), e.getPoint());
            } else {
                if (e.isShiftDown()) centreX += direction * 60 / scale();
                else centreY += direction * 60 / scale();
                clampCentre();
                repaint();
            }
        }

        private double splitX() {
            Rectangle2D shown = toScreen(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
            return shown.getX() + shown.getWidth() * split;
        }
    }

    /** The transform the straighten preview draws with; for tests. */
    AffineTransform straightenTransform() {
        Rectangle2D shown = toScreen(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        return AffineTransform.getRotateInstance(Math.toRadians(degrees), shown.getCenterX(), shown.getCenterY());
    }

    Rectangle crop() {
        return crop == null ? null : new Rectangle(crop);
    }

    Tool tool() {
        return tool;
    }
}
