package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** The toolbar pictures that amade-commons does not have. */
public enum EditorGlyph implements ToolIcon.Glyph {
    CROP, STRAIGHTEN, ADJUST, RESIZE, FLIP_HORIZONTAL, FLIP_VERTICAL;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case CROP -> {
                pen.bold(1.6f);
                pen.line(4, 1, 4, 12);
                pen.line(4, 12, 15, 12);
                pen.line(1, 4, 12, 4);
                pen.line(12, 4, 12, 15);
                pen.thin();
            }
            case STRAIGHTEN -> {
                // A spirit level over a tilted line.
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 4.5, 13, 7), pen.paper());
                pen.fillAndDraw(new Ellipse2D.Double(6, 6, 4, 4), pen.light());
                pen.line(1, 14.5, 15, 12.5);
            }
            case ADJUST -> {
                pen.fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), pen.paper());
                pen.fill(new Arc2D.Double(1.5, 1.5, 13, 13, 90, 180, Arc2D.PIE), pen.ink());
            }
            case RESIZE -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 6.5, 8, 8), pen.paper());
                pen.draw(new Rectangle2D.Double(1.5, 1.5, 13, 13));
                pen.line(7, 9, 13, 3);
                pen.line(13, 3, 9.5, 3);
                pen.line(13, 3, 13, 6.5);
            }
            case FLIP_HORIZONTAL -> {
                pen.line(8, 1, 8, 15);
                pen.triangle(6.5, 3, 6.5, 13, 1.5, 13);
                pen.fillAndDraw(triangle(9.5, 3, 9.5, 13, 14.5, 13), pen.paper());
            }
            case FLIP_VERTICAL -> {
                pen.line(1, 8, 15, 8);
                pen.triangle(3, 6.5, 13, 6.5, 13, 1.5);
                pen.fillAndDraw(triangle(3, 9.5, 13, 9.5, 13, 14.5), pen.paper());
            }
        }
    }

    private static Shape triangle(double x1, double y1, double x2, double y2, double x3, double y3) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.closePath();
        return p;
    }
}
