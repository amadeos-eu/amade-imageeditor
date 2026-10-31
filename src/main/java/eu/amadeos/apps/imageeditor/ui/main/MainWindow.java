package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.bootstrap.AmadeImageEditorApplication;
import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.commons.desktop.ImageSelection;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The window: toolbar, the picture, the panel of the open tool at the right, and the status bar.
 */
public final class MainWindow extends JFrame implements MainView {
    private static final Pattern RATIO = Pattern.compile("\\s*(\\d{1,4})\\s*[:x×/]\\s*(\\d{1,4})\\s*");

    private final Messages msg;
    private final LocaleSettings locales;
    private final EditorCanvas canvas = new EditorCanvas();
    private final JLabel status = new JLabel(" ");
    private final Map<String, Action> actions = new HashMap<>();
    private final Map<Tool, JToggleButton> toolButtons = new HashMap<>();
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private ToolPanel toolPanel;
    private boolean picture;

    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-imageeditor.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);

        JPanel picturePanel = new JPanel(new BorderLayout());
        picturePanel.setBorder(AmadeUI.sunken());
        picturePanel.add(canvas, BorderLayout.CENTER);
        canvas.setName("canvas");
        canvas.setListener(presenter);
        canvas.setCompareLabels(msg.get("adjust.original"), msg.get("adjust.adjusted"));
        toolPanel = new ToolPanel(msg, presenter);
        toolPanel.setName("tool-panel");
        JPanel centre = new JPanel(new BorderLayout(4, 0));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(picturePanel, BorderLayout.CENTER);
        centre.add(toolPanel, BorderLayout.EAST);

        status.setName("status");
        status.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        statusBar.add(status);

        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        bindKeys(getRootPane());
        setTransferHandler(dropHandler());
        canvas.setTransferHandler(getTransferHandler());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        setSize(Math.round(1100 * AmadeUI.fontScale()), Math.round(760 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(560, 420));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- toolbar and keys

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("file.open", StandardGlyph.OPEN, presenter::open)));
        bar.add(ToolButtons.button(action("file.save", StandardGlyph.SAVE, presenter::save)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("edit.undo", StandardGlyph.UNDO, presenter::undo)));
        bar.add(ToolButtons.button(action("edit.redo", StandardGlyph.REDO, presenter::redo)));
        bar.addSeparator();
        bar.add(toolToggle(Tool.CROP, "image.crop", EditorGlyph.CROP));
        bar.add(toolToggle(Tool.STRAIGHTEN, "image.straighten", EditorGlyph.STRAIGHTEN));
        bar.add(toolToggle(Tool.ADJUST, "image.adjust", EditorGlyph.ADJUST));
        bar.add(ToolButtons.button(action("image.resize", EditorGlyph.RESIZE, presenter::resize)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("image.rotate-left", StandardGlyph.ROTATE_LEFT, presenter::rotateLeft)));
        bar.add(ToolButtons.button(action("image.rotate-right", StandardGlyph.ROTATE_RIGHT, presenter::rotateRight)));
        bar.add(ToolButtons.button(action("image.flip-horizontal", EditorGlyph.FLIP_HORIZONTAL, presenter::flipHorizontally)));
        bar.add(ToolButtons.button(action("image.flip-vertical", EditorGlyph.FLIP_VERTICAL, presenter::flipVertically)));
        bar.add(Box.createHorizontalGlue());
        bar.add(ToolButtons.button(action("view.zoom-out", StandardGlyph.ZOOM_OUT, () -> presenter.zoom(ZoomCommand.OUT))));
        bar.add(ToolButtons.button(action("view.zoom-in", StandardGlyph.ZOOM_IN, () -> presenter.zoom(ZoomCommand.IN))));
        bar.add(ToolButtons.button(action("view.fit", StandardGlyph.ZOOM_FIT, () -> presenter.zoom(ZoomCommand.FIT))));
        bar.add(ToolButtons.button(action("view.actual", StandardGlyph.ZOOM_ACTUAL, () -> presenter.zoom(ZoomCommand.ACTUAL))));
        return bar;
    }

    private JToggleButton toolToggle(Tool tool, String key, ToolIcon.Glyph glyph) {
        JToggleButton button = ToolButtons.toggle(action(key, glyph, () -> presenter.selectTool(tool)));
        toolButtons.put(tool, button);
        return button;
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        AbstractAction action = new AbstractAction(msg.get("tool." + key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.ACTION_COMMAND_KEY, "tool." + key);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get("tool." + key));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        actions.put(key, action);
        return action;
    }

    /** Keys that are not in the menus. */
    private void bindKeys(JRootPane root) {
        Map<KeyStroke, Runnable> keys = new LinkedHashMap<>();
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), presenter::escape);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), presenter::enter);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), presenter::toggleCompare);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                presenter::redo);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), () -> presenter.zoom(ZoomCommand.OUT));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), () -> presenter.zoom(ZoomCommand.OUT));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.IN));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), () -> presenter.zoom(ZoomCommand.OUT));
        int i = 0;
        for (Map.Entry<KeyStroke, Runnable> key : keys.entrySet()) {
            String name = "key." + i++;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key.getKey(), name);
            root.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    key.getValue().run();
                }
            });
        }
    }

    /** A picture dropped from the file manager opens like from the Open dialog. */
    private TransferHandler dropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    presenter.open(files.stream().map(File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showImage(BufferedImage image) {
        canvas.show(image);
    }

    @Override
    public void showNoImage(String message) {
        canvas.showMessage(message);
    }

    @Override
    public void showTitle(String title) {
        setTitle(title);
    }

    @Override
    public void showStatus(String text) {
        status.setText(text);
    }

    @Override
    public void showHistory(Optional<String> undo, Optional<String> redo) {
        menuBar.showHistory(undo, redo);
        actions.get("edit.undo").setEnabled(undo.isPresent());
        actions.get("edit.redo").setEnabled(redo.isPresent());
        actions.get("edit.undo").putValue(Action.SHORT_DESCRIPTION,
                undo.map(s -> msg.format("tool.edit.undo.step", s)).orElse(msg.get("tool.edit.undo")));
        actions.get("edit.redo").putValue(Action.SHORT_DESCRIPTION,
                redo.map(s -> msg.format("tool.edit.redo.step", s)).orElse(msg.get("tool.edit.redo")));
    }

    @Override
    public void enableEditing(boolean hasPicture, boolean busy) {
        picture = hasPicture;
        menuBar.enable(hasPicture, busy);
        boolean editable = hasPicture && !busy;
        for (String key : List.of("file.save", "image.crop", "image.straighten", "image.adjust", "image.resize",
                "image.rotate-left", "image.rotate-right", "image.flip-horizontal", "image.flip-vertical")) {
            actions.get(key).setEnabled(editable);
        }
        for (String key : List.of("view.zoom-out", "view.zoom-in", "view.fit", "view.actual")) {
            actions.get(key).setEnabled(hasPicture);
        }
        actions.get("file.open").setEnabled(!busy);
        if (busy) {
            actions.get("edit.undo").setEnabled(false);
            actions.get("edit.redo").setEnabled(false);
        }
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    @Override
    public void showTool(Tool tool) {
        toolPanel.showTool(tool);
        canvas.showTool(tool);
        menuBar.showTool(tool);
        toolButtons.forEach((t, b) -> b.setSelected(t == tool));
        revalidate();
        repaint();
    }

    @Override
    public void showCrop(Rectangle box, AspectRatio ratio, boolean portrait, double ratioValue, Guides guides) {
        toolPanel.showCrop(box, ratio, portrait, guides);
        canvas.showCrop(box, ratioValue, guides);
    }

    @Override
    public void showStraighten(double degrees, boolean crops) {
        toolPanel.showStraighten(degrees, crops);
        canvas.showStraighten(degrees, crops);
    }

    @Override
    public void showAdjust(Adjustments adjustments, UnaryOperator<BufferedImage> look) {
        toolPanel.showAdjustments(adjustments);
        canvas.showLook(look);
    }

    @Override
    public void showCompare(boolean on) {
        toolPanel.showCompare(on);
        canvas.showCompare(on);
    }

    @Override
    public void zoom(ZoomCommand command) {
        canvas.zoom(command);
    }

    @Override
    public Optional<Path> chooseOpen(Path folder) {
        JFileChooser chooser = new JFileChooser(folder == null ? null : folder.toFile());
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || ImageFormat.isImage(f.toPath());
            }

            @Override
            public String getDescription() {
                return msg.get("dialog.open.filter");
            }
        });
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return Optional.empty();
        }
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<SavePanel.Request> chooseSave(Path suggested, Path original, boolean fromJpeg,
                                                  Function<ImageFormat, SaveOptions> remembered) {
        SavePanel panel = new SavePanel(msg, suggested, original, fromJpeg, remembered);
        while (true) {
            Object[] options = {msg.get("dialog.save.save"), msg.get("dialog.cancel")};
            int answer = JOptionPane.showOptionDialog(this, panel, msg.get("dialog.save.title"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (answer != 0) return Optional.empty();
            Optional<SavePanel.Request> request = panel.result();
            if (request.isPresent()) return request;
            showError(msg.get("error.name"));
        }
    }

    @Override
    public Optional<Edit.Resize> askResize(int width, int height, ResizeQuality quality,
                                           Consumer<ResizeQuality> chosenQuality) {
        ResizePanel panel = new ResizePanel(msg, width, height, quality);
        Object[] options = {msg.get("dialog.resize.resize"), msg.get("dialog.cancel")};
        int answer = JOptionPane.showOptionDialog(this, panel, msg.get("dialog.resize.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (answer != 0) return Optional.empty();
        chosenQuality.accept(panel.quality());
        return panel.result();
    }

    @Override
    public Optional<AspectRatio> askRatio() {
        Object answer = JOptionPane.showInputDialog(this, msg.get("dialog.ratio.text"), msg.get("dialog.ratio.title"),
                JOptionPane.QUESTION_MESSAGE, null, null, "7:5");
        return answer == null ? Optional.empty() : parseRatio(answer.toString());
    }

    /** "7:5", "7 x 5" or "7/5" as a ratio; empty for anything else. */
    static Optional<AspectRatio> parseRatio(String text) {
        Matcher m = RATIO.matcher(text);
        if (!m.matches()) return Optional.empty();
        int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
        return w > 0 && h > 0 ? Optional.of(AspectRatio.of(w, h)) : Optional.empty();
    }

    @Override
    public Unsaved askUnsaved(String fileName) {
        Object[] options = {msg.get("dialog.unsaved.save"), msg.get("dialog.unsaved.discard"), msg.get("dialog.cancel")};
        int answer = JOptionPane.showOptionDialog(this, msg.format("dialog.unsaved.text", fileName),
                msg.get("dialog.unsaved.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[0]);
        return switch (answer) {
            case 0 -> Unsaved.SAVE;
            case 1 -> Unsaved.DISCARD;
            default -> Unsaved.CANCEL;
        };
    }

    @Override
    public boolean confirmReplace(String fileName) {
        return JOptionPane.showConfirmDialog(this, msg.format("dialog.replace.text", fileName),
                msg.get("dialog.replace.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    @Override
    public void copyImage(BufferedImage image) {
        ImageSelection.copy(image);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("window.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-imageeditor " + AmadeImageEditorApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>" + msg.get("dialog.about.libraries") + "</small><br><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        dispose();
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(40);
        return text;
    }

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    EditorCanvas canvas() {
        return canvas;
    }

    ToolPanel toolPanel() {
        return toolPanel;
    }

    String statusText() {
        return status.getText();
    }

    Action toolAction(String key) {
        return actions.get(key);
    }

    AbstractButton toolButton(Tool tool) {
        return toolButtons.get(tool);
    }

    boolean hasPicture() {
        return picture;
    }
}
