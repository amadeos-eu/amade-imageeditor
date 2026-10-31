package eu.amadeos.apps.imageeditor.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallDomainTest {
    @Test
    void adjustmentsAreClamped() {
        Adjustments a = new Adjustments(500, -500, 0, 0, false);
        assertEquals(100, a.brightness());
        assertEquals(-100, a.contrast());
    }

    @Test
    void neutralAdjustments() {
        assertTrue(Adjustments.NONE.neutral());
        assertFalse(Adjustments.NONE.withGrayscale(true).neutral());
        assertFalse(Adjustments.NONE.withWarmth(1).neutral());
    }

    @Test
    void withersChangeOneValue() {
        Adjustments a = Adjustments.NONE.withBrightness(10).withContrast(20).withSaturation(-30).withWarmth(40);
        assertEquals(new Adjustments(10, 20, -30, 40, false), a);
    }

    @Test
    void saveOptionDefaults() {
        assertEquals(90, SaveOptions.defaults(ImageFormat.JPEG).quality());
        assertEquals(85, SaveOptions.defaults(ImageFormat.WEBP).quality());
        SaveOptions d = SaveOptions.defaults(ImageFormat.PNG);
        assertTrue(d.keepCamera() && d.keepLocation() && d.keepDescription());
        assertFalse(d.lossless());
    }

    @Test
    void saveOptionsNeedAWritableFormat() {
        assertThrows(IllegalArgumentException.class, () -> SaveOptions.defaults(ImageFormat.PSD));
        assertEquals(100, new SaveOptions(ImageFormat.JPEG, 300, false, false, false, false).quality());
    }

    @Test
    void keepsMetadata() {
        assertFalse(new SaveOptions(ImageFormat.JPEG, 90, false, false, true, false).keepsMetadata());
        assertTrue(new SaveOptions(ImageFormat.JPEG, 90, false, false, false, true).keepsMetadata());
        assertEquals(ImageFormat.TIFF, SaveOptions.defaults(ImageFormat.JPEG).withFormat(ImageFormat.TIFF).format());
    }

    @Test
    void zoomSteps() {
        assertEquals(2.0, ZoomSteps.in(1.5));
        assertEquals(1.0, ZoomSteps.out(1.5));
        assertEquals(0.5, ZoomSteps.fit(2000, 1000, 1000, 1000, false));
        assertEquals(1.0, ZoomSteps.fit(200, 100, 1000, 1000, false));
    }
}
