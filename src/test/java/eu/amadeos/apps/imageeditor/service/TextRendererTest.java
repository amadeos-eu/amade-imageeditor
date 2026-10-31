package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.TextBox;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRendererTest {
    private static TextBox box(TextBox.Effect effect, double rotation) {
        return new TextBox("Lake\nTwo lines", "SansSerif", 40, true, false, TextBox.Align.CENTRE, Color.WHITE,
                effect, Color.BLACK, 300, 200, rotation);
    }

    @Test
    void theTextLiesAroundItsCentre() {
        TextBox box = box(TextBox.Effect.NONE, 0);
        // The block of lines is placed exactly by its middle ...
        java.awt.geom.Point2D[] frame = TextRenderer.frame(box);
        assertEquals(300, (frame[0].getX() + frame[2].getX()) / 2, 1e-9);
        assertEquals(200, (frame[0].getY() + frame[2].getY()) / 2, 1e-9);
        // ... and the drawn letters lie around it; how far their ink is off the block's middle depends on the font.
        TextRenderer.Rendered r = TextRenderer.render(box);
        double cx = r.x() + r.image().getWidth() / 2.0, cy = r.y() + r.image().getHeight() / 2.0;
        assertTrue(Math.abs(cx - 300) < box.size() / 4, "x " + cx);
        assertTrue(Math.abs(cy - 200) < box.size() / 4, "y " + cy);
        assertTrue(r.image().getHeight() > 2 * box.size(), "two lines");
    }

    @Test
    void aBoxCoversTheWholeBlock() {
        TextRenderer.Rendered plain = TextRenderer.render(box(TextBox.Effect.NONE, 0));
        TextRenderer.Rendered boxed = TextRenderer.render(box(TextBox.Effect.BOX, 0));
        assertTrue(boxed.image().getWidth() > plain.image().getWidth());
        // The box is drawn behind the letters: its left edge is opaque black.
        int corner = boxed.image().getRGB(3, boxed.image().getHeight() / 2);
        assertTrue((corner >>> 24) > 200, Integer.toHexString(corner));
    }

    @Test
    void turnedTextIsTallerThanWide() {
        TextBox one = new TextBox("A long line of text", "SansSerif", 30, false, false, TextBox.Align.LEFT,
                Color.RED, TextBox.Effect.SHADOW, Color.BLACK, 200, 200, 90);
        TextRenderer.Rendered r = TextRenderer.render(one);
        assertTrue(r.image().getHeight() > r.image().getWidth());
    }

    @Test
    void hitsFollowsTheTurnedFrame() {
        TextBox one = new TextBox("A long line of text", "SansSerif", 30, false, false, TextBox.Align.LEFT,
                Color.RED, TextBox.Effect.NONE, Color.BLACK, 200, 200, 90);
        assertTrue(TextRenderer.hits(one, 200, 300));
        assertFalse(TextRenderer.hits(one, 330, 200));
        assertTrue(TextRenderer.hits(one.withRotation(0), 330, 200));
    }
}
