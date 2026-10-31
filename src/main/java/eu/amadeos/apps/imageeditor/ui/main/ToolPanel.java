package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.AbstractButton;
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
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The panel at the right of the picture with the settings of the open tool: crop, straighten or adjust, each with
 * Cancel and Apply. It reports every change to the presenter and shows what the presenter sends back; a flag keeps
 * those updates from being reported again.
 */
public final class ToolPanel extends JPanel {
    /** What the panel asks of the presenter. */
    public interface Actions extends PaintPanels.Actions {
        void setRatio(AspectRatio ratio);

        void customRatio();

        void setPortrait(boolean portrait);

        void typeCrop(CropBox.Field field, int value);

        void setGuides(Guides guides);

        void setAngle(double degrees);

        void setStraightenCrops(boolean crop);

        void rotateLeft();

        void rotateRight();

        void flipHorizontally();

        void flipVertically();

        void setAdjustments(Adjustments adjustments);

        void autoLevels();

        void setCompare(boolean on);

        void cancelTool();

        void applyTool();
    }

    private final Messages msg;
    private final Actions actions;
    private final PaintPanels paint;
    private final CardLayout cards = new CardLayout();
    private boolean updating;

    private final Map<AspectRatio, JRadioButton> ratios = new LinkedHashMap<>();
    private final JRadioButton custom = new JRadioButton();
    private final JToggleButton landscape = new JToggleButton();
    private final JToggleButton portrait = new JToggleButton();
    private final Map<CropBox.Field, JSpinner> cropFields = new EnumMap<>(CropBox.Field.class);
    private final JComboBox<Guides> guides = new JComboBox<>(Guides.values());

    private final JSlider angle = new JSlider(-450, 450, 0);
    private final JSpinner degrees = new JSpinner(new SpinnerNumberModel(0.0, -45.0, 45.0, 0.1));
    private final JRadioButton straightenCrop = new JRadioButton();
    private final JRadioButton straightenFill = new JRadioButton();

    private final JSlider brightness = slider();
    private final JSlider contrast = slider();
    private final JSlider saturation = slider();
    private final JSlider warmth = slider();
    private final Map<JSlider, JLabel> values = new LinkedHashMap<>();
    private final JCheckBox grayscale = new JCheckBox();
    private final JToggleButton compare = new JToggleButton();

    private Tool tool = Tool.NONE;

    public ToolPanel(Messages msg, Actions actions) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.actions = actions;
        setLayout(cards);
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(6, 10, 10, 10)));
        add(new JPanel(), Tool.NONE.name());
        add(withButtons(cropPanel(), "tool.cancel"), Tool.CROP.name());
        add(withButtons(straightenPanel(), "tool.cancel"), Tool.STRAIGHTEN.name());
        add(withButtons(adjustPanel(), "tool.reset"), Tool.ADJUST.name());
        paint = new PaintPanels(msg, actions);
        add(paint.move(), Tool.MOVE.name());
        add(paint.brush(), Tool.BRUSH.name());
        add(paint.eraser(), Tool.ERASER.name());
        add(paint.shapes(), Tool.SHAPES.name());
        add(paint.text(), Tool.TEXT.name());
        add(paint.fill(), Tool.FILL.name());
        add(paint.pick(), Tool.PICK.name());
        setPreferredSize(new Dimension(Math.round(250 * AmadeUI.fontScale()), 10));
        setVisible(false);
    }

    /** Opens the panel of a tool, or hides the panel for {@link Tool#NONE}. */
    public void showTool(Tool newTool) {
        tool = newTool;
        paint.showTool(newTool);
        cards.show(this, newTool.name());
        setVisible(newTool != Tool.NONE);
        if (newTool == Tool.ADJUST) {
            updating = true;
            compare.setSelected(false);
            updating = false;
        }
    }

    public Tool tool() {
        return tool;
    }

    // ---------------------------------------------------------------- crop

    private JComponent cropPanel() {
        JPanel p = column();
        p.add(section("crop.ratio"));
        JPanel grid = new JPanel(new GridLayout(0, 2, 4, 2));
        ButtonGroup group = new ButtonGroup();
        for (AspectRatio ratio : AspectRatio.PRESETS) {
            JRadioButton b = new JRadioButton(ratioLabel(ratio));
            b.setName("crop.ratio." + ratio.key());
            b.addActionListener(e -> {
                if (!updating) actions.setRatio(ratio);
            });
            group.add(b);
            grid.add(b);
            ratios.put(ratio, b);
        }
        custom.setText(msg.get("crop.ratio.custom"));
        custom.setName("crop.ratio.custom");
        custom.addActionListener(e -> {
            if (!updating) actions.customRatio();
        });
        group.add(custom);
        grid.add(custom);
        p.add(left(grid));

        JPanel orientation = new JPanel(new GridLayout(1, 2, 4, 0));
        ButtonGroup sides = new ButtonGroup();
        landscape.setText(msg.get("crop.landscape"));
        landscape.setName("crop.landscape");
        portrait.setText(msg.get("crop.portrait"));
        portrait.setName("crop.portrait");
        sides.add(landscape);
        sides.add(portrait);
        landscape.addActionListener(e -> {
            if (!updating) actions.setPortrait(false);
        });
        portrait.addActionListener(e -> {
            if (!updating) actions.setPortrait(true);
        });
        orientation.add(landscape);
        orientation.add(portrait);
        orientation.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        p.add(left(orientation));

        p.add(section("crop.size"));
        for (CropBox.Field field : new CropBox.Field[]{CropBox.Field.WIDTH, CropBox.Field.HEIGHT,
                CropBox.Field.LEFT, CropBox.Field.TOP}) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
            spinner.setName("crop." + field.name().toLowerCase(java.util.Locale.ROOT));
            spinner.addChangeListener(e -> {
                if (!updating) actions.typeCrop(field, ((Number) spinner.getValue()).intValue());
            });
            cropFields.put(field, spinner);
            p.add(row(msg.get("crop.field." + field.name().toLowerCase(java.util.Locale.ROOT)), spinner, "px"));
        }
        p.add(section("crop.guides"));
        guides.setName("crop.guides");
        guides.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(msg.get("crop.guides." + ((Guides) value).name().toLowerCase(java.util.Locale.ROOT)));
                return this;
            }
        });
        guides.addActionListener(e -> {
            if (!updating) actions.setGuides((Guides) guides.getSelectedItem());
        });
        p.add(left(flow(guides)));
        return p;
    }

    private String ratioLabel(AspectRatio ratio) {
        if (ratio.equals(AspectRatio.FREE)) return msg.get("crop.ratio.free");
        if (ratio.equals(AspectRatio.ORIGINAL)) return msg.get("crop.ratio.original");
        return ratio.key();
    }

    /** Shows the crop settings; the ratio is checked among the presets, otherwise "Custom". */
    public void showCrop(Rectangle box, AspectRatio ratio, boolean portraitOn, Guides shownGuides) {
        updating = true;
        try {
            JRadioButton button = ratios.get(ratio);
            if (button != null) button.setSelected(true);
            else {
                custom.setSelected(true);
                custom.setText(msg.get("crop.ratio.custom") + " " + ratio.key());
            }
            if (button != null) custom.setText(msg.get("crop.ratio.custom"));
            boolean sided = !ratio.free() && !ratio.equals(AspectRatio.SQUARE);
            landscape.setEnabled(sided);
            portrait.setEnabled(sided);
            landscape.setSelected(!portraitOn);
            portrait.setSelected(portraitOn);
            if (box != null) {
                set(CropBox.Field.LEFT, box.x);
                set(CropBox.Field.TOP, box.y);
                set(CropBox.Field.WIDTH, box.width);
                set(CropBox.Field.HEIGHT, box.height);
            }
            guides.setSelectedItem(shownGuides);
        } finally {
            updating = false;
        }
    }

    private void set(CropBox.Field field, int value) {
        JSpinner spinner = cropFields.get(field);
        if (((Number) spinner.getValue()).intValue() != value) spinner.setValue(value);
    }

    // ---------------------------------------------------------------- straighten

    private JComponent straightenPanel() {
        JPanel p = column();
        p.add(section("straighten.angle"));
        angle.setName("straighten.slider");
        angle.setMajorTickSpacing(150);
        angle.setPaintTicks(true);
        angle.addChangeListener(e -> {
            if (!updating) actions.setAngle(angle.getValue() / 10.0);
        });
        p.add(left(angle));
        JPanel scale = new JPanel(new BorderLayout());
        scale.add(small("-45°"), BorderLayout.WEST);
        JLabel zero = small("0°");
        zero.setHorizontalAlignment(SwingConstants.CENTER);
        scale.add(zero, BorderLayout.CENTER);
        scale.add(small("+45°"), BorderLayout.EAST);
        p.add(left(scale));
        degrees.setName("straighten.degrees");
        degrees.setEditor(new JSpinner.NumberEditor(degrees, "0.0"));
        degrees.addChangeListener(e -> {
            if (!updating) actions.setAngle(((Number) degrees.getValue()).doubleValue());
        });
        p.add(row(msg.get("straighten.degrees"), degrees, "°"));
        p.add(Box.createVerticalStrut(6));
        p.add(left(wrapped(msg.get("straighten.hint"))));

        p.add(section("straighten.edges"));
        ButtonGroup edges = new ButtonGroup();
        straightenCrop.setText(msg.get("straighten.crop"));
        straightenCrop.setName("straighten.crop");
        straightenFill.setText(msg.get("straighten.fill"));
        straightenFill.setName("straighten.fill");
        edges.add(straightenCrop);
        edges.add(straightenFill);
        straightenCrop.addActionListener(e -> {
            if (!updating) actions.setStraightenCrops(true);
        });
        straightenFill.addActionListener(e -> {
            if (!updating) actions.setStraightenCrops(false);
        });
        p.add(left(straightenCrop));
        p.add(left(straightenFill));

        p.add(section("straighten.turn"));
        JPanel quick = new JPanel(new GridLayout(2, 2, 4, 4));
        quick.add(button("straighten.left", actions::rotateLeft));
        quick.add(button("straighten.right", actions::rotateRight));
        quick.add(button("straighten.flip-horizontal", actions::flipHorizontally));
        quick.add(button("straighten.flip-vertical", actions::flipVertically));
        p.add(left(quick));
        return p;
    }

    public void showStraighten(double shownDegrees, boolean crops) {
        updating = true;
        try {
            angle.setValue((int) Math.round(shownDegrees * 10));
            degrees.setValue(Math.round(shownDegrees * 10) / 10.0);
            straightenCrop.setSelected(crops);
            straightenFill.setSelected(!crops);
        } finally {
            updating = false;
        }
    }

    // ---------------------------------------------------------------- adjust

    private JComponent adjustPanel() {
        JPanel p = column();
        p.add(section("adjust.light"));
        p.add(sliderRow("adjust.brightness", brightness, v -> actions.setAdjustments(read().withBrightness(v))));
        p.add(sliderRow("adjust.contrast", contrast, v -> actions.setAdjustments(read().withContrast(v))));
        p.add(section("adjust.colour"));
        p.add(sliderRow("adjust.saturation", saturation, v -> actions.setAdjustments(read().withSaturation(v))));
        p.add(sliderRow("adjust.warmth", warmth, v -> actions.setAdjustments(read().withWarmth(v))));
        Mnemonics.apply(grayscale, msg.get("adjust.grayscale"));
        grayscale.setName("adjust.grayscale");
        grayscale.addActionListener(e -> {
            if (!updating) actions.setAdjustments(read());
        });
        p.add(left(grayscale));
        p.add(section("adjust.automatic"));
        p.add(left(flow(button("adjust.auto-levels", actions::autoLevels))));
        p.add(Box.createVerticalStrut(10));
        compare.setText(msg.get("adjust.compare"));
        compare.setName("adjust.compare");
        compare.addActionListener(e -> {
            if (!updating) actions.setCompare(compare.isSelected());
        });
        p.add(left(flow(compare)));
        p.add(left(wrapped(msg.get("adjust.hint"))));
        return p;
    }

    private Adjustments read() {
        return new Adjustments(brightness.getValue(), contrast.getValue(), saturation.getValue(), warmth.getValue(),
                grayscale.isSelected());
    }

    public void showAdjustments(Adjustments a) {
        updating = true;
        try {
            brightness.setValue(a.brightness());
            contrast.setValue(a.contrast());
            saturation.setValue(a.saturation());
            warmth.setValue(a.warmth());
            grayscale.setSelected(a.grayscale());
            saturation.setEnabled(!a.grayscale());
            warmth.setEnabled(!a.grayscale());
            for (Map.Entry<JSlider, JLabel> e : values.entrySet()) e.getValue().setText(signed(e.getKey().getValue()));
        } finally {
            updating = false;
        }
    }

    public void showCompare(boolean on) {
        updating = true;
        compare.setSelected(on);
        updating = false;
    }

    private JComponent sliderRow(String key, JSlider slider, IntConsumer change) {
        slider.setName(key);
        JLabel value = new JLabel("0");
        values.put(slider, value);
        slider.addChangeListener(e -> {
            value.setText(signed(slider.getValue()));
            if (!updating) change.accept(slider.getValue());
        });
        JPanel top = new JPanel(new BorderLayout());
        JLabel name = new JLabel(msg.get(key));
        name.setLabelFor(slider);
        top.add(name, BorderLayout.WEST);
        top.add(value, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return left(p);
    }

    private static JSlider slider() {
        return new JSlider(Adjustments.MIN, Adjustments.MAX, 0);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    // ---------------------------------------------------------------- building blocks

    /** The tool's column with Cancel (or Reset) and Apply at the bottom. */
    private JComponent withButtons(JComponent content, String cancelKey) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(content, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        JButton cancel = new JButton(msg.get(cancelKey));
        cancel.setName(cancelKey);
        cancel.addActionListener(e -> actions.cancelTool());
        JButton apply = new JButton(msg.get("tool.apply"));
        apply.setName("tool.apply");
        apply.addActionListener(e -> actions.applyTool());
        buttons.add(cancel);
        buttons.add(apply);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JButton button(String key, Runnable action) {
        JButton b = new JButton(msg.get(key));
        b.setName(key);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private JComponent section(String key) {
        JLabel l = new JLabel(msg.get(key));
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, javax.swing.UIManager.getColor("controlShadow"))));
        return left(l);
    }

    private static JComponent row(String name, JComponent field, String unit) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        JLabel k = new JLabel(name);
        k.setLabelFor(field);
        k.setPreferredSize(new Dimension(Math.round(70 * AmadeUI.fontScale()), k.getPreferredSize().height));
        r.add(k, BorderLayout.WEST);
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        field.setPreferredSize(new Dimension(Math.round(90 * AmadeUI.fontScale()), field.getPreferredSize().height));
        f.add(field);
        f.add(Box.createHorizontalStrut(4));
        f.add(new JLabel(unit));
        r.add(f, BorderLayout.CENTER);
        r.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        return left(r);
    }

    private static JComponent flow(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        p.add(c);
        return p;
    }

    private static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 2));
        return l;
    }

    private static JLabel wrapped(String text) {
        return new JLabel("<html><div style=\"width:" + Math.round(150 * AmadeUI.fontScale()) + "px\">"
                + text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    /** The panels of the painting tools. */
    PaintPanels paint() {
        return paint;
    }

    // ---------------------------------------------------------------- for tests

    AbstractButton ratioButton(AspectRatio ratio) {
        return ratios.get(ratio);
    }

    JSpinner cropField(CropBox.Field field) {
        return cropFields.get(field);
    }

    JSlider brightnessSlider() {
        return brightness;
    }

    JSlider angleSlider() {
        return angle;
    }

    JCheckBox grayscaleBox() {
        return grayscale;
    }
}
