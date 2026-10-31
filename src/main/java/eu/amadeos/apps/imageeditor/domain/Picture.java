package eu.amadeos.apps.imageeditor.domain;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * What is edited: a size and its layers, from the bottom up. A picture opened from a JPEG or PNG has one layer;
 * painting, text and pasted pictures add more. Like its layers, a picture is never changed; the steps make new
 * ones.
 *
 * <p>The picture as it shows, all visible layers laid over each other, is {@link #composite()}; it is made once
 * and kept. A picture of a single plain layer is that layer's pixels themselves, so that a photo that was only
 * cropped or adjusted is saved exactly as before layers came.
 */
public final class Picture {
    private final int width, height;
    private final List<Layer> layers;
    private BufferedImage composite;

    public Picture(int width, int height, List<Layer> layers) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("empty picture: " + width + " x " + height);
        if (layers.isEmpty()) throw new IllegalArgumentException("picture without layers");
        this.width = width;
        this.height = height;
        this.layers = List.copyOf(layers);
    }

    /** A picture of one layer, as opened from an ordinary picture file. */
    public static Picture of(BufferedImage image, String layerName) {
        return new Picture(image.getWidth(), image.getHeight(), List.of(Layer.of(layerName, image)));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Rectangle bounds() {
        return new Rectangle(0, 0, width, height);
    }

    /** The layers from the bottom up. */
    public List<Layer> layers() {
        return layers;
    }

    public Layer layer(int index) {
        return layers.get(index);
    }

    public int size() {
        return layers.size();
    }

    /** True if the picture is more than one plain layer, so that saving it as JPEG or PNG merges something. */
    public boolean layered() {
        return layers.size() > 1 || layers.getFirst().isText();
    }

    /** The same size with other layers. */
    public Picture withLayers(List<Layer> newLayers) {
        return new Picture(width, height, newLayers);
    }

    public Picture with(int index, Layer layer) {
        List<Layer> list = new ArrayList<>(layers);
        list.set(index, layer);
        return withLayers(list);
    }

    /** All visible layers over each other, as the picture shows and is saved to formats without layers. */
    public BufferedImage composite() {
        BufferedImage c = composite;
        if (c == null) {
            c = compose(-1);
            composite = c;
        }
        return c;
    }

    /** The picture without one layer, for showing that layer while it is moved or its text edited. */
    public BufferedImage compositeWithout(int index) {
        return compose(index);
    }

    private BufferedImage compose(int left) {
        Layer bottom = layers.getFirst();
        if (left < 0 && layers.size() == 1 && plain(bottom)) return bottom.image();
        // A plain photo at the bottom keeps the result without an alpha channel, as JPEG wants it.
        boolean opaque = left != 0 && plain(bottom) && !bottom.hasAlpha();
        BufferedImage result = new BufferedImage(width, height,
                opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            if (i == left || !layer.visible() || layer.opacity() == 0) continue;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity() / 100f));
            g.drawImage(layer.image(), layer.x(), layer.y(), null);
        }
        g.dispose();
        return result;
    }

    /** Visible, fully covering, at the corner and as large as the picture. */
    private boolean plain(Layer layer) {
        return layer.visible() && layer.opacity() == 100 && layer.x() == 0 && layer.y() == 0
                && layer.width() == width && layer.height() == height;
    }
}
