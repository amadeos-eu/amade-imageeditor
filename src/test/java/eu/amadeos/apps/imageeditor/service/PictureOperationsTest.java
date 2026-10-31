package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.Picture;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PictureOperationsTest {
    private final Picture photo = Picture.of(TestPictures.quarters(200, 100), "Background");
    private static final Brush HARD = new Brush(10, 100, 100);

    static TextBox text(String s, double x, double y) {
        return new TextBox(s, "SansSerif", 20, true, false, TextBox.Align.CENTRE, Color.YELLOW,
                TextBox.Effect.NONE, Color.BLACK, x, y, 0);
    }

    @Test
    void aSinglePhotoShowsItselfWithoutCopying() {
        assertSame(photo.layer(0).image(), photo.composite());
        assertFalse(photo.layered());
    }

    @Test
    void aStrokeOnANewLayerLeavesThePhotoAsItWas() {
        Picture painted = PictureOperations.apply(photo, new Edit.Stroke(Edit.Target.above(0, "Painting"),
                List.of(new Dot(50, 25), new Dot(150, 25)), HARD, Color.BLACK, false));
        assertEquals(2, painted.size());
        assertEquals("Painting", painted.layer(1).name());
        assertSame(photo.layer(0), painted.layer(0));
        assertEquals(0xff000000, painted.composite().getRGB(100, 25));
        assertEquals(0xff00ff00, painted.composite().getRGB(150, 5));
        assertTrue(painted.layered());
    }

    @Test
    void aStrokeOnTheLayerItself() {
        Picture painted = PictureOperations.apply(photo, new Edit.Stroke(Edit.Target.on(0),
                List.of(new Dot(50, 25)), HARD, Color.BLACK, false));
        assertEquals(1, painted.size());
        assertEquals(0xff000000, painted.composite().getRGB(50, 25));
    }

    @Test
    void aMovedLayerIsPaintedWhereItIsNow() {
        Picture two = PictureOperations.apply(photo, new Edit.NewLayer(0, "Top"));
        Picture moved = PictureOperations.apply(two, new Edit.MoveLayer(1, 30, 10));
        Picture painted = PictureOperations.apply(moved, new Edit.Stroke(Edit.Target.on(1),
                List.of(new Dot(100, 50)), HARD, Color.BLACK, false));
        assertEquals(0xff000000, painted.composite().getRGB(100, 50));
        assertEquals(0xff000000, painted.layer(1).image().getRGB(70, 40));
    }

    @Test
    void textBecomesALayerNamedAfterIt() {
        Picture withText = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hello", 100, 50)));
        assertEquals(2, withText.size());
        Layer layer = withText.layer(1);
        assertEquals("Hello", layer.name());
        assertTrue(layer.isText());
        assertTrue(layer.bounds().contains(100, 50));
        Picture changed = PictureOperations.apply(withText, new Edit.Text(1, true, text("Bye", 100, 50)));
        assertEquals("Bye", changed.layer(1).name());
        assertEquals("Bye", changed.layer(1).text().orElseThrow().text());
    }

    @Test
    void movingTextMovesItsCentre() {
        Picture withText = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hi", 100, 50)));
        Picture moved = PictureOperations.apply(withText, new Edit.MoveLayer(1, 10, -5));
        TextBox box = moved.layer(1).text().orElseThrow();
        assertEquals(110, box.centreX(), 1e-9);
        assertEquals(45, box.centreY(), 1e-9);
    }

    @Test
    void cropKeepsLayersAndMovesText() {
        Picture withText = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hi", 100, 50)));
        Picture cropped = PictureOperations.apply(withText, new Edit.Crop(new Rectangle(50, 20, 100, 60)));
        assertEquals(100, cropped.width());
        assertEquals(2, cropped.size());
        TextBox box = cropped.layer(1).text().orElseThrow();
        assertEquals(50, box.centreX(), 1e-9);
        assertEquals(30, box.centreY(), 1e-9);
    }

    @Test
    void turningTurnsTheTextAndItsPlace() {
        Picture withText = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hi", 150, 25)));
        Picture turned = PictureOperations.apply(withText, new Edit.Turn(Orientation.ROTATE_90));
        assertEquals(100, turned.width());
        assertEquals(200, turned.height());
        TextBox box = turned.layer(1).text().orElseThrow();
        assertEquals(90, box.rotation(), 1e-9);
        assertEquals(75, box.centreX(), 1e-6);
        assertEquals(150, box.centreY(), 1e-6);
    }

    @Test
    void resizeScalesTheTextSize() {
        Picture withText = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hi", 100, 50)));
        Picture half = PictureOperations.apply(withText, new Edit.Resize(100, 50, ResizeQuality.FAST));
        TextBox box = half.layer(1).text().orElseThrow();
        assertEquals(10, box.size(), 0.01);
        assertEquals(50, box.centreX(), 1e-6);
    }

    @Test
    void layerOrderDuplicateDeleteAndProperties() {
        Picture p = PictureOperations.apply(photo, new Edit.NewLayer(0, "A"));
        p = PictureOperations.apply(p, new Edit.DuplicateLayer(1, "A copy"));
        assertEquals(List.of("Background", "A", "A copy"), p.layers().stream().map(Layer::name).toList());
        p = PictureOperations.apply(p, new Edit.OrderLayer(2, 0));
        assertEquals("A copy", p.layer(0).name());
        p = PictureOperations.apply(p, new Edit.LayerProperties(1, "Photo", false, 40));
        assertEquals("Photo", p.layer(1).name());
        assertFalse(p.layer(1).visible());
        assertEquals(40, p.layer(1).opacity());
        p = PictureOperations.apply(p, new Edit.DeleteLayer(0));
        assertEquals(2, p.size());
        Picture single = photo;
        assertThrows(IllegalArgumentException.class, () -> PictureOperations.apply(single, new Edit.DeleteLayer(0)));
    }

    @Test
    void hiddenLayersAreNotShown() {
        Picture painted = PictureOperations.apply(photo, new Edit.Stroke(Edit.Target.above(0, "P"),
                List.of(new Dot(50, 25)), HARD, Color.BLACK, false));
        Picture hidden = PictureOperations.apply(painted, new Edit.LayerProperties(1, "P", false, 100));
        assertEquals(0xffff0000, hidden.composite().getRGB(50, 25));
    }

    @Test
    void mergeDownKeepsThePhotoOpaque() {
        Picture painted = PictureOperations.apply(photo, new Edit.Stroke(Edit.Target.above(0, "P"),
                List.of(new Dot(50, 25)), HARD, Color.BLACK, false));
        Picture merged = PictureOperations.apply(painted, new Edit.MergeDown(1));
        assertEquals(1, merged.size());
        assertEquals("Background", merged.layer(0).name());
        assertFalse(merged.layer(0).hasAlpha());
        assertEquals(0xff000000, merged.composite().getRGB(50, 25));
        assertThrows(IllegalArgumentException.class, () -> PictureOperations.apply(merged, new Edit.MergeDown(0)));
    }

    @Test
    void flattenMakesOneLayerOfWhatShows() {
        Picture p = PictureOperations.apply(photo, new Edit.Text(0, false, text("Hi", 100, 50)));
        p = PictureOperations.apply(p, new Edit.NewLayer(1, "Empty"));
        BufferedImage before = p.composite();
        Picture flat = PictureOperations.apply(p, new Edit.Flatten());
        assertEquals(1, flat.size());
        assertFalse(flat.layered());
        assertEquals(before.getRGB(100, 50), flat.composite().getRGB(100, 50));
    }

    @Test
    void anAddedPictureLiesInTheMiddleOnTop() {
        Picture p = PictureOperations.apply(photo, new Edit.AddPicture(TestPictures.filled(20, 10, 0x123456), "Logo"));
        assertEquals(2, p.size());
        assertEquals(90, p.layer(1).x());
        assertEquals(45, p.layer(1).y());
        assertEquals(0xff123456, p.composite().getRGB(100, 50));
    }

    @Test
    void adjustingWorksOnOneLayer() {
        Picture p = PictureOperations.apply(photo, new Edit.NewLayer(0, "Top"));
        Picture levelled = PictureOperations.apply(p, new Edit.AutoLevels(0));
        assertSame(p.layer(1), levelled.layer(1));
    }
}
