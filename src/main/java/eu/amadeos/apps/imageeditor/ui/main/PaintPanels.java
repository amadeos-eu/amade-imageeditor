package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.LineStyle;
import eu.amadeos.apps.imageeditor.domain.PaintSettings;
import eu.amadeos.apps.imageeditor.domain.ShapeFill;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The panels of the painting tools, shown by {@link ToolPanel}: Move, Brush, Eraser, Shapes, Text, Fill and Pick
 * Colour. Like the other panels they report each change and show what the presenter sends back.
 */
final class PaintPanels {
    /** What the painting panels ask of the presenter. */
    interface Actions {
        void setColour(Color colour, boolean second);

        void chooseColour(boolean second);

        void swapColours();

        void setBrush(Brush brush);

        void setShape(ShapeKind kind);

        void setLine(LineStyle line);

        void setShapeFill(ShapeFill fill);

        void setTolerance(int tolerance);

        void setText(String text);

        void setTextStyle(TextBox style);

        void finishText();

        void cancelText();
    }

    /** The colours of the palette: grays, the rainbow, and a few earthy and pale ones. */
    static final List<Color> PALETTE = List.of(Color.BLACK, new Color(0x555555), new Color(0xaaaaaa), Color.WHITE,
            new Color(0xe0302a), new Color(0xf28c38), new Color(0xffd84a), new Color(0x3fa34d),
            new Color(0x2f8fd8), new Color(0x2c3e70), new Color(0x8e44ad), new Color(0xe86fa8),
            new Color(0x8b5a2b), new Color(0x1abc9c), new Color(0xffffcc));

    private final Messages msg;
    private final Actions actions;
    private boolean updating;
    private Tool tool = Tool.NONE;
    private PaintSettings paint = PaintSettings.DEFAULT;
    private TextBox shownText;

    private final JSlider brushSize = new JSlider(1, 200, 24);
    private final JSlider brushHardness = new JSlider(0, 100, 60);
    private final JSlider brushOpacity = new JSlider(1, 100, 100);
    private final JLabel brushSizeValue = new JLabel(), brushHardnessValue = new JLabel(), brushOpacityValue = new JLabel();
    private final JSlider eraserSize = new JSlider(1, 200, 40);
    private final JSlider eraserHardness = new JSlider(0, 100, 80);
    private final JLabel eraserSizeValue = new JLabel(), eraserHardnessValue = new JLabel();

    private final Map<ShapeKind, JToggleButton> shapes = new EnumMap<>(ShapeKind.class);
    private final JSlider lineWidth = new JSlider(1, 100, 8);
    private final JLabel lineWidthValue = new JLabel();
    private final JComboBox<LineStyle.Dash> dash = new JComboBox<>(LineStyle.Dash.values());
    private final Map<ShapeFill, JRadioButton> fills = new EnumMap<>(ShapeFill.class);

    private final JSlider tolerance = new JSlider(0, 255, 32);
    private final JLabel toleranceValue = new JLabel();

    private final JTextArea text = new JTextArea(3, 14);
    private final JComboBox<String> font = new JComboBox<>();
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(48.0, (double) TextBox.MIN_SIZE,
            (double) TextBox.MAX_SIZE, 2.0));
    private final JToggleButton bold = new JToggleButton("B");
    private final JToggleButton italic = new JToggleButton("I");
    private final Map<TextBox.Align, JToggleButton> aligns = new EnumMap<>(TextBox.Align.class);
    private final Map<TextBox.Effect, JRadioButton> effects = new EnumMap<>(TextBox.Effect.class);
    private final JButton textDone = new JButton();
    private final JButton textCancel = new JButton();
    private final List<Swatches> swatches = new ArrayList<>();

    PaintPanels(Messages msg, Actions actions) {
        this.msg = msg;
        this.actions = actions;
    }

    // ---------------------------------------------------------------- panels

    JComponent move() {
        JPanel p = column();
        p.add(section("move.title"));
        p.add(left(wrapped(msg.get("move.hint"))));
        return top(p);
    }

    JComponent brush() {
        JPanel p = column();
        p.add(section("brush.title"));
        p.add(slider("brush.size", brushSize, brushSizeValue, v -> brushChanged()));
        p.add(slider("brush.hardness", brushHardness, brushHardnessValue, v -> brushChanged()));
        p.add(slider("brush.opacity", brushOpacity, brushOpacityValue, v -> brushChanged()));
        p.add(section("paint.colour"));
        p.add(palette());
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("brush.hint"))));
        return top(p);
    }

    JComponent eraser() {
        JPanel p = column();
        p.add(section("eraser.title"));
        p.add(slider("brush.size", eraserSize, eraserSizeValue, v -> brushChanged()));
        p.add(slider("brush.hardness", eraserHardness, eraserHardnessValue, v -> brushChanged()));
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("eraser.hint"))));
        return top(p);
    }

    JComponent shapes() {
        JPanel p = column();
        p.add(section("shapes.title"));
        JPanel grid = new JPanel(new GridLayout(0, 2, 2, 2));
        ButtonGroup group = new ButtonGroup();
        for (ShapeKind kind : ShapeKind.values()) {
            JToggleButton b = new JToggleButton(msg.get("shapes." + key(kind)));
            b.setName("shapes." + key(kind));
            b.addActionListener(e -> {
                if (!updating) actions.setShape(kind);
            });
            group.add(b);
            grid.add(b);
            shapes.put(kind, b);
        }
        p.add(left(grid));
        p.add(section("shapes.outline"));
        p.add(slider("shapes.width", lineWidth, lineWidthValue, v -> lineChanged()));
        dash.setName("shapes.dash");
        dash.setRenderer(renderer(v -> msg.get("shapes.dash." + key((Enum<?>) v))));
        dash.addActionListener(e -> lineChanged());
        p.add(left(flow(dash)));
        p.add(section("shapes.fill"));
        ButtonGroup fillGroup = new ButtonGroup();
        for (ShapeFill fill : ShapeFill.values()) {
            JRadioButton b = new JRadioButton(msg.get("shapes.fill." + key(fill)));
            b.setName("shapes.fill." + key(fill));
            b.addActionListener(e -> {
                if (!updating) actions.setShapeFill(fill);
            });
            fillGroup.add(b);
            p.add(left(b));
            fills.put(fill, b);
        }
        p.add(section("paint.colour"));
        p.add(palette());
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("shapes.hint"))));
        return top(p);
    }

    JComponent text() {
        JPanel p = column();
        p.add(section("text.title"));
        text.setName("text.text");
        text.setLineWrap(false);
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                typed();
            }
        });
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(10, Math.round(64 * AmadeUI.fontScale())));
        p.add(left(scroll));
        p.add(section("text.font"));
        font.setName("text.font");
        for (String family : families()) font.addItem(family);
        font.setRenderer(renderer(v -> String.valueOf(v)));
        font.addActionListener(e -> styleChanged());
        font.setPreferredSize(new Dimension(Math.round(200 * AmadeUI.fontScale()), font.getPreferredSize().height));
        p.add(left(flow(font)));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        size.setName("text.size");
        size.setPreferredSize(new Dimension(Math.round(70 * AmadeUI.fontScale()), size.getPreferredSize().height));
        size.addChangeListener(e -> styleChanged());
        row.add(size);
        row.add(new JLabel(" px  "));
        bold.setName("text.bold");
        bold.setFont(bold.getFont().deriveFont(Font.BOLD));
        bold.setToolTipText(msg.get("text.bold"));
        bold.addActionListener(e -> styleChanged());
        italic.setName("text.italic");
        italic.setFont(italic.getFont().deriveFont(Font.ITALIC));
        italic.setToolTipText(msg.get("text.italic"));
        italic.addActionListener(e -> styleChanged());
        row.add(bold);
        row.add(italic);
        p.add(left(row));
        JPanel align = new JPanel(new GridLayout(1, 3, 2, 0));
        ButtonGroup alignGroup = new ButtonGroup();
        for (TextBox.Align a : TextBox.Align.values()) {
            JToggleButton b = new JToggleButton(msg.get("text.align." + key(a)));
            b.setName("text.align." + key(a));
            b.addActionListener(e -> styleChanged());
            alignGroup.add(b);
            align.add(b);
            aligns.put(a, b);
        }
        p.add(left(align));
        p.add(section("paint.colour"));
        p.add(palette());
        p.add(section("text.effect"));
        JPanel effectGrid = new JPanel(new GridLayout(2, 2, 2, 0));
        ButtonGroup effectGroup = new ButtonGroup();
        for (TextBox.Effect effect : TextBox.Effect.values()) {
            JRadioButton b = new JRadioButton(msg.get("text.effect." + key(effect)));
            b.setName("text.effect." + key(effect));
            b.addActionListener(e -> styleChanged());
            effectGroup.add(b);
            effectGrid.add(b);
            effects.put(effect, b);
        }
        p.add(left(effectGrid));
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("text.hint"))));

        JPanel all = new JPanel(new BorderLayout());
        all.add(p, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        textCancel.setText(msg.get("tool.cancel"));
        textCancel.setName("text.cancel");
        textCancel.addActionListener(e -> actions.cancelText());
        textDone.setText(msg.get("text.done"));
        textDone.setName("text.done");
        textDone.addActionListener(e -> actions.finishText());
        buttons.add(textCancel);
        buttons.add(textDone);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        all.add(buttons, BorderLayout.SOUTH);
        showText(null);
        return all;
    }

    JComponent fill() {
        JPanel p = column();
        p.add(section("fill.title"));
        p.add(slider("fill.tolerance", tolerance, toleranceValue, v -> {
            if (!updating) actions.setTolerance(v);
        }));
        p.add(section("paint.colour"));
        p.add(palette());
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("fill.hint"))));
        return top(p);
    }

    JComponent pick() {
        JPanel p = column();
        p.add(section("pick.title"));
        p.add(palette());
        p.add(Box.createVerticalStrut(4));
        p.add(left(wrapped(msg.get("pick.hint"))));
        return top(p);
    }

    // ---------------------------------------------------------------- showing

    void showTool(Tool newTool) {
        tool = newTool;
    }

    void showPaint(PaintSettings settings) {
        paint = settings;
        updating = true;
        try {
            set(brushSize, brushSizeValue, settings.brush().size(), "px");
            set(brushHardness, brushHardnessValue, settings.brush().hardness(), "%");
            set(brushOpacity, brushOpacityValue, settings.brush().opacity(), "%");
            set(eraserSize, eraserSizeValue, settings.eraser().size(), "px");
            set(eraserHardness, eraserHardnessValue, settings.eraser().hardness(), "%");
            shapes.get(settings.shape()).setSelected(true);
            set(lineWidth, lineWidthValue, settings.line().width(), "px");
            dash.setSelectedItem(settings.line().dash());
            fills.get(settings.fill()).setSelected(true);
            for (JRadioButton b : fills.values()) b.setEnabled(settings.shape().closed());
            set(tolerance, toleranceValue, settings.tolerance(), "");
            for (Swatches s : swatches) s.repaint();
        } finally {
            updating = false;
        }
    }

    /** The text being edited: its text and look in the fields; null greys the text field out. */
    void showText(TextBox draft) {
        shownText = draft;
        updating = true;
        try {
            boolean open = draft != null;
            text.setEnabled(open);
            textDone.setEnabled(open);
            textCancel.setEnabled(open);
            if (!open) {
                if (!text.getText().isEmpty()) text.setText("");
                text.setToolTipText(msg.get("text.click-hint"));
                return;
            }
            if (!text.getText().equals(draft.text())) text.setText(draft.text());
            if (indexOf(draft.font()) < 0) font.addItem(draft.font());
            font.setSelectedItem(draft.font());
            size.setValue((double) Math.round(draft.size()));
            bold.setSelected(draft.bold());
            italic.setSelected(draft.italic());
            aligns.get(draft.align()).setSelected(true);
            effects.get(draft.effect()).setSelected(true);
            SwingUtilities.invokeLater(() -> {
                if (text.isShowing() && text.isEnabled()) text.requestFocusInWindow();
            });
        } finally {
            updating = false;
        }
    }

    private int indexOf(String family) {
        for (int i = 0; i < font.getItemCount(); i++) if (font.getItemAt(i).equals(family)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- changes

    private void brushChanged() {
        if (updating) return;
        if (tool == Tool.ERASER) {
            actions.setBrush(new Brush(eraserSize.getValue(), eraserHardness.getValue(), 100));
        } else {
            actions.setBrush(new Brush(brushSize.getValue(), brushHardness.getValue(), brushOpacity.getValue()));
        }
    }

    private void lineChanged() {
        if (updating) return;
        actions.setLine(new LineStyle(lineWidth.getValue(), (LineStyle.Dash) dash.getSelectedItem()));
    }

    private void typed() {
        if (updating || shownText == null) return;
        actions.setText(text.getText());
    }

    private void styleChanged() {
        if (updating) return;
        TextBox.Align align = aligns.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                .findFirst().orElse(TextBox.Align.CENTRE);
        TextBox.Effect effect = effects.entrySet().stream().filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).findFirst().orElse(TextBox.Effect.NONE);
        Color colour = shownText != null ? shownText.colour() : paint.primary();
        String family = (String) font.getSelectedItem();
        float points = ((Number) size.getValue()).floatValue();
        actions.setTextStyle(new TextBox("", family == null ? Font.SANS_SERIF : family,
                Math.clamp(points, TextBox.MIN_SIZE, TextBox.MAX_SIZE), bold.isSelected(), italic.isSelected(),
                align, colour, effect, Color.BLACK, 0, 0, 0));
    }

    // ---------------------------------------------------------------- building blocks

    /** The font families: Java's logical fonts first, then those installed. */
    private static Set<String> families() {
        Set<String> all = new LinkedHashSet<>(List.of(Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED));
        try {
            all.addAll(List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        } catch (RuntimeException e) {
            // Without a font list the logical fonts remain.
        }
        return all;
    }

    /** Fifteen colours and a button for any other; left click sets the first colour, right click the second. */
    private JComponent palette() {
        Swatches s = new Swatches();
        swatches.add(s);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        p.add(s);
        return left(p);
    }

    private final class Swatches extends JComponent {
        private static final int SIZE = 20, GAP = 3, COLUMNS = 8;

        Swatches() {
            int s = Math.round(SIZE * AmadeUI.fontScale());
            setPreferredSize(new Dimension(COLUMNS * (s + GAP), 2 * (s + GAP)));
            setName("palette");
            setToolTipText(msg.get("paint.palette.hint"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int s = Math.round(SIZE * AmadeUI.fontScale());
                    int index = e.getY() / (s + GAP) * COLUMNS + e.getX() / (s + GAP);
                    boolean second = SwingUtilities.isRightMouseButton(e);
                    if (index >= 0 && index < PALETTE.size()) actions.setColour(PALETTE.get(index), second);
                    else if (index == PALETTE.size()) actions.chooseColour(second);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            int s = Math.round(SIZE * AmadeUI.fontScale());
            for (int i = 0; i <= PALETTE.size(); i++) {
                int x = i % COLUMNS * (s + GAP), y = i / COLUMNS * (s + GAP);
                if (i == PALETTE.size()) {
                    AmadeUI.paintBevel(g, x, y, s, s, true, 1);
                    g.setColor(getForeground());
                    g.setFont(getFont().deriveFont(Font.BOLD));
                    g.drawString("…", x + s / 2 - g.getFontMetrics().stringWidth("…") / 2, y + s * 2 / 3);
                    continue;
                }
                Color c = PALETTE.get(i);
                g.setColor(c);
                g.fillRect(x, y, s, s);
                AmadeUI.paintBevel(g, x, y, s, s, false, 1);
                if (c.equals(paint.primary())) {
                    g.setColor(brightness(c) > 0.5 ? Color.BLACK : Color.WHITE);
                    g.drawRect(x + 3, y + 3, s - 7, s - 7);
                }
            }
        }
    }

    static double brightness(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
    }

    private JComponent slider(String key, JSlider slider, JLabel value, IntConsumer change) {
        slider.setName(key);
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        JLabel name = new JLabel(msg.get(key));
        name.setLabelFor(slider);
        top.add(name, BorderLayout.WEST);
        top.add(value, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(slider, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(1, 0, 3, 0));
        String unit = key.endsWith("size") || key.endsWith("width") ? "px" : key.endsWith("tolerance") ? "" : "%";
        slider.addChangeListener(e -> {
            value.setText(slider.getValue() + (unit.isEmpty() ? "" : " " + unit));
            if (!updating) change.accept(slider.getValue());
        });
        return left(p);
    }

    private static void set(JSlider slider, JLabel value, int v, String unit) {
        if (v > slider.getMaximum()) slider.setMaximum(v);
        slider.setValue(v);
        value.setText(v + (unit.isEmpty() ? "" : " " + unit));
    }

    private static String key(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static DefaultListCellRenderer renderer(java.util.function.Function<Object, String> label) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value != null) setText(label.apply(value));
                return this;
            }
        };
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JComponent top(JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(content, BorderLayout.NORTH);
        return p;
    }

    private JComponent section(String key) {
        JLabel l = new JLabel(msg.get(key));
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("controlShadow"))));
        return left(l);
    }

    private static JComponent flow(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        p.add(c);
        return p;
    }

    private static JLabel wrapped(String text) {
        JLabel l = new JLabel("<html><div style=\"width:" + Math.round(160 * AmadeUI.fontScale()) + "px\">"
                + text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
        l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1));
        return l;
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // ---------------------------------------------------------------- for tests

    JSlider brushSizeSlider() {
        return brushSize;
    }

    JTextArea textArea() {
        return text;
    }

    JToggleButton shapeButton(ShapeKind kind) {
        return shapes.get(kind);
    }
}
