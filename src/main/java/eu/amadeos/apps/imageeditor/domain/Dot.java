package eu.amadeos.apps.imageeditor.domain;

/** A point on the picture, in picture pixels; painting keeps the fractions of a pixel the mouse gives. */
public record Dot(double x, double y) {
}
