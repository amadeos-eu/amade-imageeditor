package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** File, Edit, Image, Layer, Tools, View, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    private final Messages msg;
    private final Map<String, JMenuItem> items = new HashMap<>();

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item("menu.file.open", key(KeyEvent.VK_O, CTRL), presenter::open));
        file.add(item("menu.file.save", key(KeyEvent.VK_S, CTRL), presenter::save));
        file.add(item("menu.file.save-as", key(KeyEvent.VK_S, CTRL | SHIFT), presenter::saveAs));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(item("menu.edit.undo", key(KeyEvent.VK_Z, CTRL), presenter::undo));
        edit.add(item("menu.edit.redo", key(KeyEvent.VK_Y, CTRL), presenter::redo));
        edit.addSeparator();
        edit.add(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL), presenter::copyImage));
        edit.add(item("menu.edit.paste", key(KeyEvent.VK_V, CTRL), presenter::pasteAsLayer));

        JMenu image = menu("menu.image");
        image.add(check("menu.image.crop", key(KeyEvent.VK_K, CTRL), () -> presenter.selectTool(Tool.CROP)));
        image.add(check("menu.image.straighten", key(KeyEvent.VK_T, CTRL), () -> presenter.selectTool(Tool.STRAIGHTEN)));
        image.add(check("menu.image.adjust", key(KeyEvent.VK_J, CTRL), () -> presenter.selectTool(Tool.ADJUST)));
        image.add(item("menu.image.resize", key(KeyEvent.VK_E, CTRL), presenter::resize));
        image.add(item("menu.image.auto-levels", null, presenter::autoLevels));
        image.addSeparator();
        image.add(item("menu.image.rotate-left", key(KeyEvent.VK_L, CTRL), presenter::rotateLeft));
        image.add(item("menu.image.rotate-right", key(KeyEvent.VK_R, CTRL), presenter::rotateRight));
        image.add(item("menu.image.flip-horizontal", null, presenter::flipHorizontally));
        image.add(item("menu.image.flip-vertical", null, presenter::flipVertically));

        JMenu layer = menu("menu.layer");
        layer.add(item("menu.layer.new", key(KeyEvent.VK_N, CTRL | SHIFT), presenter::newLayer));
        layer.add(item("menu.layer.duplicate", key(KeyEvent.VK_D, CTRL | SHIFT), presenter::duplicateLayer));
        layer.add(item("menu.layer.picture", null, presenter::pictureAsLayer));
        layer.add(item("menu.layer.paste", key(KeyEvent.VK_V, CTRL | SHIFT), presenter::pasteAsLayer));
        layer.addSeparator();
        layer.add(item("menu.layer.rename", key(KeyEvent.VK_F2, 0), presenter::renameLayer));
        layer.add(item("menu.layer.up", key(KeyEvent.VK_PAGE_UP, CTRL), presenter::layerUp));
        layer.add(item("menu.layer.down", key(KeyEvent.VK_PAGE_DOWN, CTRL), presenter::layerDown));
        layer.addSeparator();
        layer.add(item("menu.layer.merge-down", key(KeyEvent.VK_M, CTRL), presenter::mergeDown));
        layer.add(item("menu.layer.flatten", null, presenter::flatten));
        layer.addSeparator();
        layer.add(item("menu.layer.delete", null, presenter::deleteLayer));

        // The letters are shown in the tooltips and bound on the picture, not as accelerators here: an
        // accelerator B would also fire while typing a text.
        JMenu tools = menu("menu.tools");
        tools.add(check("menu.tools.move", null, () -> presenter.selectTool(Tool.MOVE)));
        tools.add(check("menu.tools.brush", null, () -> presenter.selectTool(Tool.BRUSH)));
        tools.add(check("menu.tools.eraser", null, () -> presenter.selectTool(Tool.ERASER)));
        tools.add(check("menu.tools.shapes", null, () -> presenter.selectTool(Tool.SHAPES)));
        tools.add(check("menu.tools.text", null, () -> presenter.selectTool(Tool.TEXT)));
        tools.add(check("menu.tools.fill", null, () -> presenter.selectTool(Tool.FILL)));
        tools.add(check("menu.tools.pick", null, () -> presenter.selectTool(Tool.PICK)));
        tools.addSeparator();
        tools.add(item("menu.tools.swap", null, presenter::swapColours));
        tools.add(item("menu.tools.default-colours", null, presenter::defaultColours));

        JMenu view = menu("menu.view");
        view.add(item("menu.view.zoom-in", key(KeyEvent.VK_PLUS, CTRL), () -> presenter.zoom(ZoomCommand.IN)));
        view.add(item("menu.view.zoom-out", key(KeyEvent.VK_MINUS, CTRL), () -> presenter.zoom(ZoomCommand.OUT)));
        view.add(item("menu.view.fit", key(KeyEvent.VK_0, CTRL), () -> presenter.zoom(ZoomCommand.FIT)));
        view.add(item("menu.view.actual", key(KeyEvent.VK_1, CTRL), () -> presenter.zoom(ZoomCommand.ACTUAL)));
        view.addSeparator();
        view.add(check("menu.view.layers", key(KeyEvent.VK_F7, 0), presenter::toggleLayersPanel));

        JMenu options = menu("menu.options");
        JCheckBoxMenuItem newLayer = check("menu.options.paint-new-layer", null, () -> {
        });
        newLayer.setSelected(presenter.paintOnNewLayer());
        newLayer.addActionListener(e -> presenter.setPaintOnNewLayer(newLayer.isSelected()));
        options.add(newLayer);
        if (locales != null) {
            options.addSeparator();
            options.add(languageMenu(presenter, locales));
        }

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
    }

    // --8<-- [start:enable]
    /** Greys out what needs a picture, and everything that changes it while a step runs. */
    void enable(boolean picture, boolean busy) {
        boolean editable = picture && !busy;
        for (String key : List.of("menu.file.save", "menu.file.save-as", "menu.image.crop", "menu.image.straighten",
                "menu.image.adjust", "menu.image.resize", "menu.image.auto-levels", "menu.image.rotate-left",
                "menu.image.rotate-right", "menu.image.flip-horizontal", "menu.image.flip-vertical",
                "menu.edit.paste", "menu.layer.new", "menu.layer.duplicate", "menu.layer.picture", "menu.layer.paste",
                "menu.layer.rename", "menu.layer.up", "menu.layer.down", "menu.layer.merge-down", "menu.layer.flatten",
                "menu.layer.delete")) {
            items.get(key).setEnabled(editable);
        }
        for (String key : List.of("menu.tools.move", "menu.tools.brush", "menu.tools.eraser", "menu.tools.shapes",
                "menu.tools.text", "menu.tools.fill", "menu.tools.pick", "menu.tools.swap",
                "menu.tools.default-colours")) {
            items.get(key).setEnabled(picture);
        }
        for (String key : List.of("menu.edit.copy", "menu.view.zoom-in", "menu.view.zoom-out", "menu.view.fit",
                "menu.view.actual")) {
            items.get(key).setEnabled(picture);
        }
        items.get("menu.file.open").setEnabled(!busy);
    }
    // --8<-- [end:enable]

    /** "Undo Crop", "Redo Resize"; plain "Undo" when there is nothing. */
    void showHistory(Optional<String> undo, Optional<String> redo) {
        label("menu.edit.undo", undo);
        label("menu.edit.redo", redo);
    }

    private void label(String key, Optional<String> step) {
        JMenuItem item = items.get(key);
        Mnemonics.apply(item, step.map(s -> msg.format(key + ".step", s)).orElse(msg.get(key)));
        item.setEnabled(step.isPresent());
    }

    /** Ticks the open tool. */
    void showTool(Tool tool) {
        items.get("menu.image.crop").setSelected(tool == Tool.CROP);
        items.get("menu.image.straighten").setSelected(tool == Tool.STRAIGHTEN);
        items.get("menu.image.adjust").setSelected(tool == Tool.ADJUST);
        items.get("menu.tools.move").setSelected(tool == Tool.MOVE);
        items.get("menu.tools.brush").setSelected(tool == Tool.BRUSH);
        items.get("menu.tools.eraser").setSelected(tool == Tool.ERASER);
        items.get("menu.tools.shapes").setSelected(tool == Tool.SHAPES);
        items.get("menu.tools.text").setSelected(tool == Tool.TEXT);
        items.get("menu.tools.fill").setSelected(tool == Tool.FILL);
        items.get("menu.tools.pick").setSelected(tool == Tool.PICK);
    }

    void showLayersPanel(boolean on) {
        items.get("menu.view.layers").setSelected(on);
    }

    JMenuItem item(String name) {
        return items.get(name);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }

    /** A tool item: ticked while its panel is open; choosing it again closes the panel. */
    private JCheckBoxMenuItem check(String key, KeyStroke accelerator, Runnable action) {
        JCheckBoxMenuItem item = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }
}
