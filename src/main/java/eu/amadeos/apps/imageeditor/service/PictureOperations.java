package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.Picture;
import eu.amadeos.apps.imageeditor.domain.TextBox;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The steps on a picture with layers. The steps of the whole picture (crop, turn, straighten, resize) go to every
 * layer, the others to one; layers a step does not touch are passed on as they are.
 *
 * <p>Text layers stay text where they can: cropping and moving shift the text, quarter turns and straightening
 * turn it, resizing scales its font. Only what text cannot do (a mirror image, painting on it, adjusting its
 * colours) turns it into pixels.
 */
public final class PictureOperations {
    private PictureOperations() {
    }

    // --8<-- [start:apply]
    public static Picture apply(Picture picture, Edit edit) {
        return switch (edit) {
            case Edit.Crop crop -> crop(picture, crop.area());
            case Edit.Turn turn -> turn(picture, turn.orientation());
            case Edit.Straighten s -> straighten(picture, s);
            case Edit.Resize r -> resize(picture, r);
            case Edit.Adjust a -> pixels(picture, a.layer(), image -> ImageOperations.adjust(image, a.adjustments()));
            case Edit.AutoLevels a -> pixels(picture, a.layer(), ImageOperations::autoLevels);
            case Edit.Stroke s -> stroke(picture, s);
            case Edit.Shape s -> shape(picture, s);
            case Edit.Fill f -> fill(picture, f);
            case Edit.Text t -> text(picture, t);
            case Edit.NewLayer n -> insert(picture, n.above() + 1, blank(picture, n.name()));
            case Edit.AddPicture a -> addPicture(picture, a);
            case Edit.DuplicateLayer d -> insert(picture, d.layer() + 1, picture.layer(d.layer()).withName(d.name()));
            case Edit.DeleteLayer d -> delete(picture, d.layer());
            case Edit.OrderLayer o -> order(picture, o.layer(), o.to());
            case Edit.LayerProperties p -> picture.with(p.layer(), picture.layer(p.layer()).withName(p.name())
                    .withVisible(p.visible()).withOpacity(p.opacity()));
            case Edit.MoveLayer m -> picture.with(m.layer(), moved(picture.layer(m.layer()), m.dx(), m.dy()));
            case Edit.MergeDown m -> mergeDown(picture, m.layer());
            case Edit.Flatten ignored -> flatten(picture);
        };
    }
    // --8<-- [end:apply]

    // ---------------------------------------------------------------- the whole picture

    // --8<-- [start:crop]
    static Picture crop(Picture picture, Rectangle area) {
        Rectangle inside = area.intersection(picture.bounds());
        if (inside.isEmpty()) throw new IllegalArgumentException("crop outside the picture: " + area);
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : picture.layers()) {
            if (layer.isText()) {
                TextBox box = layer.text().orElseThrow();
                layers.add(withText(layer, box.withCentre(box.centreX() - inside.x, box.centreY() - inside.y)));
                continue;
            }
            Rectangle keep = layer.bounds().intersection(inside);
            if (keep.isEmpty()) {
                // Wholly cut away: an empty layer keeps its place, name and settings.
                layers.add(layer.withPixels(ImageOperations.blank(1, 1, true), 0, 0));
            } else if (keep.equals(layer.bounds())) {
                layers.add(layer.at(layer.x() - inside.x, layer.y() - inside.y));
            } else {
                BufferedImage pixels = ImageOperations.crop(layer.image(),
                        new Rectangle(keep.x - layer.x(), keep.y - layer.y(), keep.width, keep.height));
                layers.add(layer.withPixels(pixels, keep.x - inside.x, keep.y - inside.y));
            }
        }
        return new Picture(inside.width, inside.height, layers);
    }
    // --8<-- [end:crop]

    static Picture turn(Picture picture, Orientation orientation) {
        int w = picture.width(), h = picture.height();
        AffineTransform map = ImageOperations.affine(orientation, w, h);
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : picture.layers()) {
            if (layer.isText() && !orientation.mirrored()) {
                TextBox box = layer.text().orElseThrow();
                Point2D centre = map.transform(new Point2D.Double(box.centreX(), box.centreY()), null);
                layers.add(withText(layer, box.withCentre(centre.getX(), centre.getY())
                        .withRotation(box.rotation() + 90 * orientation.turns())));
                continue;
            }
            Rectangle where = map.createTransformedShape(layer.bounds()).getBounds();
            layers.add(layer.withPixels(ImageOperations.turn(layer.image(), orientation), where.x, where.y));
        }
        boolean swap = orientation.swapsSides();
        return new Picture(swap ? h : w, swap ? w : h, layers);
    }

    // --8<-- [start:straighten]
    /**
     * Each layer is laid onto the picture's own size and turned like the picture; the bottom photo exactly as
     * before layers came, so that straightening a photo gives the same pixels.
     */
    static Picture straighten(Picture picture, Edit.Straighten s) {
        int w = picture.width(), h = picture.height();
        Rectangle keep = s.crop() ? ImageOperations.inscribed(w, h, s.degrees()) : new Rectangle(0, 0, w, h);
        AffineTransform map = AffineTransform.getTranslateInstance(-keep.x, -keep.y);
        map.rotate(Math.toRadians(s.degrees()), w / 2.0, h / 2.0);
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : picture.layers()) {
            if (layer.isText()) {
                TextBox box = layer.text().orElseThrow();
                Point2D centre = map.transform(new Point2D.Double(box.centreX(), box.centreY()), null);
                layers.add(withText(layer, box.withCentre(centre.getX(), centre.getY())
                        .withRotation(box.rotation() + s.degrees())));
                continue;
            }
            BufferedImage full = covering(picture, layer);
            layers.add(layer.withPixels(ImageOperations.straighten(full, s.degrees(), s.crop()), 0, 0));
        }
        return new Picture(keep.width, keep.height, layers);
    }
    // --8<-- [end:straighten]

    /** The layer's pixels on a transparent picture of the picture's size; a covering layer as it is. */
    private static BufferedImage covering(Picture picture, Layer layer) {
        if (layer.x() == 0 && layer.y() == 0 && layer.width() == picture.width()
                && layer.height() == picture.height()) {
            return layer.image();
        }
        BufferedImage full = ImageOperations.blank(picture.width(), picture.height(), true);
        Graphics2D g = full.createGraphics();
        g.drawImage(layer.image(), layer.x(), layer.y(), null);
        g.dispose();
        return full;
    }

    static Picture resize(Picture picture, Edit.Resize r) {
        double sx = (double) r.width() / picture.width(), sy = (double) r.height() / picture.height();
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : picture.layers()) {
            if (layer.isText()) {
                TextBox box = layer.text().orElseThrow();
                layers.add(withText(layer, box.withCentre(box.centreX() * sx, box.centreY() * sy)
                        .withSize((float) (box.size() * Math.sqrt(sx * sy)))));
                continue;
            }
            boolean whole = layer.bounds().equals(picture.bounds());
            int x = (int) Math.round(layer.x() * sx), y = (int) Math.round(layer.y() * sy);
            int lw = whole ? r.width() : Math.max(1, (int) Math.round((layer.x() + layer.width()) * sx) - x);
            int lh = whole ? r.height() : Math.max(1, (int) Math.round((layer.y() + layer.height()) * sy) - y);
            layers.add(layer.withPixels(ImageOperations.resize(layer.image(), lw, lh, r.quality()), x, y));
        }
        return new Picture(r.width(), r.height(), layers);
    }

    // ---------------------------------------------------------------- one layer

    private static Picture pixels(Picture picture, int index, java.util.function.UnaryOperator<BufferedImage> change) {
        Layer layer = picture.layer(index);
        return picture.with(index, layer.withPixels(change.apply(layer.image()), layer.x(), layer.y()));
    }

    // --8<-- [start:stroke]
    static Picture stroke(Picture picture, Edit.Stroke s) {
        Placed placed = target(picture, s.target());
        Layer layer = placed.picture().layer(placed.index());
        BufferedImage painted = Painter.stroke(layer.image(), shifted(s.dots(), layer), s.brush(), s.colour(),
                s.erase());
        return placed.picture().with(placed.index(), layer.withPixels(painted, layer.x(), layer.y()));
    }
    // --8<-- [end:stroke]

    static Picture shape(Picture picture, Edit.Shape s) {
        Placed placed = target(picture, s.target());
        Layer layer = placed.picture().layer(placed.index());
        BufferedImage painted = Painter.shape(layer.image(), s.kind(), shifted(s.dots(), layer), s.line(),
                s.colour(), s.fill());
        return placed.picture().with(placed.index(), layer.withPixels(painted, layer.x(), layer.y()));
    }

    static Picture fill(Picture picture, Edit.Fill f) {
        Layer layer = picture.layer(f.layer());
        BufferedImage filled = Painter.fill(layer.image(), f.x() - layer.x(), f.y() - layer.y(), f.colour(),
                f.tolerance());
        return picture.with(f.layer(), layer.withPixels(filled, layer.x(), layer.y()));
    }

    /** The picture with the layer painting goes on, and that layer's place. */
    private record Placed(Picture picture, int index) {
    }

    private static Placed target(Picture picture, Edit.Target target) {
        if (!target.makesLayer()) return new Placed(picture, target.layer());
        return new Placed(insert(picture, target.layer() + 1, blank(picture, target.newLayer())), target.layer() + 1);
    }

    private static List<Dot> shifted(List<Dot> dots, Layer layer) {
        if (layer.x() == 0 && layer.y() == 0) return dots;
        return dots.stream().map(d -> new Dot(d.x() - layer.x(), d.y() - layer.y())).toList();
    }

    // --8<-- [start:text]
    static Picture text(Picture picture, Edit.Text t) {
        TextRenderer.Rendered r = TextRenderer.render(t.box());
        if (t.replace()) {
            Layer layer = picture.layer(t.layer());
            // A layer still named after its old text is named after the new one.
            String oldTitle = layer.text().map(TextBox::title).orElse(null);
            Layer changed = layer.withText(t.box(), r.image(), r.x(), r.y());
            if (layer.name().equals(oldTitle) && !t.box().title().isEmpty()) changed = changed.withName(t.box().title());
            return picture.with(t.layer(), changed);
        }
        String name = t.box().title().isEmpty() ? "Text" : t.box().title();
        Layer layer = new Layer(name, r.image(), r.x(), r.y(), true, 100, t.box());
        return insert(picture, t.layer() + 1, layer);
    }
    // --8<-- [end:text]

    /** The text layer with a moved, turned or resized text, rendered again. */
    private static Layer withText(Layer layer, TextBox box) {
        TextRenderer.Rendered r = TextRenderer.render(box);
        return layer.withText(box, r.image(), r.x(), r.y());
    }

    // ---------------------------------------------------------------- layers

    private static Layer blank(Picture picture, String name) {
        return Layer.of(name, ImageOperations.blank(picture.width(), picture.height(), true));
    }

    private static Picture insert(Picture picture, int index, Layer layer) {
        List<Layer> layers = new ArrayList<>(picture.layers());
        layers.add(Math.clamp(index, 0, layers.size()), layer);
        return picture.withLayers(layers);
    }

    private static Picture addPicture(Picture picture, Edit.AddPicture a) {
        BufferedImage image = ImageOperations.normalize(a.image());
        int x = (picture.width() - image.getWidth()) / 2, y = (picture.height() - image.getHeight()) / 2;
        return insert(picture, picture.size(), Layer.of(a.name(), image).at(x, y));
    }

    private static Picture delete(Picture picture, int index) {
        if (picture.size() == 1) throw new IllegalArgumentException("the only layer cannot be deleted");
        List<Layer> layers = new ArrayList<>(picture.layers());
        layers.remove(index);
        return picture.withLayers(layers);
    }

    private static Picture order(Picture picture, int index, int to) {
        List<Layer> layers = new ArrayList<>(picture.layers());
        Layer layer = layers.remove(index);
        layers.add(Math.clamp(to, 0, layers.size()), layer);
        return picture.withLayers(layers);
    }

    private static Layer moved(Layer layer, int dx, int dy) {
        if (layer.isText()) {
            TextBox box = layer.text().orElseThrow();
            return layer.withText(box.withCentre(box.centreX() + dx, box.centreY() + dy), layer.image(),
                    layer.x() + dx, layer.y() + dy);
        }
        return layer.at(layer.x() + dx, layer.y() + dy);
    }

    // --8<-- [start:merge]
    /**
     * Lays a layer onto the one below with its opacity; the result keeps the lower layer's name and settings. A
     * photo at the bottom stays without transparency, as long as the upper layer lies within it.
     */
    static Picture mergeDown(Picture picture, int index) {
        if (index <= 0) throw new IllegalArgumentException("nothing below layer " + index);
        Layer lower = picture.layer(index - 1), upper = picture.layer(index);
        Rectangle union = lower.bounds().union(upper.visible() ? upper.bounds() : lower.bounds());
        boolean opaque = !lower.hasAlpha() && union.equals(lower.bounds());
        BufferedImage merged = ImageOperations.blank(union.width, union.height, !opaque);
        Graphics2D g = merged.createGraphics();
        g.drawImage(lower.image(), lower.x() - union.x, lower.y() - union.y, null);
        if (upper.visible()) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, upper.opacity() / 100f));
            g.drawImage(upper.image(), upper.x() - union.x, upper.y() - union.y, null);
        }
        g.dispose();
        List<Layer> layers = new ArrayList<>(picture.layers());
        layers.set(index - 1, lower.withPixels(merged, union.x, union.y));
        layers.remove(index);
        return picture.withLayers(layers);
    }
    // --8<-- [end:merge]

    static Picture flatten(Picture picture) {
        Layer bottom = picture.layer(0);
        BufferedImage all = picture.composite();
        return picture.withLayers(List.of(Layer.of(bottom.name(), all)));
    }
}
