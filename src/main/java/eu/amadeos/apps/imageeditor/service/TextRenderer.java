package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.TextBox;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Makes the pixels of a text layer from its {@link TextBox}: the letters as outlines, laid out line by line,
 * with the effect behind them, turned by the box's rotation and placed by its middle.
 */
public final class TextRenderer {
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private TextRenderer() {
    }

    /** The pixels and where their top left corner lies on the picture. */
    public record Rendered(BufferedImage image, int x, int y) {
    }

    // --8<-- [start:render]
    public static Rendered render(TextBox box) {
        Shape letters = letters(box);
        Rectangle2D block = block(box, letters);
        Shape effect = switch (box.effect()) {
            case BOX -> {
                double pad = box.size() * 0.25;
                yield new RoundRectangle2D.Double(block.getX() - pad, block.getY() - pad,
                        block.getWidth() + 2 * pad, block.getHeight() + 2 * pad, pad, pad);
            }
            case SHADOW -> AffineTransform.getTranslateInstance(shadowOffset(box), shadowOffset(box))
                    .createTransformedShape(letters);
            case OUTLINE -> new BasicStroke(outlineWidth(box) * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    .createStrokedShape(letters);
            case NONE -> null;
        };
        AffineTransform turn = AffineTransform.getRotateInstance(Math.toRadians(box.rotation()),
                block.getCenterX(), block.getCenterY());
        Area all = new Area(turn.createTransformedShape(letters));
        if (effect != null) all.add(new Area(turn.createTransformedShape(effect)));
        Rectangle2D extent = all.getBounds2D();
        // Place the block's middle on the box's centre.
        double ox = box.centreX() - block.getCenterX(), oy = box.centreY() - block.getCenterY();
        int x = (int) Math.floor(extent.getX() + ox) - 1, y = (int) Math.floor(extent.getY() + oy) - 1;
        int w = (int) Math.ceil(extent.getMaxX() + ox) + 1 - x, h = (int) Math.ceil(extent.getMaxY() + oy) + 1 - y;
        BufferedImage image = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(ox - x, oy - y);
        g.transform(turn);
        if (effect != null) {
            Color c = box.effectColour();
            if (box.effect() == TextBox.Effect.SHADOW) {
                c = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(c.getAlpha(), 150));
            }
            g.setColor(c);
            g.fill(effect);
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(box.colour());
        g.fill(letters);
        g.dispose();
        return new Rendered(image, x, y);
    }
    // --8<-- [end:render]

    /** The letters of all lines, each line placed below the last and aligned in the block. */
    static Shape letters(TextBox box) {
        Font font = font(box);
        List<String> lines = box.text().lines().toList();
        if (lines.isEmpty()) lines = List.of("");
        double lineHeight = lineHeight(font);
        double widest = 0;
        double[] widths = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            widths[i] = lines.get(i).isEmpty() ? 0 : new TextLayout(lines.get(i), font, FRC).getAdvance();
            widest = Math.max(widest, widths[i]);
        }
        Area all = new Area();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            TextLayout layout = new TextLayout(lines.get(i), font, FRC);
            double left = switch (box.align()) {
                case LEFT -> 0;
                case CENTRE -> (widest - widths[i]) / 2;
                case RIGHT -> widest - widths[i];
            };
            double baseline = i * lineHeight + layout.getAscent();
            all.add(new Area(layout.getOutline(AffineTransform.getTranslateInstance(left, baseline))));
        }
        return all;
    }

    /** The block the lines fill, from the top of the first line to the bottom of the last. */
    static Rectangle2D block(TextBox box, Shape letters) {
        Font font = font(box);
        List<String> lines = box.text().lines().toList();
        int count = Math.max(1, lines.size());
        double widest = letters.getBounds2D().isEmpty() ? 0 : letters.getBounds2D().getMaxX();
        for (String line : lines) {
            if (!line.isEmpty()) widest = Math.max(widest, new TextLayout(line, font, FRC).getAdvance());
        }
        return new Rectangle2D.Double(0, 0, Math.max(1, widest), count * lineHeight(font));
    }

    public static Font font(TextBox box) {
        int style = (box.bold() ? Font.BOLD : 0) | (box.italic() ? Font.ITALIC : 0);
        return new Font(box.font(), style, 1).deriveFont(style, box.size());
    }

    private static double lineHeight(Font font) {
        var metrics = font.getLineMetrics("Ág", FRC);
        return metrics.getAscent() + metrics.getDescent() + metrics.getLeading();
    }

    static double shadowOffset(TextBox box) {
        return Math.max(1, box.size() / 16.0);
    }

    static float outlineWidth(TextBox box) {
        return Math.max(1, box.size() / 14f);
    }

    /**
     * The four corners of the text's block on the picture, turned with the text, for the frame the Text tool
     * draws around it.
     */
    public static Point2D[] frame(TextBox box) {
        Rectangle2D block = block(box, letters(box));
        double hw = block.getWidth() / 2, hh = block.getHeight() / 2;
        AffineTransform turn = AffineTransform.getRotateInstance(Math.toRadians(box.rotation()),
                box.centreX(), box.centreY());
        Point2D[] corners = {new Point2D.Double(box.centreX() - hw, box.centreY() - hh),
                new Point2D.Double(box.centreX() + hw, box.centreY() - hh),
                new Point2D.Double(box.centreX() + hw, box.centreY() + hh),
                new Point2D.Double(box.centreX() - hw, box.centreY() + hh)};
        for (int i = 0; i < corners.length; i++) corners[i] = turn.transform(corners[i], null);
        return corners;
    }

    /** True if a picture point lies on the text's block, turned with it. */
    public static boolean hits(TextBox box, double x, double y) {
        Rectangle2D block = block(box, letters(box));
        AffineTransform back = AffineTransform.getRotateInstance(-Math.toRadians(box.rotation()),
                box.centreX(), box.centreY());
        Point2D p = back.transform(new Point2D.Double(x, y), null);
        double pad = box.size() * 0.25;
        return Math.abs(p.getX() - box.centreX()) <= block.getWidth() / 2 + pad
                && Math.abs(p.getY() - box.centreY()) <= block.getHeight() / 2 + pad;
    }

    static Rectangle bounds(Rendered r) {
        return new Rectangle(r.x(), r.y(), r.image().getWidth(), r.image().getHeight());
    }
}
