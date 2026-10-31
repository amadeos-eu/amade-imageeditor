package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.PaintSettings;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.apps.imageeditor.domain.TextBox;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Records what the presenter shows and answers its questions as the test sets up. */
final class FakeView implements MainView {
    BufferedImage image;
    String noImage;
    String title = "";
    String status = "";
    Optional<String> undo = Optional.empty(), redo = Optional.empty();
    boolean picture, busy;
    Tool tool = Tool.NONE;
    Rectangle crop;
    AspectRatio ratio;
    boolean portrait;
    double ratioValue;
    double degrees;
    boolean straightenCrops;
    Adjustments adjustments;
    UnaryOperator<BufferedImage> look;
    boolean compare;
    final List<ZoomCommand> zooms = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    BufferedImage copied;
    boolean closed;
    boolean aboutShown;

    Optional<Path> openAnswer = Optional.empty();
    Optional<SavePanel.Request> saveAnswer = Optional.empty();
    Path saveSuggested;
    boolean saveFromJpeg;
    Optional<Edit.Resize> resizeAnswer = Optional.empty();
    Optional<AspectRatio> ratioAnswer = Optional.empty();
    Unsaved unsavedAnswer = Unsaved.CANCEL;
    int unsavedAsked;
    boolean replaceAnswer = true;

    List<Layer> layers = List.of();
    int activeLayer;
    boolean layersPanel;
    PaintSettings paint;
    TextBox text;
    List<Dot> pendingShape;
    Overlay overlay;
    boolean saveLayered;
    Layers layersAnswer = Layers.MERGED;
    int layersAsked;
    Optional<Color> colourAnswer = Optional.empty();
    Optional<String> nameAnswer = Optional.empty();
    Optional<BufferedImage> pasteAnswer = Optional.empty();

    @Override
    public void showLayers(List<Layer> newLayers, int active) {
        layers = newLayers;
        activeLayer = active;
    }

    @Override
    public void showLayersPanel(boolean on) {
        layersPanel = on;
    }

    @Override
    public void showPaint(PaintSettings settings) {
        paint = settings;
    }

    @Override
    public void showText(TextBox draft) {
        text = draft;
    }

    @Override
    public void showPendingShape(List<Dot> dots, boolean second) {
        pendingShape = dots;
    }

    @Override
    public void showOverlay(Overlay newOverlay) {
        overlay = newOverlay;
    }

    @Override
    public Layers askLayers(String fileName, String layersName) {
        layersAsked++;
        return layersAnswer;
    }

    @Override
    public Optional<Color> chooseColour(Color initial) {
        return colourAnswer;
    }

    @Override
    public Optional<String> askLayerName(String current) {
        return nameAnswer;
    }

    @Override
    public Optional<BufferedImage> pasteImage() {
        return pasteAnswer;
    }

    @Override
    public void showImage(BufferedImage newImage) {
        image = newImage;
        noImage = null;
    }

    @Override
    public void showNoImage(String message) {
        noImage = message;
    }

    @Override
    public void showTitle(String newTitle) {
        title = newTitle;
    }

    @Override
    public void showStatus(String text) {
        status = text;
    }

    @Override
    public void showHistory(Optional<String> newUndo, Optional<String> newRedo) {
        undo = newUndo;
        redo = newRedo;
    }

    @Override
    public void enableEditing(boolean hasPicture, boolean isBusy) {
        picture = hasPicture;
        busy = isBusy;
    }

    @Override
    public void showTool(Tool newTool) {
        tool = newTool;
    }

    @Override
    public void showCrop(Rectangle box, AspectRatio newRatio, boolean newPortrait, double value, Guides guides) {
        crop = box;
        ratio = newRatio;
        portrait = newPortrait;
        ratioValue = value;
    }

    @Override
    public void showStraighten(double newDegrees, boolean crops) {
        degrees = newDegrees;
        straightenCrops = crops;
    }

    @Override
    public void showAdjust(Adjustments newAdjustments, UnaryOperator<BufferedImage> newLook) {
        adjustments = newAdjustments;
        look = newLook;
    }

    @Override
    public void showCompare(boolean on) {
        compare = on;
    }

    @Override
    public void zoom(ZoomCommand command) {
        zooms.add(command);
    }

    @Override
    public Optional<Path> chooseOpen(Path folder) {
        return openAnswer;
    }

    @Override
    public Optional<SavePanel.Request> chooseSave(Path suggested, Path original, boolean fromJpeg, boolean layered,
                                                  Function<ImageFormat, SaveOptions> remembered) {
        saveSuggested = suggested;
        saveFromJpeg = fromJpeg;
        saveLayered = layered;
        return saveAnswer;
    }

    @Override
    public Optional<Edit.Resize> askResize(int width, int height, ResizeQuality quality,
                                           Consumer<ResizeQuality> chosenQuality) {
        resizeAnswer.ifPresent(r -> chosenQuality.accept(r.quality()));
        return resizeAnswer;
    }

    @Override
    public Optional<AspectRatio> askRatio() {
        return ratioAnswer;
    }

    @Override
    public Unsaved askUnsaved(String fileName) {
        unsavedAsked++;
        return unsavedAnswer;
    }

    @Override
    public boolean confirmReplace(String fileName) {
        return replaceAnswer;
    }

    @Override
    public void copyImage(BufferedImage newImage) {
        copied = newImage;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
        aboutShown = true;
    }

    @Override
    public void close() {
        closed = true;
    }
}
