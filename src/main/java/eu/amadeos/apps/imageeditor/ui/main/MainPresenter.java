package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.History;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.apps.imageeditor.repository.EditorPreferences;
import eu.amadeos.apps.imageeditor.service.ImageLoader;
import eu.amadeos.apps.imageeditor.service.ImageOperations;
import eu.amadeos.apps.imageeditor.service.ImageSaver;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The editor's logic: opening, the open tool and its settings, applying steps to the history, undo and redo, and
 * saving. It knows the window only as a {@link MainView}; pictures are opened, changed and saved in the
 * background, and the results come back on the {@code ui} executor.
 */
public final class MainPresenter implements ToolPanel.Actions, EditorCanvas.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);

    /** What the presenter works with besides the window. */
    public record Services(EditorPreferences preferences, ImageLoader loader, ImageSaver saver, Executor background,
                           Executor ui, Path pictures, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final Services services;

    private ImageLoader.Loaded loaded;
    private History history;
    /** Where the picture is saved by Save: the opened file, or the last Save As target. */
    private Path file;
    private ImageFormat fileFormat;
    private boolean busy;
    /** A save that waits for the open tool's step to be applied. */
    private Runnable pendingSave, pendingSaveAs;
    private double scale = 1;

    private Tool tool = Tool.NONE;
    private Rectangle crop;
    private AspectRatio ratio = AspectRatio.FREE;
    private boolean portrait;
    private Guides guides = Guides.THIRDS;
    private double degrees;
    private boolean straightenCrops;
    private Adjustments adjustments = Adjustments.NONE;
    private boolean compare;

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.services = services;
        this.straightenCrops = services.preferences().straightenCrops();
    }

    /** Opens the file from the command line, if any. */
    public void start(List<Path> paths) {
        update();
        if (paths.isEmpty()) {
            view.showNoImage(msg.get("empty.hint"));
            return;
        }
        load(paths.getFirst());
    }

    // ---------------------------------------------------------------- open and save

    public void open() {
        if (busy) return;
        whenSettled(() -> {
            Path folder = file != null ? file.toAbsolutePath().getParent()
                    : services.preferences().lastFolder().filter(Files::isDirectory).orElse(services.pictures());
            view.chooseOpen(folder).ifPresent(this::load);
        });
    }

    /** Files dropped on the window: the first picture opens. */
    public void open(List<Path> paths) {
        if (busy || paths.isEmpty()) return;
        whenSettled(() -> load(paths.getFirst()));
    }

    private void load(Path path) {
        if (Files.isDirectory(path)) {
            view.showError(msg.format("error.folder", path.getFileName()));
            return;
        }
        run(() -> services.loader().load(path), opened -> {
            loaded = opened;
            history = new History(opened.image(), ImageOperations::apply);
            file = path;
            fileFormat = opened.format();
            tool = Tool.NONE;
            view.showTool(Tool.NONE);
            view.showImage(opened.image());
            Path folder = path.toAbsolutePath().getParent();
            if (folder != null) services.preferences().setLastFolder(folder);
            update();
        }, e -> {
            if (loaded == null) view.showNoImage(msg.get("empty.hint"));
            view.showError(e instanceof ImageLoader.UnsupportedFormatException
                    ? msg.format("error.format", path.getFileName())
                    : msg.format("error.open", path.getFileName(), e.getMessage()));
        });
    }

    /**
     * Writes to the file again, in its format, with the last settings of that format. Formats the editor cannot
     * write go to Save As.
     */
    public void save() {
        save(() -> {
        });
    }

    private void save(Runnable after) {
        if (busy || history == null) return;
        if (tool != Tool.NONE) {
            // The open tool's change belongs to what is saved: apply it, then save.
            applyTool();
            if (busy) {
                pendingSave = after;
                return;
            }
        }
        if (!fileFormat.writable() || !fileFormat.matches(file)) {
            saveAs(after);
            return;
        }
        write(file, services.preferences().saveOptions(fileFormat), after);
    }

    public void saveAs() {
        saveAs(() -> {
        });
    }

    private void saveAs(Runnable after) {
        if (busy || history == null) return;
        if (tool != Tool.NONE) {
            applyTool();
            if (busy) {
                pendingSaveAs = after;
                return;
            }
        }
        Path suggested = fileFormat.writable() ? file : ImageFormat.PNG.withExtension(file);
        boolean fromJpeg = loaded.format() == ImageFormat.JPEG;
        Optional<SavePanel.Request> request = view.chooseSave(suggested, loaded.file(), fromJpeg,
                services.preferences()::saveOptions);
        if (request.isEmpty()) return;
        Path target = request.get().target();
        boolean sameAsOpen = target.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize());
        if (Files.exists(target) && !sameAsOpen && !view.confirmReplace(target.getFileName().toString())) return;
        services.preferences().setSaveOptions(request.get().options());
        write(target, request.get().options(), after);
    }

    private void write(Path target, SaveOptions options, Runnable after) {
        BufferedImage image = history.current();
        Optional<Orientation> turns = history.onlyTurns();
        ImageSaver.Source source = new ImageSaver.Source(loaded.file(), loaded.format(), loaded.stored());
        run(() -> services.saver().save(image, turns, source, target, options), result -> {
            history.markSaved();
            file = target;
            fileFormat = options.format();
            if (result.trimmed()) view.showInfo(msg.get("info.trimmed"));
            update();
            after.run();
        }, e -> view.showError(e instanceof ImageSaver.CwebpMissingException ? msg.get("error.cwebp")
                : msg.format("error.save", target.getFileName(), e.getMessage())));
    }

    /** Runs {@code then} once unsaved changes are saved or given up; not at all if the user cancels. */
    private void whenSettled(Runnable then) {
        if (history == null || !history.modified()) {
            then.run();
            return;
        }
        switch (view.askUnsaved(file.getFileName().toString())) {
            case SAVE -> save(() -> {
                if (!history.modified()) then.run();
            });
            case DISCARD -> then.run();
            case CANCEL -> {
            }
        }
    }

    public void exit() {
        if (busy) return;
        whenSettled(() -> {
            view.close();
            services.exit().run();
        });
    }

    // ---------------------------------------------------------------- history

    public void undo() {
        if (busy || history == null || !history.canUndo()) return;
        closeTool();
        run(history::undo, this::edited, this::failed);
    }

    public void redo() {
        if (busy || history == null || !history.canRedo()) return;
        closeTool();
        run(history::redo, this::edited, this::failed);
    }

    private void push(Edit edit) {
        if (busy || history == null) return;
        run(() -> history.push(edit), this::edited, this::failed);
    }

    private void edited(BufferedImage image) {
        view.showImage(image);
        if (tool == Tool.CROP) resetCrop();
        update();
        if (pendingSave != null) {
            Runnable after = pendingSave;
            pendingSave = null;
            save(after);
        } else if (pendingSaveAs != null) {
            Runnable after = pendingSaveAs;
            pendingSaveAs = null;
            saveAs(after);
        }
    }

    private void failed(Exception e) {
        pendingSave = null;
        pendingSaveAs = null;
        LOG.warn("Editing failed", e);
        view.showError(msg.format("error.edit", e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    // ---------------------------------------------------------------- quick steps

    public void rotateLeft() {
        push(new Edit.Turn(Orientation.ROTATE_270));
    }

    public void rotateRight() {
        push(new Edit.Turn(Orientation.ROTATE_90));
    }

    public void flipHorizontally() {
        push(new Edit.Turn(Orientation.MIRROR));
    }

    public void flipVertically() {
        push(new Edit.Turn(Orientation.FLIP));
    }

    @Override
    public void autoLevels() {
        if (tool == Tool.ADJUST) closeTool();
        push(new Edit.AutoLevels());
    }

    public void resize() {
        if (busy || history == null) return;
        if (tool != Tool.NONE) {
            applyTool();
            if (busy) return;
        }
        BufferedImage image = history.current();
        view.askResize(image.getWidth(), image.getHeight(), services.preferences().resizeQuality(),
                services.preferences()::setResizeQuality).ifPresent(this::push);
    }

    public void copyImage() {
        if (history != null) view.copyImage(history.current());
    }

    // ---------------------------------------------------------------- tools

    /** Opens a tool's panel; the same tool again closes it without applying. */
    public void selectTool(Tool newTool) {
        if (busy || history == null) return;
        if (newTool == tool) {
            cancelTool();
            return;
        }
        tool = newTool;
        switch (newTool) {
            case CROP -> resetCrop();
            case STRAIGHTEN -> {
                degrees = 0;
                view.showStraighten(0, straightenCrops);
            }
            case ADJUST -> {
                adjustments = Adjustments.NONE;
                compare = false;
                view.showAdjust(adjustments, null);
                view.showCompare(false);
            }
            case NONE -> {
            }
        }
        view.showTool(newTool);
        update();
    }

    @Override
    public void cancelTool() {
        if (tool == Tool.ADJUST && !adjustments.neutral()) {
            // In Adjust the left button is "Reset": back to zero, the panel stays open.
            adjustments = Adjustments.NONE;
            view.showAdjust(adjustments, null);
            return;
        }
        closeTool();
    }

    private void closeTool() {
        if (tool == Tool.NONE) return;
        tool = Tool.NONE;
        view.showTool(Tool.NONE);
        update();
    }

    @Override
    public void applyTool() {
        if (busy || history == null) return;
        Optional<Edit> edit = switch (tool) {
            case CROP -> crop == null || crop.equals(whole()) ? Optional.empty() : Optional.of(new Edit.Crop(crop));
            case STRAIGHTEN -> degrees == 0 ? Optional.empty() : Optional.of(new Edit.Straighten(degrees, straightenCrops));
            case ADJUST -> adjustments.neutral() ? Optional.empty() : Optional.of(new Edit.Adjust(adjustments));
            case NONE -> Optional.empty();
        };
        closeTool();
        edit.ifPresent(this::push);
    }

    // crop

    private void resetCrop() {
        BufferedImage image = history.current();
        crop = ratio.free() ? whole() : CropBox.centred(image.getWidth(), image.getHeight(), ratioValue(), 1);
        showCrop();
    }

    private Rectangle whole() {
        BufferedImage image = history.current();
        return new Rectangle(0, 0, image.getWidth(), image.getHeight());
    }

    private double ratioValue() {
        BufferedImage image = history.current();
        return ratio.value(image.getWidth(), image.getHeight(), portrait);
    }

    private void showCrop() {
        view.showCrop(crop, ratio, portrait, ratioValue(), guides);
        updateStatus();
    }

    @Override
    public void setRatio(AspectRatio newRatio) {
        if (history == null) return;
        ratio = newRatio;
        BufferedImage image = history.current();
        if (newRatio.equals(AspectRatio.ORIGINAL)) portrait = image.getHeight() > image.getWidth();
        crop = CropBox.withRatio(crop == null ? whole() : crop, ratioValue(), image.getWidth(), image.getHeight());
        showCrop();
    }

    @Override
    public void customRatio() {
        Optional<AspectRatio> asked = view.askRatio();
        if (asked.isPresent()) {
            AspectRatio r = asked.get();
            portrait = r.height() > r.width();
            setRatio(AspectRatio.of(Math.max(r.width(), r.height()), Math.min(r.width(), r.height())));
        } else {
            showCrop();
        }
    }

    @Override
    public void setPortrait(boolean on) {
        if (history == null) return;
        portrait = on;
        BufferedImage image = history.current();
        crop = CropBox.withRatio(crop == null ? whole() : crop, ratioValue(), image.getWidth(), image.getHeight());
        showCrop();
    }

    @Override
    public void typeCrop(CropBox.Field field, int value) {
        if (history == null || crop == null) return;
        BufferedImage image = history.current();
        crop = CropBox.typed(crop, field, value, ratioValue(), image.getWidth(), image.getHeight());
        showCrop();
    }

    @Override
    public void setGuides(Guides newGuides) {
        guides = newGuides;
        showCrop();
    }

    @Override
    public void cropChanged(Rectangle box) {
        crop = new Rectangle(box);
        // Only the numbers: the canvas has drawn the frame already.
        view.showCrop(crop, ratio, portrait, ratioValue(), guides);
        updateStatus();
    }

    // straighten

    @Override
    public void setAngle(double newDegrees) {
        degrees = Math.clamp(Math.round(newDegrees * 10) / 10.0, -Edit.Straighten.LIMIT, Edit.Straighten.LIMIT);
        view.showStraighten(degrees, straightenCrops);
        updateStatus();
    }

    @Override
    public void levelLine(double newDegrees) {
        setAngle(newDegrees);
    }

    @Override
    public void setStraightenCrops(boolean crop) {
        straightenCrops = crop;
        services.preferences().setStraightenCrops(crop);
        view.showStraighten(degrees, straightenCrops);
    }

    // adjust

    @Override
    public void setAdjustments(Adjustments newAdjustments) {
        adjustments = newAdjustments;
        Adjustments shown = newAdjustments;
        UnaryOperator<BufferedImage> look = shown.neutral() ? null : image -> ImageOperations.adjust(image, shown);
        view.showAdjust(shown, look);
    }

    @Override
    public void setCompare(boolean on) {
        compare = on;
        view.showCompare(on);
    }

    /** Space held down in Adjust shows the original. */
    public void toggleCompare() {
        if (tool == Tool.ADJUST) setCompare(!compare);
    }

    // ---------------------------------------------------------------- view

    public void zoom(ZoomCommand command) {
        view.zoom(command);
    }

    @Override
    public void scaleChanged(double newScale) {
        scale = newScale;
        updateStatus();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-imageeditor"));
        }
    }

    public void about() {
        view.showAbout();
    }

    /** Escape closes the open tool without applying it. */
    public void escape() {
        if (tool != Tool.NONE) closeTool();
    }

    /** Enter applies the open tool. */
    public void enter() {
        if (tool != Tool.NONE) applyTool();
    }

    private void update() {
        boolean picture = history != null;
        view.enableEditing(picture, busy);
        if (!picture) {
            view.showTitle(msg.get("window.title"));
            view.showHistory(Optional.empty(), Optional.empty());
            view.showStatus(" ");
            return;
        }
        String name = file.getFileName().toString();
        view.showTitle(msg.format("window.title.picture", history.modified() ? name + " *" : name));
        view.showHistory(history.undoable().map(e -> msg.get(e.key())), history.redoable().map(e -> msg.get(e.key())));
        updateStatus();
    }

    private void updateStatus() {
        if (history == null) return;
        BufferedImage image = history.current();
        NumberFormat numbers = NumberFormat.getIntegerInstance(msg.locale());
        String base = msg.format("status", numbers.format(image.getWidth()), numbers.format(image.getHeight()),
                fileFormat.label(), numbers.format(Math.round(scale * 100)));
        String extra = switch (tool) {
            case CROP -> crop == null ? "" : msg.format("status.crop", numbers.format(crop.width),
                    numbers.format(crop.height), numbers.format(crop.x), numbers.format(crop.y));
            case STRAIGHTEN -> msg.format("status.straighten",
                    String.format(msg.locale(), "%+.1f", degrees));
            case ADJUST -> msg.get("status.adjust");
            case NONE -> "";
        };
        view.showStatus(extra.isEmpty() ? base : base + "   " + extra);
    }

    // ---------------------------------------------------------------- background work

    /** Runs work in the background; its result or failure comes back on the UI executor. */
    private <T> void run(Callable<T> work, Consumer<T> done, Consumer<Exception> failed) {
        busy = true;
        view.enableEditing(history != null, true);
        services.background().execute(() -> {
            try {
                T result = work.call();
                services.ui().execute(() -> {
                    busy = false;
                    done.accept(result);
                    update();
                });
            } catch (Exception | OutOfMemoryError e) {
                Exception reported = e instanceof Exception ex ? ex : new IOException(msg.get("error.memory"));
                services.ui().execute(() -> {
                    busy = false;
                    failed.accept(reported);
                    update();
                });
            }
        });
    }

    // ---------------------------------------------------------------- for tests

    History history() {
        return history;
    }

    Tool tool() {
        return tool;
    }

    Rectangle cropBox() {
        return crop == null ? null : new Rectangle(crop);
    }

    double degrees() {
        return degrees;
    }

    Path file() {
        return file;
    }
}
