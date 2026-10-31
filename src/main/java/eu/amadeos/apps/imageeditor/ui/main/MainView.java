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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    /** The answer to "save the changes first?". */
    enum Unsaved { SAVE, DISCARD, CANCEL }

    /** The answer to "this format cannot hold layers": save merged (and also keep an OpenRaster file), or not. */
    enum Layers { MERGED, MERGED_AND_LAYERS, SAVE_AS, CANCEL }

    /**
     * A new or edited picture: all visible layers merged. A picture of another size is fitted into the window;
     * one of the same size keeps the zoom, so that painting does not jump.
     */
    void showImage(BufferedImage image);

    /** The layers from the bottom up, and the one painting goes to. */
    void showLayers(List<Layer> layers, int active);

    /** Shows or hides the Layers panel (View > Layers). */
    void showLayersPanel(boolean on);

    /** The colours, brush, shape and fill settings. */
    void showPaint(PaintSettings settings);

    /** The text being edited, or null when no text is open. */
    void showText(TextBox draft);

    /** The shape drawn last, with handles to change it before it is painted; null for none. */
    void showPendingShape(List<Dot> dots, boolean second);

    /** What the canvas draws over the picture, in picture pixels; null for nothing. */
    void showOverlay(Overlay overlay);

    /** No picture: nothing opened yet, or it cannot be read. */
    void showNoImage(String message);

    void showTitle(String title);

    void showStatus(String text);

    /** The names of the steps Undo and Redo would take, for the menu; empty if there is none. */
    void showHistory(Optional<String> undo, Optional<String> redo);

    /** Enables what needs a picture; {@code busy} while a step or a save runs in the background. */
    void enableEditing(boolean picture, boolean busy);

    void showTool(Tool tool);

    void showCrop(Rectangle box, AspectRatio ratio, boolean portrait, double ratioValue, Guides guides);

    void showStraighten(double degrees, boolean crops);

    void showAdjust(Adjustments adjustments, UnaryOperator<BufferedImage> look);

    void showCompare(boolean on);

    void zoom(ZoomCommand command);

    Optional<Path> chooseOpen(Path folder);

    /** The Save As dialog; {@code layered} if the picture has layers that formats other than OpenRaster merge. */
    Optional<SavePanel.Request> chooseSave(Path suggested, Path original, boolean fromJpeg, boolean layered,
                                           Function<ImageFormat, SaveOptions> remembered);

    /** The Resize dialog; empty if cancelled or unchanged. Reports the chosen quality to remember it. */
    Optional<Edit.Resize> askResize(int width, int height, ResizeQuality quality,
                                    java.util.function.Consumer<ResizeQuality> chosenQuality);

    /** A ratio like "7:5"; empty if cancelled. */
    Optional<AspectRatio> askRatio();

    Unsaved askUnsaved(String fileName);

    /** Saving a layered picture to {@code fileName}, whose format cannot hold layers; {@code layersName} is the .ora. */
    Layers askLayers(String fileName, String layersName);

    Optional<Color> chooseColour(Color initial);

    /** A layer's new name; empty if cancelled or blank. */
    Optional<String> askLayerName(String current);

    /** A picture from the clipboard, for Paste as New Layer. */
    Optional<BufferedImage> pasteImage();

    boolean confirmReplace(String fileName);

    void copyImage(BufferedImage image);

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
