package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * The two colours in the toolbar, as in every paint program: the first (left mouse button) in front, the second
 * (right button) behind it. A click on either opens the colour chooser for it; the small bent arrow swaps them.
 */
final class ColourPair extends JComponent {
    private final PaintPanels.Actions actions;
    private Color primary = Color.BLACK, secondary = Color.WHITE;

    ColourPair(PaintPanels.Actions actions, String tip) {
        this.actions = actions;
        setName("colours");
        setToolTipText(tip);
        int w = Math.round(36 * AmadeUI.fontScale()), h = Math.round(30 * AmadeUI.fontScale());
        setPreferredSize(new Dimension(w, h));
        setMaximumSize(new Dimension(w, h));
        setMinimumSize(new Dimension(w, h));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isEnabled() || !SwingUtilities.isLeftMouseButton(e)) return;
                if (swapArea().contains(e.getPoint())) actions.swapColours();
                else if (front().contains(e.getPoint())) actions.chooseColour(false);
                else if (back().contains(e.getPoint())) actions.chooseColour(true);
            }
        });
    }

    void showColours(Color first, Color second) {
        primary = first;
        secondary = second;
        repaint();
    }

    private Rectangle front() {
        int s = getHeight() * 3 / 5;
        return new Rectangle(2, 2, s, s);
    }

    private Rectangle back() {
        int s = getHeight() * 3 / 5;
        return new Rectangle(getHeight() - s + 4, getHeight() - s - 1, s, s);
    }

    private Rectangle swapArea() {
        Rectangle b = back();
        return new Rectangle(b.x, 0, getWidth() - b.x, b.y);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Rectangle b = back(), f = front();
        g.setColor(secondary);
        g.fillRect(b.x, b.y, b.width, b.height);
        AmadeUI.paintBevel(g, b.x, b.y, b.width, b.height, false, 1);
        g.setColor(primary);
        g.fillRect(f.x, f.y, f.width, f.height);
        AmadeUI.paintBevel(g, f.x, f.y, f.width, f.height, false, 1);
        // The swap arrow at the top right.
        Rectangle s = swapArea();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getForeground());
        g.setStroke(new BasicStroke(1.2f));
        Path2D arrow = new Path2D.Double();
        double x0 = s.x + 3, x1 = s.getMaxX() - 3, y0 = 3, y1 = s.getMaxY() - 2;
        arrow.moveTo(x0, y0 + 1);
        arrow.quadTo(x1, y0 + 1, x1, y1);
        g.draw(arrow);
        g.drawLine((int) x1, (int) y1, (int) x1 - 2, (int) y1 - 2);
        g.drawLine((int) x1, (int) y1, (int) x1 + 2, (int) y1 - 2);
        g.dispose();
    }
}
