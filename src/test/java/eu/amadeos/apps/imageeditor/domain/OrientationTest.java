package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationTest {
    @Test
    void exifValuesRoundTrip() {
        for (int value = 1; value <= 8; value++) assertEquals(value, Orientation.ofExif(value).exif());
        assertEquals(Orientation.NORMAL, Orientation.ofExif(0));
        assertEquals(Orientation.NORMAL, Orientation.ofExif(42));
    }

    @Test
    void fourTurnsRightAreNoTurn() {
        Orientation o = Orientation.NORMAL;
        for (int i = 0; i < 4; i++) o = o.rotateRight();
        assertEquals(Orientation.NORMAL, o);
        assertEquals(Orientation.ROTATE_90, Orientation.NORMAL.rotateRight());
        assertEquals(Orientation.ROTATE_270, Orientation.NORMAL.rotateLeft());
        assertEquals(Orientation.ROTATE_180, Orientation.ROTATE_90.rotateRight());
    }

    @Test
    void flippingTwiceIsNoFlip() {
        assertEquals(Orientation.NORMAL, Orientation.NORMAL.flipHorizontally().flipHorizontally());
        assertEquals(Orientation.NORMAL, Orientation.NORMAL.flipVertically().flipVertically());
        // Both flips are a half turn.
        assertEquals(Orientation.ROTATE_180, Orientation.NORMAL.flipHorizontally().flipVertically());
    }

    @Test
    void exifSixTurnedLeftIsNormal() {
        // A phone held upright writes 6; turning that left on screen cancels it.
        assertEquals(Orientation.NORMAL, Orientation.ofExif(6).rotateLeft());
        assertEquals(Orientation.ROTATE_180, Orientation.ofExif(6).rotateRight());
    }

    @Test
    void transposeAndTransverseAreMirroredQuarterTurns() {
        assertEquals(Orientation.TRANSPOSE, Orientation.MIRROR.rotateLeft());
        assertEquals(Orientation.TRANSVERSE, Orientation.MIRROR.rotateRight());
        assertTrue(Orientation.TRANSPOSE.swapsSides());
        assertFalse(Orientation.FLIP.swapsSides());
    }

    @Test
    void everyOrientationHasAnInverse() {
        for (Orientation o : Orientation.values()) assertEquals(Orientation.NORMAL, o.then(o.inverse()));
    }

    @Test
    void jpegtranOptions() {
        assertEquals(List.of(), Orientation.NORMAL.jpegtranOptions());
        assertEquals(List.of("-rotate", "90"), Orientation.ROTATE_90.jpegtranOptions());
        assertEquals(List.of("-flip", "horizontal"), Orientation.MIRROR.jpegtranOptions());
        assertEquals(List.of("-transverse"), Orientation.TRANSVERSE.jpegtranOptions());
    }
}
