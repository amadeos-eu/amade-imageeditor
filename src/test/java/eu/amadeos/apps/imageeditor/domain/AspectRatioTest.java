package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectRatioTest {
    @Test
    void freeHasNoValue() {
        assertTrue(AspectRatio.FREE.free());
        assertEquals(0, AspectRatio.FREE.value(400, 300, false));
    }

    @Test
    void presetsAreLandscapeAndTurnForPortrait() {
        AspectRatio r = AspectRatio.of(3, 2);
        assertEquals("3:2", r.key());
        assertEquals(1.5, r.value(100, 100, false), 1e-9);
        assertEquals(2 / 3.0, r.value(100, 100, true), 1e-9);
        assertEquals(1.5, AspectRatio.of(2, 3).value(1, 1, false), 1e-9);
    }

    @Test
    void squareStaysSquare() {
        assertEquals(1, AspectRatio.SQUARE.value(10, 20, true), 1e-9);
    }

    @Test
    void originalFollowsThePicture() {
        assertEquals(1.5, AspectRatio.ORIGINAL.value(3000, 2000, false), 1e-9);
        assertEquals(2 / 3.0, AspectRatio.ORIGINAL.value(3000, 2000, true), 1e-9);
        assertEquals(2 / 3.0, AspectRatio.ORIGINAL.value(2000, 3000, true), 1e-9);
        assertFalse(AspectRatio.ORIGINAL.free());
    }

    @Test
    void presetsInOrder() {
        assertEquals(7, AspectRatio.PRESETS.size());
        assertEquals(AspectRatio.FREE, AspectRatio.PRESETS.getFirst());
        assertTrue(AspectRatio.PRESETS.contains(AspectRatio.of(16, 9)));
    }

    @Test
    void badRatios() {
        assertThrows(IllegalArgumentException.class, () -> AspectRatio.of(0, 2));
        assertThrows(IllegalArgumentException.class, () -> AspectRatio.of(3, -1));
    }
}
