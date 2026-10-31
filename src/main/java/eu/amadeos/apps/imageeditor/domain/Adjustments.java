package eu.amadeos.apps.imageeditor.domain;

/**
 * The sliders of the Adjust panel, each from -100 to +100 (0 changes nothing), and the grayscale switch.
 */
public record Adjustments(int brightness, int contrast, int saturation, int warmth, boolean grayscale) {
    public static final Adjustments NONE = new Adjustments(0, 0, 0, 0, false);
    public static final int MIN = -100;
    public static final int MAX = 100;

    public Adjustments {
        brightness = Math.clamp(brightness, MIN, MAX);
        contrast = Math.clamp(contrast, MIN, MAX);
        saturation = Math.clamp(saturation, MIN, MAX);
        warmth = Math.clamp(warmth, MIN, MAX);
    }

    public boolean neutral() {
        return equals(NONE);
    }

    public Adjustments withBrightness(int value) {
        return new Adjustments(value, contrast, saturation, warmth, grayscale);
    }

    public Adjustments withContrast(int value) {
        return new Adjustments(brightness, value, saturation, warmth, grayscale);
    }

    public Adjustments withSaturation(int value) {
        return new Adjustments(brightness, contrast, value, warmth, grayscale);
    }

    public Adjustments withWarmth(int value) {
        return new Adjustments(brightness, contrast, saturation, value, grayscale);
    }

    public Adjustments withGrayscale(boolean value) {
        return new Adjustments(brightness, contrast, saturation, warmth, value);
    }
}
