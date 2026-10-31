package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.Optional;

/** The contents of the Resize Image dialog: new size in pixels or percent, presets and the quality. */
public final class ResizePanel extends JPanel {
    /** The presets: the long side in pixels, or a percentage (negative numbers). */
    static final int[] PRESETS = {3840, 1920, 1280, 1024, 800, -50};

    private final int originalWidth, originalHeight;
    private final JRadioButton pixels = new JRadioButton();
    private final JRadioButton percent = new JRadioButton();
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(1, 1, Edit.Resize.LIMIT, 1));
    private final JSpinner height = new JSpinner(new SpinnerNumberModel(1, 1, Edit.Resize.LIMIT, 1));
    private final JLabel widthUnit = new JLabel("px"), heightUnit = new JLabel("px");
    private final JCheckBox keepRatio = new JCheckBox();
    private final JComboBox<ResizeQuality> quality = new JComboBox<>(ResizeQuality.values());
    /** The size in pixels, whatever the fields show. */
    private int targetWidth, targetHeight;
    private boolean updating;

    public ResizePanel(Messages msg, int originalWidth, int originalHeight, ResizeQuality initialQuality) {
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        targetWidth = originalWidth;
        targetHeight = originalHeight;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(left(new JLabel(msg.format("resize.current", originalWidth, originalHeight))));
        add(section(msg.get("resize.new")));
        ButtonGroup units = new ButtonGroup();
        pixels.setText(msg.get("resize.pixels"));
        pixels.setName("resize.pixels");
        percent.setText(msg.get("resize.percent"));
        percent.setName("resize.percent");
        units.add(pixels);
        units.add(percent);
        pixels.setSelected(true);
        pixels.addActionListener(e -> showFields());
        percent.addActionListener(e -> showFields());
        JPanel unitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        unitRow.add(pixels);
        unitRow.add(Box.createHorizontalStrut(10));
        unitRow.add(percent);
        add(left(unitRow));
        width.setName("resize.width");
        height.setName("resize.height");
        add(row(msg.get("resize.width"), width, widthUnit));
        add(row(msg.get("resize.height"), height, heightUnit));
        keepRatio.setText(msg.get("resize.keep-ratio"));
        keepRatio.setName("resize.keep-ratio");
        keepRatio.setSelected(true);
        keepRatio.addActionListener(e -> {
            if (keepRatio.isSelected()) changed(true);
        });
        add(left(keepRatio));
        width.addChangeListener(e -> changed(true));
        height.addChangeListener(e -> changed(false));

        add(section(msg.get("resize.presets")));
        JPanel presets = new JPanel(new GridLayout(2, 3, 4, 4));
        for (int preset : PRESETS) {
            JButton b = new JButton(preset < 0 ? -preset + " %" : String.valueOf(preset));
            b.setName("resize.preset." + preset);
            b.addActionListener(e -> preset(preset));
            presets.add(b);
        }
        add(left(presets));
        add(section(msg.get("resize.quality")));
        quality.setName("resize.quality");
        quality.setSelectedItem(initialQuality);
        quality.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(msg.get("resize.quality." + ((ResizeQuality) value).name().toLowerCase(Locale.ROOT)));
                return this;
            }
        });
        JPanel q = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        q.add(quality);
        add(left(q));
        showFields();
        setPreferredSize(new Dimension(Math.round(320 * AmadeUI.fontScale()), getPreferredSize().height));
    }

    /** The resize the user set up; empty if it would change nothing. */
    public Optional<Edit.Resize> result() {
        if (targetWidth == originalWidth && targetHeight == originalHeight) return Optional.empty();
        return Optional.of(new Edit.Resize(targetWidth, targetHeight, (ResizeQuality) quality.getSelectedItem()));
    }

    public ResizeQuality quality() {
        return (ResizeQuality) quality.getSelectedItem();
    }

    void preset(int preset) {
        int[] size = preset < 0 ? percentOf(-preset, originalWidth, originalHeight)
                : longSide(preset, originalWidth, originalHeight);
        keepRatio.setSelected(true);
        targetWidth = size[0];
        targetHeight = size[1];
        showFields();
    }

    /** One field changed: keep the ratio if asked, and remember the size in pixels. */
    private void changed(boolean widthChanged) {
        if (updating) return;
        int w = ((Number) width.getValue()).intValue(), h = ((Number) height.getValue()).intValue();
        if (percent.isSelected()) {
            w = Math.max(1, (int) Math.round(originalWidth * w / 100.0));
            h = Math.max(1, (int) Math.round(originalHeight * h / 100.0));
        }
        if (keepRatio.isSelected()) {
            if (widthChanged) h = other(w, originalWidth, originalHeight);
            else w = other(h, originalHeight, originalWidth);
        }
        targetWidth = Math.clamp(w, 1, Edit.Resize.LIMIT);
        targetHeight = Math.clamp(h, 1, Edit.Resize.LIMIT);
        showFields();
    }

    private void showFields() {
        updating = true;
        try {
            if (percent.isSelected()) {
                width.setValue((int) Math.round(targetWidth * 100.0 / originalWidth));
                height.setValue((int) Math.round(targetHeight * 100.0 / originalHeight));
                widthUnit.setText("%");
                heightUnit.setText("%");
            } else {
                width.setValue(targetWidth);
                height.setValue(targetHeight);
                widthUnit.setText("px");
                heightUnit.setText("px");
            }
        } finally {
            updating = false;
        }
    }

    /** The other side for one side's new length, keeping the ratio. */
    static int other(int side, int originalSide, int originalOther) {
        return Math.max(1, (int) Math.round((double) side * originalOther / originalSide));
    }

    /** The size whose longer side is {@code side}; small pictures are not enlarged by a preset. */
    static int[] longSide(int side, int w, int h) {
        if (Math.max(w, h) <= side) return new int[]{w, h};
        return w >= h ? new int[]{side, other(side, w, h)} : new int[]{other(side, h, w), side};
    }

    static int[] percentOf(int percent, int w, int h) {
        return new int[]{Math.max(1, (int) Math.round(w * percent / 100.0)),
                Math.max(1, (int) Math.round(h * percent / 100.0))};
    }

    private static JComponent section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, javax.swing.UIManager.getColor("controlShadow"))));
        return left(l);
    }

    private static JComponent row(String name, JComponent field, JLabel unit) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        JLabel k = new JLabel(name);
        k.setLabelFor(field);
        k.setPreferredSize(new Dimension(Math.round(70 * AmadeUI.fontScale()), k.getPreferredSize().height));
        r.add(k, BorderLayout.WEST);
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        field.setPreferredSize(new Dimension(Math.round(90 * AmadeUI.fontScale()), field.getPreferredSize().height));
        f.add(field);
        f.add(Box.createHorizontalStrut(4));
        f.add(unit);
        r.add(f, BorderLayout.CENTER);
        r.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        return left(r);
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // ---------------------------------------------------------------- for tests

    JSpinner widthField() {
        return width;
    }

    JSpinner heightField() {
        return height;
    }

    JRadioButton percentButton() {
        return percent;
    }

    JCheckBox keepRatioBox() {
        return keepRatio;
    }
}
