package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.LineStyle;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PainterTest {
    private static BufferedImage white(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static BufferedImage clear(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void aHardStrokePaintsAlongTheLineAndNothingElse() {
        BufferedImage image = white(100, 40);
        BufferedImage painted = Painter.stroke(image, List.of(new Dot(10, 20), new Dot(90, 20)),
                new Brush(10, 100, 100), Color.RED, false);
        assertNotSame(image, painted);
        assertEquals(0xffffffff, image.getRGB(50, 20), "the original stays as it was");
        assertEquals(0xffff0000, painted.getRGB(50, 20));
        assertEquals(0xffff0000, painted.getRGB(10, 20));
        assertEquals(0xffffffff, painted.getRGB(50, 5));
        assertEquals(0xffffffff, painted.getRGB(97, 20));
    }

    @Test
    void overlappingDabsDoNotAddUp() {
        // One dab, or the same place painted many times: a half-opaque brush stays half opaque.
        BufferedImage one = Painter.stroke(clear(40, 40), List.of(new Dot(20, 20)), new Brush(10, 100, 50),
                Color.BLUE, false);
        BufferedImage many = Painter.stroke(clear(40, 40), List.of(new Dot(20, 20), new Dot(21, 20),
                new Dot(20, 20), new Dot(20, 21)), new Brush(10, 100, 50), Color.BLUE, false);
        int alpha = one.getRGB(20, 20) >>> 24;
        assertTrue(Math.abs(alpha - 128) <= 2, "alpha " + alpha);
        assertEquals(alpha, many.getRGB(20, 20) >>> 24);
    }

    @Test
    void aSoftBrushFadesTowardsItsEdge() {
        BufferedImage painted = Painter.stroke(clear(60, 60), List.of(new Dot(30, 30)), new Brush(40, 0, 100),
                Color.BLACK, false);
        int middle = painted.getRGB(30, 30) >>> 24, edge = painted.getRGB(30 + 17, 30) >>> 24;
        assertTrue(middle > 200, "middle " + middle);
        assertTrue(edge < middle / 2, "edge " + edge);
    }

    @Test
    void theEraserMakesALayerTransparent() {
        BufferedImage layer = clear(40, 40);
        var g = layer.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 40, 40);
        g.dispose();
        BufferedImage erased = Painter.stroke(layer, List.of(new Dot(20, 20)), new Brush(10, 100, 100),
                Color.WHITE, true);
        assertEquals(0, erased.getRGB(20, 20) >>> 24);
        assertEquals(0xff00ff00, erased.getRGB(2, 2));
    }

    @Test
    void theEraserPaintsTheSecondColourOnAPhoto() {
        BufferedImage photo = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage erased = Painter.stroke(photo, List.of(new Dot(20, 20)), new Brush(10, 100, 100),
                Color.WHITE, true);
        assertEquals(0xffffffff, erased.getRGB(20, 20));
    }

    @Test
    void aFilledRectangleHasItsOutlineInTheFirstColour() {
        BufferedImage painted = Painter.shape(white(100, 100), ShapeKind.RECTANGLE,
                List.of(new Dot(20, 20), new Dot(80, 80)), new LineStyle(6, LineStyle.Dash.SOLID), Color.BLACK,
                Color.YELLOW);
        assertEquals(0xff000000, painted.getRGB(20, 50));
        assertEquals(0xffffff00, painted.getRGB(50, 50));
        assertEquals(0xffffffff, painted.getRGB(5, 5));
    }

    @Test
    void anArrowHasAHeadAtItsEnd() {
        BufferedImage painted = Painter.shape(white(200, 100), ShapeKind.ARROW,
                List.of(new Dot(10, 50), new Dot(190, 50)), new LineStyle(4, LineStyle.Dash.SOLID), Color.RED, null);
        // The head is wider than the line near the tip, not at the tail.
        assertEquals(0xffff0000, painted.getRGB(175, 50 + 5));
        assertEquals(0xffffffff, painted.getRGB(20, 50 + 5));
    }

    @Test
    void fillStopsAtOtherColours() {
        BufferedImage image = white(60, 60);
        var g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(29, 0, 2, 60);
        g.dispose();
        BufferedImage filled = Painter.fill(image, 5, 5, Color.BLUE, 32);
        assertEquals(0xff0000ff, filled.getRGB(0, 59));
        assertEquals(0xff0000ff, filled.getRGB(28, 30));
        assertEquals(0xff000000, filled.getRGB(29, 30));
        assertEquals(0xffffffff, filled.getRGB(40, 30));
    }

    @Test
    void fillToleranceTakesSimilarColours() {
        BufferedImage image = white(20, 10);
        var g = image.createGraphics();
        g.setColor(new Color(240, 240, 240));
        g.fillRect(10, 0, 10, 10);
        g.dispose();
        assertEquals(0xfff0f0f0, Painter.fill(image, 1, 1, Color.RED, 0).getRGB(15, 5));
        assertEquals(0xffff0000, Painter.fill(image, 1, 1, Color.RED, 32).getRGB(15, 5));
    }

    @Test
    void fillOutsideChangesNothing() {
        BufferedImage image = white(10, 10);
        assertEquals(0xffffffff, Painter.fill(image, 50, 50, Color.RED, 32).getRGB(5, 5));
    }
}
