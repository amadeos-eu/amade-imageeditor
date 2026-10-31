package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageOperationsTest {
    private static final int RED = 0xff0000, GREEN = 0x00ff00, BLUE = 0x0000ff, WHITE = 0xffffff;

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xffffff;
    }

    private static void assertNear(int expected, int actual, String what) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int e = expected >> shift & 0xff, a = actual >> shift & 0xff;
            assertTrue(Math.abs(e - a) <= 8, what + ": " + Integer.toHexString(actual) + " is not near "
                    + Integer.toHexString(expected));
        }
    }

    @Test
    void cropCutsOutTheRectangle() {
        BufferedImage q = TestPictures.quarters(40, 20);
        BufferedImage c = ImageOperations.crop(q, new Rectangle(20, 10, 20, 10));
        assertEquals(20, c.getWidth());
        assertEquals(10, c.getHeight());
        assertEquals(WHITE, rgb(c, 0, 0));
        assertEquals(WHITE, rgb(c, 19, 9));
    }

    @Test
    void cropIsLimitedToThePicture() {
        BufferedImage c = ImageOperations.crop(TestPictures.quarters(40, 20), new Rectangle(30, 15, 50, 50));
        assertEquals(10, c.getWidth());
        assertEquals(5, c.getHeight());
        assertThrows(IllegalArgumentException.class,
                () -> ImageOperations.crop(TestPictures.quarters(4, 4), new Rectangle(10, 10, 2, 2)));
    }

    @Test
    void quarterTurnsMoveTheCorners() {
        BufferedImage q = TestPictures.quarters(40, 20);
        BufferedImage right = ImageOperations.turn(q, Orientation.ROTATE_90);
        assertEquals(20, right.getWidth());
        assertEquals(40, right.getHeight());
        // Turned clockwise, the bottom-left (blue) comes to the top left.
        assertEquals(BLUE, rgb(right, 0, 0));
        assertEquals(RED, rgb(right, 19, 0));
        assertEquals(WHITE, rgb(right, 0, 39));
        BufferedImage left = ImageOperations.turn(q, Orientation.ROTATE_270);
        assertEquals(GREEN, rgb(left, 0, 0));
        BufferedImage half = ImageOperations.turn(q, Orientation.ROTATE_180);
        assertEquals(WHITE, rgb(half, 0, 0));
    }

    @Test
    void flips() {
        BufferedImage q = TestPictures.quarters(40, 20);
        assertEquals(GREEN, rgb(ImageOperations.turn(q, Orientation.MIRROR), 0, 0));
        assertEquals(BLUE, rgb(ImageOperations.turn(q, Orientation.FLIP), 0, 0));
    }

    @Test
    void turnsArePixelExact() {
        BufferedImage q = TestPictures.quarters(41, 23);
        q.setRGB(0, 0, 0x123456);
        BufferedImage back = q;
        for (int i = 0; i < 4; i++) back = ImageOperations.turn(back, Orientation.ROTATE_90);
        for (int y = 0; y < q.getHeight(); y++) {
            for (int x = 0; x < q.getWidth(); x++) assertEquals(q.getRGB(x, y), back.getRGB(x, y), x + "," + y);
        }
    }

    @Test
    void inscribedRectangleShrinksWithTheAngle() {
        assertEquals(new Rectangle(0, 0, 4000, 3000), ImageOperations.inscribed(4000, 3000, 0));
        Rectangle r = ImageOperations.inscribed(4000, 3000, 5);
        assertTrue(r.width < 4000 && r.width > 3300, r.toString());
        assertEquals(4000 / 3000.0, (double) r.width / r.height, 0.01);
        assertEquals(ImageOperations.inscribed(4000, 3000, -5), r);
        assertEquals(2000, r.getCenterX(), 1);
    }

    @Test
    void straightenWithCropHasNoEmptyCorners() {
        BufferedImage image = TestPictures.filled(400, 300, 0x808080);
        BufferedImage s = ImageOperations.straighten(image, 10, true);
        assertFalse(ImageOperations.hasAlpha(s));
        assertTrue(s.getWidth() < 400);
        for (int[] p : new int[][]{{0, 0}, {s.getWidth() - 1, 0}, {0, s.getHeight() - 1},
                {s.getWidth() - 1, s.getHeight() - 1}}) {
            int c = rgb(s, p[0], p[1]);
            assertTrue(Math.abs((c >> 16) - 0x80) < 12, Integer.toHexString(c));
        }
    }

    @Test
    void straightenWithoutCropKeepsSizeAndLeavesTransparentCorners() {
        BufferedImage s = ImageOperations.straighten(TestPictures.filled(400, 300, 0x808080), 10, false);
        assertEquals(400, s.getWidth());
        assertEquals(300, s.getHeight());
        assertTrue(ImageOperations.hasAlpha(s));
        assertEquals(0, s.getRGB(0, 0) >>> 24);
        assertEquals(255, s.getRGB(200, 150) >>> 24);
    }

    @Test
    void straightenByZeroIsACopy() {
        BufferedImage q = TestPictures.quarters(10, 10);
        BufferedImage s = ImageOperations.straighten(q, 0, true);
        assertNotSame(q, s);
        assertEquals(q.getRGB(9, 9), s.getRGB(9, 9));
    }

    @Test
    void resizeInEveryQuality() {
        BufferedImage q = TestPictures.quarters(400, 200);
        for (ResizeQuality quality : ResizeQuality.values()) {
            BufferedImage r = ImageOperations.resize(q, 100, 50, quality);
            assertEquals(100, r.getWidth());
            assertEquals(50, r.getHeight());
            assertNear(RED, rgb(r, 10, 10), quality.name());
            assertNear(WHITE, rgb(r, 90, 40), quality.name());
        }
        BufferedImage up = ImageOperations.resize(q, 800, 400, ResizeQuality.PIXELS);
        assertEquals(800, up.getWidth());
        assertEquals(BLUE, rgb(up, 0, 399));
    }

    @Test
    void neutralAdjustmentsChangeNothing() {
        BufferedImage q = TestPictures.quarters(20, 20);
        BufferedImage a = ImageOperations.adjust(q, Adjustments.NONE);
        assertNotSame(q, a);
        assertEquals(q.getRGB(5, 5), a.getRGB(5, 5));
    }

    @Test
    void brightnessLightensAndDarkens() {
        BufferedImage gray = TestPictures.filled(4, 4, 0x808080);
        int lighter = rgb(ImageOperations.adjust(gray, Adjustments.NONE.withBrightness(50)), 0, 0) & 0xff;
        int darker = rgb(ImageOperations.adjust(gray, Adjustments.NONE.withBrightness(-50)), 0, 0) & 0xff;
        assertTrue(lighter > 0x80 + 50, String.valueOf(lighter));
        assertTrue(darker < 0x80 - 50, String.valueOf(darker));
        assertEquals(WHITE, rgb(ImageOperations.adjust(gray, Adjustments.NONE.withBrightness(100)), 0, 0));
    }

    @Test
    void contrastSpreadsAndFlattens() {
        BufferedImage dark = TestPictures.filled(2, 2, 0x404040);
        assertTrue((rgb(ImageOperations.adjust(dark, Adjustments.NONE.withContrast(50)), 0, 0) & 0xff) < 0x40);
        assertEquals(0x80, rgb(ImageOperations.adjust(dark, Adjustments.NONE.withContrast(-100)), 0, 0) & 0xff, 1);
    }

    @Test
    void grayscaleAndSaturation() {
        BufferedImage red = TestPictures.filled(2, 2, RED);
        int g = rgb(ImageOperations.adjust(red, Adjustments.NONE.withGrayscale(true)), 0, 0);
        assertEquals(g >> 16 & 0xff, g & 0xff);
        assertEquals(g >> 16 & 0xff, g >> 8 & 0xff);
        assertEquals(76, g & 0xff, 1);
        int half = rgb(ImageOperations.adjust(red, Adjustments.NONE.withSaturation(-50)), 0, 0);
        assertTrue((half & 0xff) > 0 && (half >> 16 & 0xff) < 255, Integer.toHexString(half));
    }

    @Test
    void warmthMovesRedAgainstBlue() {
        BufferedImage gray = TestPictures.filled(2, 2, 0x808080);
        int warm = rgb(ImageOperations.adjust(gray, Adjustments.NONE.withWarmth(100)), 0, 0);
        assertTrue((warm >> 16 & 0xff) > (warm & 0xff));
        assertEquals(0x80, warm >> 8 & 0xff);
    }

    @Test
    void adjustKeepsTransparency() {
        BufferedImage clear = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        clear.setRGB(0, 0, 0x40808080);
        BufferedImage a = ImageOperations.adjust(clear, Adjustments.NONE.withBrightness(20));
        assertEquals(0x40, a.getRGB(0, 0) >>> 24);
    }

    @Test
    void autoLevelsStretchesAFlatPicture() {
        BufferedImage image = new BufferedImage(100, 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            int v = 100 + x / 2;
            image.setRGB(x, 0, v << 16 | v << 8 | v);
        }
        BufferedImage stretched = ImageOperations.autoLevels(image);
        assertTrue((rgb(stretched, 0, 0) & 0xff) < 10);
        assertTrue((rgb(stretched, 99, 0) & 0xff) > 245);
    }

    @Test
    void autoLevelsLeavesAPlainColour() {
        BufferedImage flat = TestPictures.filled(10, 10, 0x336699);
        assertEquals(0x336699, rgb(ImageOperations.autoLevels(flat), 3, 3));
    }

    @Test
    void flattenPutsTransparencyOnWhite() {
        BufferedImage clear = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage flat = ImageOperations.flatten(clear);
        assertFalse(ImageOperations.hasAlpha(flat));
        assertEquals(WHITE, rgb(flat, 0, 0));
        BufferedImage rgb = TestPictures.filled(2, 2, RED);
        assertEquals(rgb, ImageOperations.flatten(rgb));
    }

    @Test
    void normalizeConvertsOtherTypes() {
        BufferedImage gray = new BufferedImage(3, 3, BufferedImage.TYPE_BYTE_GRAY);
        assertEquals(BufferedImage.TYPE_INT_RGB, ImageOperations.normalize(gray).getType());
        BufferedImage rgb = TestPictures.filled(2, 2, RED);
        assertEquals(rgb, ImageOperations.normalize(rgb));
    }

    @Test
    void applyDispatchesEveryEdit() {
        BufferedImage q = TestPictures.quarters(40, 20);
        assertEquals(10, ImageOperations.apply(q, new Edit.Crop(new Rectangle(0, 0, 10, 10))).getWidth());
        assertEquals(20, ImageOperations.apply(q, new Edit.Turn(Orientation.ROTATE_90)).getWidth());
        assertEquals(40, ImageOperations.apply(q, new Edit.Straighten(3, false)).getWidth());
        assertEquals(8, ImageOperations.apply(q, new Edit.Resize(8, 4, ResizeQuality.FAST)).getWidth());
        assertEquals(40, ImageOperations.apply(q, new Edit.Adjust(Adjustments.NONE.withContrast(5))).getWidth());
        assertEquals(40, ImageOperations.apply(q, new Edit.AutoLevels()).getWidth());
    }
}
