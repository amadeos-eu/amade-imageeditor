package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.CropBox;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.History;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.LineStyle;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.PaintSettings;
import eu.amadeos.apps.imageeditor.domain.Picture;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.apps.imageeditor.domain.ShapeFill;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import eu.amadeos.apps.imageeditor.repository.EditorPreferences;
import eu.amadeos.apps.imageeditor.service.ImageLoader;
import eu.amadeos.apps.imageeditor.service.ImageOperations;
import eu.amadeos.apps.imageeditor.service.ImageSaver;
import eu.amadeos.apps.imageeditor.service.PictureOperations;
import eu.amadeos.apps.imageeditor.service.TextRenderer;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The editor's logic: opening, the open tool and its settings, applying steps to the history, undo and redo, the
 * layers, and saving. It knows the window only as a {@link MainView}; pictures are opened, changed and saved in the
 * background, and the results come back on the {@code ui} executor. Steps that come while one is still being
 * applied (quick brush strokes) wait in a queue, so none is lost.
 */
public final class MainPresenter implements ToolPanel.Actions, EditorCanvas.Listener, LayersPanel.Actions {
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

    /** The layer painting, adjusting and the layer commands go to. */
    private int active;
    private PaintSettings paint = PaintSettings.DEFAULT;
    /** The shape drawn last, still with handles; null when there is none. */
    private List<Dot> pendingShape;
    private boolean pendingSecond;
    /** The text being typed, and the text layer it replaces (-1 for a new text). */
    private TextBox draft;
    private int draftLayer = -1;
    /** The look of the next text; its size follows the picture until the user sets one. */
    private TextBox textStyle = new TextBox("", "SansSerif", 48, true, false, TextBox.Align.CENTRE, Color.BLACK,
            TextBox.Effect.SHADOW, Color.WHITE, 0, 0, 0);
    private boolean textSizeChosen;
    private boolean layersPanel = true;
    /** Steps waiting for the one being applied. */
    private final Deque<Supplier<Optional<Edit>>> queue = new ArrayDeque<>();
    /** The file whose "cannot hold layers" question was answered, and whether an .ora is kept next to it. */
    private Path layersAnsweredFor;
    private boolean layersKept;

    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, Services services) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.services = services;
        this.straightenCrops = services.preferences().straightenCrops();
        this.paint = services.preferences().paintSettings(PaintSettings.DEFAULT);
    }

    /** Opens the file from the command line, if any. */
    public void start(List<Path> paths) {
        view.showPaint(paint);
        view.showLayersPanel(layersPanel);
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
            history = new History(opened.picture(), PictureOperations::apply);
            file = path;
            fileFormat = opened.format();
            active = opened.picture().size() - 1;
            layersAnsweredFor = null;
            queue.clear();
            clearPending();
            textSizeChosen = false;
            tool = Tool.NONE;
            view.showTool(Tool.NONE);
            view.showImage(opened.image());
            showLayers();
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
        boolean keepLayers = false;
        if (picture().layered() && !fileFormat.layers()) {
            if (file.equals(layersAnsweredFor)) {
                keepLayers = layersKept;
            } else {
                switch (view.askLayers(file.getFileName().toString(), layersFile().getFileName().toString())) {
                    case MERGED -> keepLayers = false;
                    case MERGED_AND_LAYERS -> keepLayers = true;
                    case SAVE_AS -> {
                        saveAs(after);
                        return;
                    }
                    case CANCEL -> {
                        return;
                    }
                }
                layersAnsweredFor = file;
                layersKept = keepLayers;
            }
        }
        if (keepLayers) {
            Path ora = layersFile();
            write(file, services.preferences().saveOptions(fileFormat), false,
                    () -> write(ora, services.preferences().saveOptions(ImageFormat.ORA), true, after));
        } else {
            write(file, services.preferences().saveOptions(fileFormat), false, after);
        }
    }

    /** The OpenRaster file next to the picture, which keeps the layers when the picture is saved merged. */
    private Path layersFile() {
        return ImageFormat.ORA.withExtension(file);
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
        // A picture with layers is offered as OpenRaster, which keeps them.
        Path suggested = picture().layered() ? ImageFormat.ORA.withExtension(file)
                : fileFormat.writable() ? file : ImageFormat.PNG.withExtension(file);
        boolean fromJpeg = loaded.format() == ImageFormat.JPEG;
        Optional<SavePanel.Request> request = view.chooseSave(suggested, loaded.file(), fromJpeg,
                picture().layered(), services.preferences()::saveOptions);
        if (request.isEmpty()) return;
        Path target = request.get().target();
        boolean sameAsOpen = target.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize());
        if (Files.exists(target) && !sameAsOpen && !view.confirmReplace(target.getFileName().toString())) return;
        services.preferences().setSaveOptions(request.get().options());
        write(target, request.get().options(), false, after);
    }

    /** Saves the picture; a {@code copy} (the .ora kept next to a merged JPEG) leaves the picture's file as it is. */
    private void write(Path target, SaveOptions options, boolean copy, Runnable after) {
        Picture picture = picture();
        Optional<Orientation> turns = history.onlyTurns();
        ImageSaver.Source source = new ImageSaver.Source(loaded.file(), loaded.format(), loaded.stored());
        run(() -> services.saver().save(picture, turns, source, target, options), result -> {
            if (!copy) {
                // The .ora kept next to a merged JPEG is a copy: the picture stays the JPEG.
                history.markSaved();
                file = target;
                fileFormat = options.format();
            }
            if (result.trimmed()) view.showInfo(msg.get("info.trimmed"));
            update();
            after.run();
        }, e -> view.showError(e instanceof ImageSaver.CwebpMissingException ? msg.get("error.cwebp")
                : msg.format("error.save", target.getFileName(), e.getMessage())));
    }

    // --8<-- [start:settled]
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
    // --8<-- [end:settled]

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
        leaveForHistory();
        run(history::undo, this::edited, this::failed);
    }

    public void redo() {
        if (busy || history == null || !history.canRedo()) return;
        leaveForHistory();
        run(history::redo, this::edited, this::failed);
    }

    /** Before undo and redo: Crop, Straighten and Adjust close; a shape or text not yet painted is dropped. */
    private void leaveForHistory() {
        if (tool.paints()) clearPending();
        else closeTool();
    }

    // --8<-- [start:push]
    private void push(Edit edit) {
        push(() -> Optional.of(edit));
    }

    /**
     * Applies a step in the background. A step that comes while another is applied waits; it is made only when
     * its turn comes, so that it sees the picture the steps before it left (the layer a stroke goes to, say).
     */
    private void push(Supplier<Optional<Edit>> next) {
        if (history == null) return;
        if (busy) {
            queue.add(next);
            return;
        }
        Optional<Edit> edit = next.get();
        if (edit.isEmpty()) {
            runQueued();
            return;
        }
        Picture before = history.current();
        run(() -> history.push(edit.get()), picture -> {
            active = activeAfter(edit.get(), before, picture);
            edited(picture);
        }, this::failed);
    }
    // --8<-- [end:push]

    private void runQueued() {
        Supplier<Optional<Edit>> next = queue.poll();
        if (next != null) push(next);
    }

    // --8<-- [start:active]
    /** The layer that is active after a step: a new layer, the moved one, or the one below a deleted one. */
    private int activeAfter(Edit edit, Picture before, Picture after) {
        int last = after.size() - 1;
        int next = switch (edit) {
            case Edit.NewLayer n -> n.above() + 1;
            case Edit.Text t -> t.replace() ? t.layer() : t.layer() + 1;
            case Edit.AddPicture ignored -> last;
            case Edit.DuplicateLayer d -> d.layer() + 1;
            case Edit.DeleteLayer d -> d.layer() <= active ? active - 1 : active;
            case Edit.OrderLayer o -> o.layer() == active ? o.to() : active;
            case Edit.MergeDown m -> m.layer() - 1;
            case Edit.Flatten ignored -> 0;
            case Edit.Stroke st -> st.target().makesLayer() ? st.target().layer() + 1 : active;
            case Edit.Shape sh -> sh.target().makesLayer() ? sh.target().layer() + 1 : active;
            default -> before.size() == after.size() ? active : last;
        };
        return Math.clamp(next, 0, last);
    }
    // --8<-- [end:active]

    private void edited(Picture picture) {
        active = Math.clamp(active, 0, picture.size() - 1);
        view.showImage(picture.composite());
        showLayers();
        if (tool == Tool.CROP) resetCrop();
        if (tool == Tool.MOVE || tool == Tool.TEXT && draft == null) view.showOverlay(null);
        update();
        if (pendingSave != null) {
            Runnable after = pendingSave;
            pendingSave = null;
            save(after);
        } else if (pendingSaveAs != null) {
            Runnable after = pendingSaveAs;
            pendingSaveAs = null;
            saveAs(after);
        } else {
            runQueued();
        }
    }

    private void failed(Exception e) {
        pendingSave = null;
        pendingSaveAs = null;
        queue.clear();
        LOG.warn("Editing failed", e);
        view.showError(msg.format("error.edit", e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    /** The picture as it stands, with its layers. */
    private Picture picture() {
        return history.current();
    }

    /** All layers merged, as the picture shows. */
    private BufferedImage image() {
        return history.current().composite();
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
        if (history == null) return;
        push(new Edit.AutoLevels(active));
    }

    public void resize() {
        if (busy || history == null) return;
        if (tool != Tool.NONE) {
            applyTool();
            if (busy) return;
        }
        Picture picture = picture();
        view.askResize(picture.width(), picture.height(), services.preferences().resizeQuality(),
                services.preferences()::setResizeQuality).ifPresent(this::push);
    }

    public void copyImage() {
        if (history != null) view.copyImage(image());
    }

    // ---------------------------------------------------------------- tools

    /** Opens a tool's panel; the same tool again closes it without applying. */
    public void selectTool(Tool newTool) {
        if (history == null) return;
        // While a step is applied, only the painting tools change: they wait for nothing, and quick strokes
        // must not lose the tool chosen right after them. The others need the picture as it will be.
        if (busy && !(newTool.paints() && (tool.paints() || tool == Tool.NONE))) {
            view.showTool(tool);
            return;
        }
        if (newTool == tool) {
            cancelTool();
            return;
        }
        // A shape or text still open is painted before the next tool takes over.
        if (tool.paints()) commitPending();
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
            default -> {
            }
        }
        view.showTool(newTool);
        if (newTool.paints()) view.showPaint(paint);
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
        if (tool == Tool.TEXT && draft != null) {
            cancelText();
            return;
        }
        if (tool.paints()) commitPending();
        closeTool();
    }

    private void closeTool() {
        if (tool == Tool.NONE) return;
        clearPending();
        tool = Tool.NONE;
        view.showTool(Tool.NONE);
        update();
    }

    // --8<-- [start:apply-tool]
    @Override
    public void applyTool() {
        if (busy || history == null) return;
        if (tool.paints()) {
            // The painting tools stay open; only what is still pending becomes a step.
            commitPending();
            return;
        }
        Optional<Edit> edit = switch (tool) {
            case CROP -> crop == null || crop.equals(whole()) ? Optional.empty() : Optional.of(new Edit.Crop(crop));
            case STRAIGHTEN -> degrees == 0 ? Optional.empty() : Optional.of(new Edit.Straighten(degrees, straightenCrops));
            case ADJUST -> adjustments.neutral() ? Optional.empty() : Optional.of(new Edit.Adjust(adjustments, active));
            default -> Optional.empty();
        };
        closeTool();
        edit.ifPresent(this::push);
    }
    // --8<-- [end:apply-tool]

    // crop

    private void resetCrop() {
        Picture picture = picture();
        crop = ratio.free() ? whole() : CropBox.centred(picture.width(), picture.height(), ratioValue(), 1);
        showCrop();
    }

    private Rectangle whole() {
        return picture().bounds();
    }

    private double ratioValue() {
        Picture picture = picture();
        return ratio.value(picture.width(), picture.height(), portrait);
    }

    private void showCrop() {
        view.showCrop(crop, ratio, portrait, ratioValue(), guides);
        updateStatus();
    }

    @Override
    public void setRatio(AspectRatio newRatio) {
        if (history == null) return;
        ratio = newRatio;
        Picture picture = picture();
        if (newRatio.equals(AspectRatio.ORIGINAL)) portrait = picture.height() > picture.width();
        crop = CropBox.withRatio(crop == null ? whole() : crop, ratioValue(), picture.width(), picture.height());
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
        Picture picture = picture();
        crop = CropBox.withRatio(crop == null ? whole() : crop, ratioValue(), picture.width(), picture.height());
        showCrop();
    }

    @Override
    public void typeCrop(CropBox.Field field, int value) {
        if (history == null || crop == null) return;
        Picture picture = picture();
        crop = CropBox.typed(crop, field, value, ratioValue(), picture.width(), picture.height());
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

    // --8<-- [start:adjust]
    @Override
    public void setAdjustments(Adjustments newAdjustments) {
        adjustments = newAdjustments;
        Adjustments shown = newAdjustments;
        UnaryOperator<BufferedImage> look = shown.neutral() ? null : image -> ImageOperations.adjust(image, shown);
        view.showAdjust(shown, look);
    }
    // --8<-- [end:adjust]

    @Override
    public void setCompare(boolean on) {
        compare = on;
        view.showCompare(on);
    }

    /** Space held down in Adjust shows the original. */
    public void toggleCompare() {
        if (tool == Tool.ADJUST) setCompare(!compare);
    }

    // ---------------------------------------------------------------- painting

    // --8<-- [start:target]
    /**
     * Where a stroke or shape goes. A picture that is still one photo gets a new layer for it, if the user wants
     * that (Options > Paint on a New Layer), so that the photo itself stays as it was and the painting can be
     * moved, hidden or deleted. A text layer stays text: painting goes on a new layer above it.
     */
    private Edit.Target target() {
        Picture picture = picture();
        if (services.preferences().paintOnNewLayer() && picture.size() == 1 && !picture.layer(0).hasAlpha()) {
            return Edit.Target.above(0, msg.get("layer.painting"));
        }
        if (picture.layer(active).isText()) return Edit.Target.above(active, msg.get("layer.painting"));
        return Edit.Target.on(active);
    }
    // --8<-- [end:target]

    // --8<-- [start:painted]
    @Override
    public void painted(List<Dot> dots, boolean second) {
        if (history == null || dots.isEmpty()) return;
        boolean erase = tool == Tool.ERASER;
        if (tool != Tool.BRUSH && !erase) return;
        PaintSettings settings = paint;
        push(() -> {
            if (erase) {
                // The eraser works on the layer itself; on a photo it paints the second colour.
                return Optional.of(new Edit.Stroke(Edit.Target.on(active), dots, settings.eraser(),
                        settings.secondary(), true));
            }
            return Optional.of(new Edit.Stroke(target(), dots, settings.brush(), settings.colour(second), false));
        });
    }
    // --8<-- [end:painted]

    @Override
    public void shapeChanged(List<Dot> dots, boolean second) {
        if (history == null || tool != Tool.SHAPES) return;
        if (paint.shape() == ShapeKind.FREEHAND) {
            pendingShape = List.copyOf(dots);
            pendingSecond = second;
            commitPending();
            return;
        }
        pendingShape = List.copyOf(dots);
        pendingSecond = second;
        view.showPendingShape(pendingShape, pendingSecond);
    }

    @Override
    public void shapeDone() {
        commitPending();
    }

    /** Paints the shape that still has handles, or the text being typed. */
    private void commitPending() {
        if (pendingShape != null) {
            List<Dot> dots = pendingShape;
            boolean second = pendingSecond;
            PaintSettings settings = paint;
            pendingShape = null;
            view.showPendingShape(null, false);
            push(() -> Optional.of(new Edit.Shape(target(), settings.shape(), dots, settings.line(),
                    settings.colour(second), settings.fillColour(second))));
        }
        if (draft != null) finishText();
    }

    private void clearPending() {
        pendingShape = null;
        view.showPendingShape(null, false);
        if (draft != null) {
            draft = null;
            draftLayer = -1;
            view.showText(null);
            view.showOverlay(null);
            if (history != null) view.showImage(image());
        }
    }

    @Override
    public void clicked(Dot at, boolean second) {
        if (history == null) return;
        switch (tool) {
            case FILL -> {
                Color colour = paint.colour(second);
                int tolerance = paint.tolerance();
                push(() -> Optional.of(new Edit.Fill(active, (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                        colour, tolerance)));
            }
            case PICK -> picked(at, second);
            case TEXT -> textClicked(at);
            default -> {
            }
        }
    }

    // --8<-- [start:pick]
    /** Takes the colour of the picture as it shows at a point, for the first or (right button) second colour. */
    @Override
    public void picked(Dot at, boolean second) {
        if (history == null) return;
        BufferedImage image = image();
        int x = (int) Math.floor(at.x()), y = (int) Math.floor(at.y());
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return;
        Color colour = new Color(image.getRGB(x, y) | 0xff000000);
        setColour(colour, second);
    }
    // --8<-- [end:pick]

    @Override
    public void setColour(Color colour, boolean second) {
        paint = second ? paint.withColours(paint.primary(), colour) : paint.withColours(colour, paint.secondary());
        paintChanged();
        if (!second && draft != null) {
            draft = withColour(draft, colour);
            showDraft();
        }
    }

    @Override
    public void chooseColour(boolean second) {
        view.chooseColour(paint.colour(second)).ifPresent(c -> setColour(c, second));
    }

    public void swapColours() {
        paint = paint.withColours(paint.secondary(), paint.primary());
        paintChanged();
    }

    public void defaultColours() {
        paint = paint.withColours(Color.BLACK, Color.WHITE);
        paintChanged();
    }

    @Override
    public void setBrush(Brush brush) {
        paint = tool == Tool.ERASER ? paint.withEraser(brush) : paint.withBrush(brush);
        paintChanged();
    }

    /** [ and ] make the brush or eraser smaller and larger, in steps that suit its size. */
    public void growBrush(int direction) {
        if (tool != Tool.BRUSH && tool != Tool.ERASER) return;
        Brush brush = tool == Tool.ERASER ? paint.eraser() : paint.brush();
        int step = brush.size() < 10 ? 1 : brush.size() < 50 ? 5 : brush.size() < 100 ? 10 : 25;
        setBrush(brush.withSize(brush.size() + direction * step));
    }

    @Override
    public void setShape(ShapeKind kind) {
        commitPending();
        paint = paint.withShape(kind);
        paintChanged();
    }

    @Override
    public void setLine(LineStyle line) {
        paint = paint.withLine(line);
        paintChanged();
    }

    @Override
    public void setShapeFill(ShapeFill fill) {
        paint = paint.withFill(fill);
        paintChanged();
    }

    @Override
    public void setTolerance(int tolerance) {
        paint = paint.withTolerance(tolerance);
        paintChanged();
    }

    private void paintChanged() {
        services.preferences().setPaintSettings(paint);
        view.showPaint(paint);
        if (pendingShape != null) view.showPendingShape(pendingShape, pendingSecond);
        updateStatus();
    }

    // ---------------------------------------------------------------- moving a layer

    // --8<-- [start:move]
    @Override
    public void layerDragged(int dx, int dy, boolean done) {
        if (history == null || tool != Tool.MOVE) return;
        Layer layer = picture().layer(active);
        if (done) {
            moving = false;
            view.showOverlay(null);
            if (dx != 0 || dy != 0) push(new Edit.MoveLayer(active, dx, dy));
            else view.showImage(image());
            return;
        }
        // While dragging: the picture without the layer, and the layer drawn where it would go.
        if (!moving) {
            moving = true;
            view.showImage(picture().compositeWithout(active));
        }
        view.showOverlay(g -> {
            if (!layer.visible()) return;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity() / 100f));
            g.drawImage(layer.image(), layer.x() + dx, layer.y() + dy, null);
        });
    }
    // --8<-- [end:move]

    /** True while a layer is dragged with the Move tool. */
    private boolean moving;

    /** The arrow keys with the Move tool: one pixel, or ten with Shift. */
    public void nudge(int dx, int dy) {
        if (history == null || tool != Tool.MOVE) return;
        push(() -> Optional.of(new Edit.MoveLayer(active, dx, dy)));
    }

    // ---------------------------------------------------------------- text

    // --8<-- [start:text-click]
    /**
     * A click with the Text tool: on a text layer it opens that text again; elsewhere it finishes the open text
     * and starts a new one at the click.
     */
    private void textClicked(Dot at) {
        if (draft != null && TextRenderer.hits(draft, at.x(), at.y())) return;
        if (draft != null) finishText();
        List<Layer> layers = picture().layers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.visible() && layer.text().filter(t -> TextRenderer.hits(t, at.x(), at.y())).isPresent()) {
                editText(i);
                return;
            }
        }
        if (!textSizeChosen) {
            textStyle = textStyle.withSize(Math.max(12, Math.round(Math.min(picture().width(), picture().height()) / 12f)));
        }
        draft = withColour(textStyle, paint.primary()).withText("").withCentre(at.x(), at.y()).withRotation(0);
        draftLayer = -1;
        showDraft();
        updateStatus();
    }
    // --8<-- [end:text-click]

    /** Opens the text of a text layer for editing. */
    public void editText(int index) {
        Layer layer = picture().layer(index);
        if (layer.text().isEmpty()) return;
        active = index;
        draft = layer.text().get();
        draftLayer = index;
        view.showImage(picture().compositeWithout(index));
        showLayers();
        showDraft();
        updateStatus();
    }

    private void showDraft() {
        view.showText(draft);
        if (draft == null || draft.blank()) {
            view.showOverlay(null);
            return;
        }
        TextRenderer.Rendered r = TextRenderer.render(draft);
        view.showOverlay(g -> g.drawImage(r.image(), r.x(), r.y(), null));
    }

    @Override
    public void setText(String text) {
        if (draft == null) return;
        draft = draft.withText(text);
        showDraft();
    }

    /** Font, size, bold, italic, alignment and effect from the Text panel; the place and text stay. */
    @Override
    public void setTextStyle(TextBox style) {
        if (style.size() != textStyle.size()) textSizeChosen = true;
        textStyle = withColour(style, style.colour());
        if (draft != null) {
            draft = draft.withStyleOf(textStyle);
            showDraft();
        }
    }

    /** The text's colour, with an effect colour that stands out against it. */
    private static TextBox withColour(TextBox box, Color colour) {
        double light = (0.299 * colour.getRed() + 0.587 * colour.getGreen() + 0.114 * colour.getBlue()) / 255;
        Color against = light > 0.5 ? new Color(0, 0, 0, box.effect() == TextBox.Effect.BOX ? 170 : 255)
                : new Color(255, 255, 255, box.effect() == TextBox.Effect.BOX ? 200 : 255);
        return new TextBox(box.text(), box.font(), box.size(), box.bold(), box.italic(), box.align(), colour,
                box.effect(), against, box.centreX(), box.centreY(), box.rotation());
    }

    @Override
    public void textDragged(double dx, double dy) {
        if (draft == null) return;
        draft = draft.withCentre(draft.centreX() + dx, draft.centreY() + dy);
        showDraft();
    }

    // --8<-- [start:finish-text]
    /** Done in the Text panel, Enter on the picture, or a click elsewhere: the text becomes a step. */
    @Override
    public void finishText() {
        if (draft == null) return;
        TextBox box = draft;
        int layer = draftLayer;
        draft = null;
        draftLayer = -1;
        view.showText(null);
        view.showOverlay(null);
        if (layer >= 0) {
            Layer old = picture().layer(layer);
            if (box.blank()) push(new Edit.DeleteLayer(layer));
            else if (!box.equals(old.text().orElse(null))) push(new Edit.Text(layer, true, box));
            else view.showImage(image());
        } else if (!box.blank()) {
            // Made when its turn comes: a stroke still being applied may add the layer the text goes above.
            push(() -> Optional.of(new Edit.Text(active, false, box)));
        }
        updateStatus();
    }
    // --8<-- [end:finish-text]

    @Override
    public void cancelText() {
        if (draft == null) return;
        draft = null;
        draftLayer = -1;
        view.showText(null);
        view.showOverlay(null);
        view.showImage(image());
        updateStatus();
    }

    // ---------------------------------------------------------------- layers

    private void showLayers() {
        if (history == null) return;
        view.showLayers(picture().layers(), active);
    }

    @Override
    public void selectLayer(int index) {
        if (history == null || index < 0 || index >= picture().size() || index == active) return;
        if (tool.paints()) commitPending();
        active = index;
        showLayers();
        updateStatus();
    }

    @Override
    public void setLayerVisible(int index, boolean visible) {
        Layer layer = picture().layer(index);
        if (layer.visible() == visible) return;
        push(new Edit.LayerProperties(index, layer.name(), visible, layer.opacity()));
    }

    @Override
    public void setLayerOpacity(int index, int opacity) {
        Layer layer = picture().layer(index);
        if (layer.opacity() == opacity) return;
        push(new Edit.LayerProperties(index, layer.name(), layer.visible(), opacity));
    }

    @Override
    public void renameLayer(int index) {
        if (history == null) return;
        Layer layer = picture().layer(index);
        view.askLayerName(layer.name()).filter(n -> !n.equals(layer.name()))
                .ifPresent(name -> push(new Edit.LayerProperties(index, name, layer.visible(), layer.opacity())));
    }

    public void renameLayer() {
        renameLayer(active);
    }

    @Override
    public void newLayer() {
        if (history == null) return;
        commitPending();
        push(() -> Optional.of(new Edit.NewLayer(active, msg.format("layer.new", picture().size() + 1))));
    }

    @Override
    public void duplicateLayer() {
        if (history == null) return;
        commitPending();
        push(() -> Optional.of(new Edit.DuplicateLayer(active,
                msg.format("layer.copy", picture().layer(active).name()))));
    }

    @Override
    public void deleteLayer() {
        if (history == null || picture().size() < 2) return;
        clearPending();
        push(new Edit.DeleteLayer(active));
    }

    @Override
    public void layerUp() {
        if (history == null || active >= picture().size() - 1) return;
        push(new Edit.OrderLayer(active, active + 1));
    }

    @Override
    public void layerDown() {
        if (history == null || active <= 0) return;
        push(new Edit.OrderLayer(active, active - 1));
    }

    @Override
    public void mergeDown() {
        if (history == null || active <= 0) return;
        commitPending();
        push(new Edit.MergeDown(active));
    }

    @Override
    public void flatten() {
        if (history == null || picture().size() < 2) return;
        commitPending();
        push(new Edit.Flatten());
    }

    // --8<-- [start:paste]
    /** A picture from the clipboard as a new layer on top, in the middle. */
    @Override
    public void pasteAsLayer() {
        if (busy || history == null) return;
        Optional<BufferedImage> pasted = view.pasteImage();
        if (pasted.isEmpty()) {
            view.showInfo(msg.get("info.no-picture-to-paste"));
            return;
        }
        commitPending();
        push(new Edit.AddPicture(ImageOperations.copy(ImageOperations.normalize(pasted.get())),
                msg.get("layer.pasted")));
    }
    // --8<-- [end:paste]

    /** A picture file as a new layer on top. */
    @Override
    public void pictureAsLayer() {
        if (busy || history == null) return;
        Path folder = file.toAbsolutePath().getParent();
        view.chooseOpen(folder).ifPresent(path -> run(() -> services.loader().load(path),
                opened -> {
                    commitPending();
                    push(new Edit.AddPicture(opened.image(), path.getFileName().toString()));
                },
                e -> view.showError(e instanceof ImageLoader.UnsupportedFormatException
                        ? msg.format("error.format", path.getFileName())
                        : msg.format("error.open", path.getFileName(), e.getMessage()))));
    }

    /** View > Layers: shows or hides the Layers panel. */
    public void toggleLayersPanel() {
        layersPanel = !layersPanel;
        view.showLayersPanel(layersPanel);
    }

    public void setPaintOnNewLayer(boolean on) {
        services.preferences().setPaintOnNewLayer(on);
    }

    public boolean paintOnNewLayer() {
        return services.preferences().paintOnNewLayer();
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

    /** Escape closes the open tool without applying it; in the painting tools it drops what is pending. */
    public void escape() {
        if (tool == Tool.TEXT && draft != null) cancelText();
        else if (tool.paints() && pendingShape != null) clearPending();
        else if (tool != Tool.NONE) closeTool();
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
        Picture picture = picture();
        NumberFormat numbers = NumberFormat.getIntegerInstance(msg.locale());
        String base = msg.format("status", numbers.format(picture.width()), numbers.format(picture.height()),
                fileFormat.label(), numbers.format(Math.round(scale * 100)));
        String layer = picture.layer(Math.clamp(active, 0, picture.size() - 1)).name();
        String extra = switch (tool) {
            case CROP -> crop == null ? "" : msg.format("status.crop", numbers.format(crop.width),
                    numbers.format(crop.height), numbers.format(crop.x), numbers.format(crop.y));
            case STRAIGHTEN -> msg.format("status.straighten",
                    String.format(msg.locale(), "%+.1f", degrees));
            case ADJUST -> msg.get("status.adjust");
            case BRUSH -> msg.format("status.brush", paint.brush().size(), layer);
            case ERASER -> msg.format("status.eraser", paint.eraser().size(), layer);
            case SHAPES -> msg.format("status.shapes", layer);
            case TEXT -> msg.get(draft == null ? "status.text" : "status.text.editing");
            case MOVE -> msg.format("status.move", layer);
            case FILL -> msg.format("status.fill", layer);
            case PICK -> msg.get("status.pick");
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

    int activeLayer() {
        return active;
    }

    PaintSettings paint() {
        return paint;
    }

    TextBox draft() {
        return draft;
    }

    List<Dot> pendingShape() {
        return pendingShape;
    }
}
