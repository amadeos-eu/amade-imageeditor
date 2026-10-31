package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/**
 * The contents of the Save As dialog: folder, file name, format, the format's quality, and which metadata of the
 * original comes along. Changing the format changes the file name's extension and shows that format's last
 * settings.
 */
public final class SavePanel extends JPanel {
    /** Where and how to save. */
    public record Request(Path target, SaveOptions options) {
    }

    private final Messages msg;
    private final Function<ImageFormat, SaveOptions> remembered;
    private final Path original;
    private final boolean fromJpeg;
    private Path folder;
    private final JLabel folderLabel = new JLabel();
    private final JTextField name = new JTextField(18);
    private final JComboBox<ImageFormat> format = new JComboBox<>(ImageFormat.writableFormats().toArray(ImageFormat[]::new));
    private final JLabel qualityValue = new JLabel();
    private final JSlider quality = new JSlider(0, 100, 90);
    private final JCheckBox lossless = new JCheckBox();
    private final JLabel formatNote = new JLabel();
    private final JCheckBox keepCamera = new JCheckBox();
    private final JCheckBox keepLocation = new JCheckBox();
    private final JCheckBox keepDescription = new JCheckBox();
    private final JLabel metadataNote = new JLabel();
    private final JLabel originalNote = new JLabel();
    private final JComponent qualityRow;

    /**
     * @param target     the suggested file
     * @param original   the file that was opened, or null for a picture from the clipboard
     * @param fromJpeg   true if the original is a JPEG, whose metadata can come along
     * @param remembered the last settings per format
     */
    public SavePanel(Messages msg, Path target, Path original, boolean fromJpeg,
                     Function<ImageFormat, SaveOptions> remembered) {
        this.msg = msg;
        this.remembered = remembered;
        this.original = original;
        this.fromJpeg = fromJpeg;
        this.folder = target.toAbsolutePath().getParent();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JButton browse = new JButton(msg.get("save.browse"));
        browse.setName("save.browse");
        browse.addActionListener(e -> browse());
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.add(folderLabel, BorderLayout.CENTER);
        folderRow.add(browse, BorderLayout.EAST);
        add(row(msg.get("save.folder"), folderRow));
        name.setName("save.name");
        name.setText(target.getFileName().toString());
        add(row(msg.get("save.name"), name));

        add(section(msg.get("save.format")));
        format.setName("save.format");
        format.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                ImageFormat f = (ImageFormat) value;
                setText(f.label() + " (." + f.extension() + ")");
                return this;
            }
        });
        add(left(flow(format)));

        JPanel q = new JPanel(new BorderLayout());
        q.add(new JLabel(msg.get("save.quality")), BorderLayout.WEST);
        q.add(qualityValue, BorderLayout.EAST);
        quality.setName("save.quality");
        quality.addChangeListener(e -> qualityValue.setText(String.valueOf(quality.getValue())));
        JPanel qualityPanel = new JPanel(new BorderLayout());
        qualityPanel.add(q, BorderLayout.NORTH);
        qualityPanel.add(quality, BorderLayout.CENTER);
        qualityRow = left(qualityPanel);
        add(qualityRow);
        lossless.setText(msg.get("save.lossless"));
        lossless.setName("save.lossless");
        lossless.addActionListener(e -> quality.setEnabled(!lossless.isSelected()));
        add(left(lossless));
        add(left(formatNote));

        add(section(msg.get("save.metadata")));
        keepCamera.setText(msg.get("save.keep-camera"));
        keepCamera.setName("save.keep-camera");
        keepLocation.setText(msg.get("save.keep-location"));
        keepLocation.setName("save.keep-location");
        keepDescription.setText(msg.get("save.keep-description"));
        keepDescription.setName("save.keep-description");
        keepCamera.addActionListener(e -> keepLocation.setEnabled(metadataPossible() && keepCamera.isSelected()));
        add(left(keepCamera));
        add(left(keepLocation));
        add(left(keepDescription));
        add(left(metadataNote));
        add(Box.createVerticalStrut(6));
        add(left(originalNote));

        ImageFormat initial = ImageFormat.of(target).filter(ImageFormat::writable).orElse(ImageFormat.PNG);
        format.setSelectedItem(initial);
        showOptions(remembered.apply(initial));
        format.addActionListener(e -> formatChanged());
        name.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                showOriginalNote();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                showOriginalNote();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                showOriginalNote();
            }
        });
        showFolder();
        showOriginalNote();
        setPreferredSize(new Dimension(Math.round(360 * AmadeUI.fontScale()), getPreferredSize().height));
    }

    /** Where and how to save; empty if the name is empty. */
    public Optional<Request> result() {
        String text = name.getText().strip();
        if (text.isEmpty() || text.contains("/")) return Optional.empty();
        ImageFormat chosen = selected();
        Path target = folder.resolve(text);
        // A typed extension of another savable format wins over the list; no extension gets the list's.
        Optional<ImageFormat> typed = ImageFormat.of(target).filter(ImageFormat::writable);
        if (typed.isPresent()) chosen = typed.get();
        else target = chosen.withExtension(target);
        SaveOptions options = new SaveOptions(chosen, quality.getValue(), lossless.isSelected(),
                keepCamera.isSelected(), keepLocation.isSelected(), keepDescription.isSelected());
        return Optional.of(new Request(target, options));
    }

    private ImageFormat selected() {
        return (ImageFormat) format.getSelectedItem();
    }

    private void formatChanged() {
        ImageFormat f = selected();
        Path renamed = f.withExtension(Path.of(name.getText().isBlank() ? "picture" : name.getText().strip()));
        name.setText(renamed.getFileName().toString());
        showOptions(remembered.apply(f));
    }

    private void showOptions(SaveOptions options) {
        ImageFormat f = options.format();
        quality.setValue(options.quality());
        qualityValue.setText(String.valueOf(options.quality()));
        qualityRow.setVisible(f.lossy());
        lossless.setVisible(f == ImageFormat.WEBP);
        lossless.setSelected(f == ImageFormat.WEBP && options.lossless());
        quality.setEnabled(!lossless.isSelected());
        formatNote.setText(f == ImageFormat.WEBP ? small(msg.get("save.webp-note"))
                : f == ImageFormat.TIFF ? small(msg.get("save.tiff-note")) : "");
        formatNote.setVisible(!formatNote.getText().isEmpty());
        keepCamera.setSelected(options.keepCamera());
        keepLocation.setSelected(options.keepLocation());
        keepDescription.setSelected(options.keepDescription());
        boolean possible = metadataPossible();
        keepCamera.setEnabled(possible);
        keepLocation.setEnabled(possible && keepCamera.isSelected());
        keepDescription.setEnabled(possible);
        metadataNote.setText(possible ? "" : small(msg.get(fromJpeg ? "save.metadata-jpeg-only"
                : "save.metadata-none")));
        metadataNote.setVisible(!possible);
        revalidate();
    }

    private boolean metadataPossible() {
        return fromJpeg && selected() == ImageFormat.JPEG;
    }

    private void showOriginalNote() {
        if (original == null) {
            originalNote.setText("");
            return;
        }
        Optional<Request> request = result();
        boolean replaces = request.isPresent()
                && request.get().target().toAbsolutePath().normalize().equals(original.toAbsolutePath().normalize());
        originalNote.setName("save.original-note");
        originalNote.setText(small(msg.format(replaces ? "save.replaces" : "save.keeps",
                original.getFileName().toString())));
    }

    private void showFolder() {
        folderLabel.setText(folder.toString());
        folderLabel.setToolTipText(folder.toString());
        showOriginalNote();
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(folder.toFile());
        chooser.setDialogTitle(msg.get("save.browse.title"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showDialog(this, msg.get("save.browse.choose")) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            folder = chooser.getSelectedFile().toPath();
            showFolder();
        }
    }

    private static String small(String text) {
        return "<html><div style=\"width:" + Math.round(260 * AmadeUI.fontScale()) + "px\"><small>"
                + text.replace("&", "&amp;").replace("<", "&lt;") + "</small></div></html>";
    }

    private static JComponent section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, javax.swing.UIManager.getColor("controlShadow"))));
        return left(l);
    }

    private static JComponent row(String label, JComponent field) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        JLabel k = new JLabel(label);
        k.setLabelFor(field);
        k.setPreferredSize(new Dimension(Math.round(80 * AmadeUI.fontScale()), k.getPreferredSize().height));
        r.add(k, BorderLayout.WEST);
        r.add(field, BorderLayout.CENTER);
        r.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return left(r);
    }

    private static JComponent flow(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        p.add(c);
        return p;
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // ---------------------------------------------------------------- for tests

    JTextField nameField() {
        return name;
    }

    JComboBox<ImageFormat> formatBox() {
        return format;
    }

    JCheckBox keepLocationBox() {
        return keepLocation;
    }

    JCheckBox keepCameraBox() {
        return keepCamera;
    }

    JSlider qualitySlider() {
        return quality;
    }

    JLabel originalNoteLabel() {
        return originalNote;
    }
}
