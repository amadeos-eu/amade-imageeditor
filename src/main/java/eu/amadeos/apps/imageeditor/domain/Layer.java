package eu.amadeos.apps.imageeditor.domain;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * One layer of a picture: its pixels, where they lie on the picture, and whether and how strongly they show. A
 * text layer also keeps its {@link TextBox}, so that it can be edited again; its pixels are the rendered text.
 *
 * <p>A layer is never changed. Its pixels are not drawn on after it was made: every step makes a new layer, and
 * the layers a step leaves alone are shared between the pictures before and after it.
 */
public final class Layer {
    private final String name;
    private final BufferedImage image;
    private final int x, y;
    private final boolean visible;
    private final int opacity;
    private final TextBox text;

    public Layer(String name, BufferedImage image, int x, int y, boolean visible, int opacity, TextBox text) {
        if (name == null || image == null) throw new IllegalArgumentException("layer without name or pixels");
        if (opacity < 0 || opacity > 100) throw new IllegalArgumentException("opacity out of range: " + opacity);
        this.name = name;
        this.image = image;
        this.x = x;
        this.y = y;
        this.visible = visible;
        this.opacity = opacity;
        this.text = text;
    }

    /** A visible, fully covering layer of pixels at the top left corner. */
    public static Layer of(String name, BufferedImage image) {
        return new Layer(name, image, 0, 0, true, 100, null);
    }

    public String name() {
        return name;
    }

    public BufferedImage image() {
        return image;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    /** Where the pixels lie, in picture pixels. */
    public Rectangle bounds() {
        return new Rectangle(x, y, image.getWidth(), image.getHeight());
    }

    public boolean visible() {
        return visible;
    }

    /** 0 (invisible) to 100 (fully covering). */
    public int opacity() {
        return opacity;
    }

    public Optional<TextBox> text() {
        return Optional.ofNullable(text);
    }

    public boolean isText() {
        return text != null;
    }

    /** True if the layer has transparent pixels or may get some (its pixels have an alpha channel). */
    public boolean hasAlpha() {
        return image.getColorModel().hasAlpha();
    }

    public Layer withName(String newName) {
        return new Layer(newName, image, x, y, visible, opacity, text);
    }

    public Layer withImage(BufferedImage newImage, int newX, int newY) {
        return new Layer(name, newImage, newX, newY, visible, opacity, text);
    }

    /** New pixels that are no longer text: a text layer painted on becomes a layer of pixels. */
    public Layer withPixels(BufferedImage newImage, int newX, int newY) {
        return new Layer(name, newImage, newX, newY, visible, opacity, null);
    }

    public Layer withText(TextBox newText, BufferedImage rendered, int newX, int newY) {
        return new Layer(name, rendered, newX, newY, visible, opacity, newText);
    }

    public Layer at(int newX, int newY) {
        return new Layer(name, image, newX, newY, visible, opacity, text);
    }

    public Layer withVisible(boolean on) {
        return new Layer(name, image, x, y, on, opacity, text);
    }

    public Layer withOpacity(int newOpacity) {
        return new Layer(name, image, x, y, visible, newOpacity, text);
    }

    @Override
    public String toString() {
        return "Layer[" + name + " " + bounds() + (visible ? "" : " hidden") + " " + opacity + "%"
                + (text != null ? " text" : "") + "]";
    }
}
