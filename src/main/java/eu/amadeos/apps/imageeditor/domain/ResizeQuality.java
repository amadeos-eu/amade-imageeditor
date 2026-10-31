package eu.amadeos.apps.imageeditor.domain;

/** How a picture is scaled. */
public enum ResizeQuality {
    /** Bicubic, halving step by step when shrinking: smooth and sharp, the default. */
    BEST,
    /** One bilinear step: quick, a little soft when shrinking a lot. */
    FAST,
    /** Nearest neighbour: hard pixels, for pixel art and screenshots enlarged by whole factors. */
    PIXELS
}
