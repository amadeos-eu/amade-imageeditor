package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropBoxTest {
    private static final int W = 4000, H = 2667;

    private static void assertInside(Rectangle r) {
        assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.width <= W && r.y + r.height <= H, "outside: " + r);
    }

    private static void assertRatio(double ratio, Rectangle r) {
        assertEquals(ratio, (double) r.width / r.height, 0.01, r.toString());
    }

    @Test
    void centredFrameKeepsTheRatioAndFillsOneSide() {
        Rectangle r = CropBox.centred(W, H, 1, 1);
        assertEquals(new Rectangle((W - H) / 2, 0, H, H), r);
        assertEquals(new Rectangle(0, 0, W, H), CropBox.centred(W, H, 0, 1));
    }

    @Test
    void centredFrameWithFraction() {
        Rectangle r = CropBox.centred(1000, 1000, 0, 0.5);
        assertEquals(new Rectangle(250, 250, 500, 500), r);
    }

    @Test
    void withRatioKeepsTheCentreAndStaysInside() {
        Rectangle r = CropBox.withRatio(new Rectangle(0, 0, W, H), 16 / 9.0, W, H);
        assertRatio(16 / 9.0, r);
        assertInside(r);
        assertEquals(W / 2.0, r.getCenterX(), 1);
        assertEquals(H / 2.0, r.getCenterY(), 1);
    }

    @Test
    void withRatioNearTheEdgeIsPushedInside() {
        Rectangle r = CropBox.withRatio(new Rectangle(3500, 0, 500, 500), 3, W, H);
        assertRatio(3, r);
        assertInside(r);
    }

    @Test
    void freeDragOfTheSouthEastCornerChangesOnlyThatCorner() {
        Rectangle r = CropBox.drag(new Rectangle(100, 100, 1000, 800), CropBox.Handle.SOUTH_EAST, 50, -30, 0, W, H);
        assertEquals(new Rectangle(100, 100, 1050, 770), r);
    }

    @Test
    void freeDragOfTheWestEdgeMovesTheLeftSide() {
        Rectangle r = CropBox.drag(new Rectangle(100, 100, 1000, 800), CropBox.Handle.WEST, -60, 999, 0, W, H);
        assertEquals(new Rectangle(40, 100, 1060, 800), r);
    }

    @Test
    void cornerDragKeepsTheRatioAndTheOppositeCorner() {
        Rectangle start = new Rectangle(100, 100, 1500, 1000);
        Rectangle r = CropBox.drag(start, CropBox.Handle.SOUTH_EAST, 300, 10, 1.5, W, H);
        assertRatio(1.5, r);
        assertEquals(100, r.x);
        assertEquals(100, r.y);
        assertEquals(1800, r.width);
    }

    @Test
    void northWestCornerKeepsTheSouthEastCorner() {
        Rectangle start = new Rectangle(1000, 1000, 1500, 1000);
        Rectangle r = CropBox.drag(start, CropBox.Handle.NORTH_WEST, -300, -50, 1.5, W, H);
        assertRatio(1.5, r);
        assertEquals(2500, r.x + r.width);
        assertEquals(2000, r.y + r.height);
    }

    @Test
    void edgeDragWithRatioGrowsAroundTheMiddle() {
        Rectangle start = new Rectangle(1000, 1000, 1000, 1000);
        Rectangle r = CropBox.drag(start, CropBox.Handle.EAST, 200, 0, 1, W, H);
        assertEquals(1200, r.width);
        assertEquals(1200, r.height);
        assertEquals(1000, r.x);
        assertEquals(1500, r.getCenterY(), 1);
    }

    @Test
    void ratioDragStopsAtThePictureEdge() {
        Rectangle start = new Rectangle(2000, 1500, 1000, 1000);
        Rectangle r = CropBox.drag(start, CropBox.Handle.SOUTH_EAST, 5000, 5000, 1, W, H);
        assertRatio(1, r);
        assertInside(r);
        assertEquals(H, r.y + r.height);
    }

    @Test
    void framesNeverGetSmallerThanTheMinimum() {
        Rectangle r = CropBox.drag(new Rectangle(100, 100, 50, 50), CropBox.Handle.SOUTH_EAST, -500, -500, 0, W, H);
        assertTrue(r.width >= CropBox.MIN && r.height >= CropBox.MIN, r.toString());
    }

    @Test
    void moveStaysInside() {
        Rectangle r = CropBox.drag(new Rectangle(100, 100, 1000, 800), CropBox.Handle.MOVE, -500, 99999, 0, W, H);
        assertEquals(new Rectangle(0, H - 800, 1000, 800), r);
    }

    @Test
    void drawingInAnyDirection() {
        assertEquals(new Rectangle(100, 200, 300, 400), CropBox.draw(100, 200, 400, 600, 0, W, H));
        assertEquals(new Rectangle(100, 200, 300, 400), CropBox.draw(400, 600, 100, 200, 0, W, H));
        Rectangle r = CropBox.draw(1000, 1000, 1300, 1100, 1, W, H);
        assertEquals(new Rectangle(1000, 1000, 300, 300), r);
    }

    @Test
    void drawingWithRatioStaysInside() {
        Rectangle r = CropBox.draw(3900, 2600, 4500, 2700, 1.5, W, H);
        assertInside(r);
        assertRatio(1.5, r);
    }

    @Test
    void typedWidthPullsTheHeightAlong() {
        Rectangle r = CropBox.typed(new Rectangle(0, 0, 1500, 1000), CropBox.Field.WIDTH, 900, 1.5, W, H);
        assertEquals(new Rectangle(0, 0, 900, 600), r);
    }

    @Test
    void typedHeightWithoutRatio() {
        Rectangle r = CropBox.typed(new Rectangle(0, 0, 1500, 1000), CropBox.Field.HEIGHT, 700, 0, W, H);
        assertEquals(new Rectangle(0, 0, 1500, 700), r);
    }

    @Test
    void typedPositionIsClamped() {
        Rectangle r = CropBox.typed(new Rectangle(0, 0, 1500, 1000), CropBox.Field.LEFT, 3900, 0, W, H);
        assertEquals(W - 1500, r.x);
        r = CropBox.typed(new Rectangle(0, 0, 1500, 1000), CropBox.Field.TOP, -40, 0, W, H);
        assertEquals(0, r.y);
    }

    @Test
    void typedTooLargeWithRatioShrinksToFit() {
        Rectangle r = CropBox.typed(new Rectangle(0, 0, 100, 100), CropBox.Field.WIDTH, 9000, 1, W, H);
        assertEquals(new Rectangle(0, 0, H, H), r);
    }

    @Test
    void clampKeepsAtLeastOnePixel() {
        assertEquals(new Rectangle(9, 9, 1, 1), CropBox.clamp(new Rectangle(50, 50, 0, -3), 10, 10));
    }
}
