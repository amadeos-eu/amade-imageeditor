package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** The toolbar pictures that amade-commons does not have. */
public enum EditorGlyph implements ToolIcon.Glyph {
    CROP, STRAIGHTEN, ADJUST, RESIZE, FLIP_HORIZONTAL, FLIP_VERTICAL, MOVE, BRUSH, ERASER, SHAPES, TEXT, FILL, PICK;

    private static final Color PAINT = new Color(0xe0302a);
    private static final Color RUBBER = new Color(0xe86fa8);
    private static final Color WATER = new Color(0x2f8fd8);

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
            case MOVE -> {
                // Four arrows from the middle.
                pen.line(8, 1.5, 8, 14.5);
                pen.line(1.5, 8, 14.5, 8);
                pen.triangle(8, 0.5, 5.5, 3.5, 10.5, 3.5);
                pen.triangle(8, 15.5, 5.5, 12.5, 10.5, 12.5);
                pen.triangle(0.5, 8, 3.5, 5.5, 3.5, 10.5);
                pen.triangle(15.5, 8, 12.5, 5.5, 12.5, 10.5);
            }
            case BRUSH -> {
                Path2D handle = new Path2D.Double();
                handle.moveTo(14.5, 1.5);
                handle.lineTo(7.5, 9.5);
                handle.lineTo(6.5, 8.5);
                handle.lineTo(13.5, 0.5);
                handle.closePath();
                pen.fillAndDraw(handle, pen.paper());
                Path2D tip = new Path2D.Double();
                tip.moveTo(6, 8.5);
                tip.curveTo(3, 9, 4, 12, 1.5, 14.5);
                tip.curveTo(6, 14, 8, 12, 7.5, 10);
                tip.closePath();
                pen.fillAndDraw(tip, pen.enabled() ? PAINT : pen.paper());
            }
            case ERASER -> {
                Path2D body = new Path2D.Double();
                body.moveTo(9, 2);
                body.lineTo(15, 8);
                body.lineTo(9, 14);
                body.lineTo(3, 8);
                body.closePath();
                pen.fillAndDraw(body, pen.paper());
                Path2D end = new Path2D.Double();
                end.moveTo(6, 5);
                end.lineTo(3, 8);
                end.lineTo(7, 12);
                end.lineTo(10, 9);
                end.closePath();
                pen.fillAndDraw(end, pen.enabled() ? RUBBER : pen.light());
                pen.line(1, 15, 15, 15);
            }
            case SHAPES -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 1.5, 8, 8), pen.paper());
                pen.fillAndDraw(new Ellipse2D.Double(6.5, 6.5, 8, 8), pen.light());
            }
            case TEXT -> pen.letter("T", new Font(Font.SERIF, Font.BOLD, 15), 14);
            case FILL -> {
                Path2D bucket = new Path2D.Double();
                bucket.moveTo(2, 7);
                bucket.lineTo(7, 2);
                bucket.lineTo(13, 8);
                bucket.lineTo(8, 13);
                bucket.closePath();
                pen.fillAndDraw(bucket, pen.paper());
                pen.fill(new Ellipse2D.Double(12, 10, 3.5, 5), pen.enabled() ? WATER : pen.light());
            }
            case PICK -> {
                pen.bold(2.2f);
                pen.line(3, 13, 10, 6);
                pen.thin();
                pen.fillAndDraw(new Ellipse2D.Double(9.5, 1.5, 5, 5), pen.ink());
                pen.line(1.5, 14.5, 3, 13);
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
