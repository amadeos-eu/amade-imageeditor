package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.apps.imageeditor.domain.ZoomSteps;
import eu.amadeos.apps.imageeditor.service.ImageOperations;

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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;

/**
 * Shows the picture being edited on a dark background, fitted or zoomed, with what the open tool draws over it:
 * the crop frame with its handles, the turned picture and its grid while straightening, or the adjusted picture,
 * optionally split against the original. The canvas only shows and reports; the presenter decides.
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
        if (tool != Tool.ADJUST) {
            look = null;
            split = -1;
            looked = null;
        }
        setCursor(Cursor.getDefaultCursor());
        repaint();
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
    static double levelling(double current, double x1, double y1, double x2, double y2) {
        double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
        // A line has no direction: bring it into -90..90, then steep lines count as vertical ones.
        if (angle > 90) angle -= 180;
        if (angle < -90) angle += 180;
        if (angle > 45) angle -= 90;
        else if (angle < -45) angle += 90;
        return Math.clamp(current - angle, -45, 45);
    }

    // ---------------------------------------------------------------- mouse

    private final class Mouse extends MouseAdapter {
        private Point from;
        private Rectangle cropAtPress;
        private CropBox.Handle handle;
        private boolean drawing;
        private boolean panning;
        private boolean splitting;

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (image == null || !SwingUtilities.isLeftMouseButton(e)) return;
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

        @Override
        public void mouseDragged(MouseEvent e) {
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
            from = null;
            panning = false;
            splitting = false;
            drawing = false;
            setCursor(tool == Tool.CROP ? cursorFor(handleAt(e.getPoint())) : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (image == null) return;
            if (tool == Tool.CROP) setCursor(cursorFor(handleAt(e.getPoint())));
            else if (tool == Tool.STRAIGHTEN) setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            else if (tool == Tool.ADJUST && split >= 0 && Math.abs(e.getX() - splitX()) <= 10) {
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            } else setCursor(Cursor.getDefaultCursor());
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
