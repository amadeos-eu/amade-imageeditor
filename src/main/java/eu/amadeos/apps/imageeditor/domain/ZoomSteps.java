package eu.amadeos.apps.imageeditor.domain;

import java.util.List;

/** The zoom factors that Zoom In and Zoom Out step through, and the factor that fits a picture into a view. */
public final class ZoomSteps {
    public static final List<Double> STEPS = List.of(1 / 16.0, 1 / 12.0, 1 / 8.0, 1 / 6.0, 1 / 4.0, 1 / 3.0, 1 / 2.0,
            2 / 3.0, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0);

    public static final double MIN = STEPS.getFirst();
    public static final double MAX = STEPS.getLast();

    private ZoomSteps() {
    }

    /** The next larger step; the largest stays. */
    public static double in(double factor) {
        for (double step : STEPS) {
            if (step > factor * 1.001) return step;
        }
        return MAX;
    }

    /** The next smaller step; the smallest stays. */
    public static double out(double factor) {
        for (int i = STEPS.size() - 1; i >= 0; i--) {
            if (STEPS.get(i) < factor / 1.001) return STEPS.get(i);
        }
        return MIN;
    }

    /**
     * The factor at which the whole picture fits into the view. Small pictures are not enlarged unless
     * {@code enlarge} is true.
     */
    public static double fit(int imageWidth, int imageHeight, int viewWidth, int viewHeight, boolean enlarge) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 1;
        double factor = Math.min((double) viewWidth / imageWidth, (double) viewHeight / imageHeight);
        return enlarge ? factor : Math.min(1, factor);
    }
}
