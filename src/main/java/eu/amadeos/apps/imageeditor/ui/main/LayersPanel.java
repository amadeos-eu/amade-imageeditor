package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Layers panel below the tool panel: the layers from the top down with a box for visibility, a small picture
 * and the name; below them the active layer's opacity and buttons for new, copy, up, down and delete. A right
 * click opens the layer menu, a double click renames.
 */
public final class LayersPanel extends JPanel {
    /** What the panel asks of the presenter. Layers are named by their place from the bottom. */
    public interface Actions {
        void selectLayer(int index);

        void setLayerVisible(int index, boolean visible);

        void setLayerOpacity(int index, int opacity);

        void renameLayer(int index);

        void newLayer();

        void duplicateLayer();

        void deleteLayer();

        void layerUp();

        void layerDown();

        void mergeDown();

        void flatten();

        void pasteAsLayer();

        void pictureAsLayer();
    }

    private static final int THUMB_W = 44, THUMB_H = 30;

    private final Messages msg;
    private final Actions actions;
    private final DefaultListModel<Layer> model = new DefaultListModel<>();
    private final JList<Layer> list = new JList<>(model);
    private final JSlider opacity = new JSlider(0, 100, 100);
    private final JLabel opacityValue = new JLabel("100 %");
    private final JButton up, down, delete;
    /** Small pictures of the layers, made once per layer's pixels. */
    private final Map<BufferedImage, Image> thumbnails = new WeakHashMap<>();
    private List<Layer> layers = List.of();
    private int active;
    private boolean updating;

    public LayersPanel(Messages msg, Actions actions) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.actions = actions;
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        JLabel title = new JLabel(msg.get("layers.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("controlShadow"))));
        add(title, BorderLayout.NORTH);

        list.setName("layers.list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Row());
        list.setVisibleRowCount(4);
        list.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                actions.selectLayer(layerIndex(list.getSelectedIndex()));
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = list.locationToIndex(e.getPoint());
                if (row < 0 || !list.getCellBounds(row, row).contains(e.getPoint())) return;
                int index = layerIndex(row);
                if (SwingUtilities.isLeftMouseButton(e) && e.getX() < checkWidth()) {
                    actions.setLayerVisible(index, !layers.get(index).visible());
                } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    actions.renameLayer(index);
                }
                if (e.isPopupTrigger()) popup(e, row);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e, list.locationToIndex(e.getPoint()));
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(AmadeUI.sunken());
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        JPanel opacityRow = new JPanel(new BorderLayout(6, 0));
        JLabel opacityLabel = new JLabel(msg.get("layers.opacity"));
        opacityLabel.setLabelFor(opacity);
        opacityRow.add(opacityLabel, BorderLayout.WEST);
        opacity.setName("layers.opacity");
        opacity.addChangeListener(e -> {
            opacityValue.setText(opacity.getValue() + " %");
            // One step when the knob is let go, not one per pixel of dragging.
            if (!updating && !opacity.getValueIsAdjusting() && !layers.isEmpty()) {
                actions.setLayerOpacity(active, opacity.getValue());
            }
        });
        opacityRow.add(opacity, BorderLayout.CENTER);
        opacityRow.add(opacityValue, BorderLayout.EAST);
        bottom.add(opacityRow, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(1, 5, 2, 0));
        buttons.add(button("layers.new", actions::newLayer));
        buttons.add(button("layers.duplicate", actions::duplicateLayer));
        up = button("layers.up", actions::layerUp);
        down = button("layers.down", actions::layerDown);
        delete = button("layers.delete", actions::deleteLayer);
        buttons.add(up);
        buttons.add(down);
        buttons.add(delete);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(Math.round(250 * AmadeUI.fontScale()), Math.round(230 * AmadeUI.fontScale())));
    }

    private JButton button(String key, Runnable action) {
        JButton b = new JButton(msg.get(key));
        b.setName(key);
        b.setToolTipText(msg.get(key + ".tip"));
        b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() - 2));
        b.setMargin(new java.awt.Insets(1, 1, 1, 1));
        b.addActionListener(e -> action.run());
        return b;
    }

    /** The layers from the bottom up (shown from the top down) and the active one. */
    public void showLayers(List<Layer> newLayers, int newActive) {
        updating = true;
        try {
            layers = List.copyOf(newLayers);
            active = newActive;
            model.clear();
            for (int i = layers.size() - 1; i >= 0; i--) model.addElement(layers.get(i));
            int row = layers.size() - 1 - active;
            list.setSelectedIndex(row);
            if (row >= 0) list.ensureIndexIsVisible(row);
            boolean any = !layers.isEmpty();
            int value = any ? layers.get(active).opacity() : 100;
            opacity.setValue(value);
            opacityValue.setText(value + " %");
            opacity.setEnabled(any);
            up.setEnabled(any && active < layers.size() - 1);
            down.setEnabled(any && active > 0);
            delete.setEnabled(layers.size() > 1);
        } finally {
            updating = false;
        }
    }

    private int layerIndex(int row) {
        return layers.size() - 1 - row;
    }

    private int checkWidth() {
        return Math.round(26 * AmadeUI.fontScale());
    }

    private void popup(MouseEvent e, int row) {
        if (row >= 0 && row < model.size() && layerIndex(row) != active) actions.selectLayer(layerIndex(row));
        menu().show(list, e.getX(), e.getY());
    }

    /** The layer menu, also shown on a right click in the panel. */
    JPopupMenu menu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("menu.layer.new", actions::newLayer, true));
        menu.add(item("menu.layer.duplicate", actions::duplicateLayer, !layers.isEmpty()));
        menu.add(item("menu.layer.picture", actions::pictureAsLayer, !layers.isEmpty()));
        menu.add(item("menu.layer.paste", actions::pasteAsLayer, !layers.isEmpty()));
        menu.addSeparator();
        menu.add(item("menu.layer.rename", () -> actions.renameLayer(active), !layers.isEmpty()));
        menu.add(item("menu.layer.up", actions::layerUp, active < layers.size() - 1));
        menu.add(item("menu.layer.down", actions::layerDown, active > 0));
        menu.addSeparator();
        menu.add(item("menu.layer.merge-down", actions::mergeDown, active > 0));
        menu.add(item("menu.layer.flatten", actions::flatten, layers.size() > 1));
        menu.addSeparator();
        menu.add(item("menu.layer.delete", actions::deleteLayer, layers.size() > 1));
        return menu;
    }

    private JMenuItem item(String key, Runnable action, boolean enabled) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        item.setEnabled(enabled);
        item.addActionListener(e -> action.run());
        return item;
    }

    private Image thumbnail(Layer layer) {
        return thumbnails.computeIfAbsent(layer.image(), image -> {
            double scale = Math.min((double) THUMB_W / image.getWidth(), (double) THUMB_H / image.getHeight());
            int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
            BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, w, h, null);
            g.dispose();
            return small;
        });
    }

    /** One row: visibility box, small picture on a checkerboard, name, and the opacity if it is not full. */
    private final class Row extends JPanel implements ListCellRenderer<Layer> {
        private final JCheckBox eye = new JCheckBox();
        private final JLabel name = new JLabel();
        private final JLabel percent = new JLabel();
        private Layer layer;

        Row() {
            super(new BorderLayout(6, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            eye.setOpaque(false);
            add(eye, BorderLayout.WEST);
            JPanel middle = new JPanel(new BorderLayout(6, 0));
            middle.setOpaque(false);
            JComponent thumb = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    paintThumb((Graphics2D) g);
                }
            };
            thumb.setPreferredSize(new Dimension(THUMB_W + 2, THUMB_H + 2));
            middle.add(thumb, BorderLayout.WEST);
            middle.add(name, BorderLayout.CENTER);
            middle.add(percent, BorderLayout.EAST);
            add(middle, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Layer> l, Layer value, int index,
                                                      boolean selected, boolean focus) {
            layer = value;
            setBackground(selected ? l.getSelectionBackground() : l.getBackground());
            Color fg = selected ? l.getSelectionForeground() : l.getForeground();
            name.setForeground(fg);
            percent.setForeground(fg);
            name.setText(value.name());
            name.setFont(l.getFont().deriveFont(value.isText() ? Font.ITALIC | Font.BOLD : Font.BOLD));
            percent.setText(value.opacity() < 100 ? value.opacity() + " %" : "");
            eye.setSelected(value.visible());
            eye.setToolTipText(msg.get("layers.visible"));
            return this;
        }

        private void paintThumb(Graphics2D g) {
            int s = 5;
            for (int y = 0; y < THUMB_H; y += s) {
                for (int x = 0; x < THUMB_W; x += s) {
                    g.setColor(((x / s + y / s) & 1) == 0 ? new Color(0xcccccc) : Color.WHITE);
                    g.fillRect(1 + x, 1 + y, Math.min(s, THUMB_W - x), Math.min(s, THUMB_H - y));
                }
            }
            if (layer != null) {
                Image t = thumbnail(layer);
                int w = t.getWidth(null), h = t.getHeight(null);
                g.drawImage(t, 1 + (THUMB_W - w) / 2, 1 + (THUMB_H - h) / 2, null);
            }
            g.setColor(UIManager.getColor("controlShadow") == null ? Color.GRAY : UIManager.getColor("controlShadow"));
            g.drawRect(0, 0, THUMB_W + 1, THUMB_H + 1);
        }
    }

    // ---------------------------------------------------------------- for tests

    JList<Layer> layerList() {
        return list;
    }

    JSlider opacitySlider() {
        return opacity;
    }

    Rectangle rowBounds(int layer) {
        int row = layers.size() - 1 - layer;
        return list.getCellBounds(row, row);
    }
}
