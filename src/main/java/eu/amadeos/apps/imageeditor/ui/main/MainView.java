package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.domain.Adjustments;
import eu.amadeos.apps.imageeditor.domain.AspectRatio;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** The window as the presenter sees it. All methods run on the EDT. */
public interface MainView {
    /** The answer to "save the changes first?". */
    enum Unsaved { SAVE, DISCARD, CANCEL }

    /** A new or edited picture. */
    void showImage(BufferedImage image);

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

    Optional<SavePanel.Request> chooseSave(Path suggested, Path original, boolean fromJpeg,
                                           Function<ImageFormat, SaveOptions> remembered);

    /** The Resize dialog; empty if cancelled or unchanged. Reports the chosen quality to remember it. */
    Optional<Edit.Resize> askResize(int width, int height, ResizeQuality quality,
                                    java.util.function.Consumer<ResizeQuality> chosenQuality);

    /** A ratio like "7:5"; empty if cancelled. */
    Optional<AspectRatio> askRatio();

    Unsaved askUnsaved(String fileName);

    boolean confirmReplace(String fileName);

    void copyImage(BufferedImage image);

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
