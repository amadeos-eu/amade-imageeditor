package eu.amadeos.apps.imageeditor.ui.main;

import java.awt.Graphics2D;

/**
 * Something the canvas draws over the picture while a tool works: the text being typed, the layer being moved.
 * It draws in picture pixels; the canvas has set up the graphics for zoom and scrolling.
 */
@FunctionalInterface
public interface Overlay {
    void paint(Graphics2D g);
}
