package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.Dot;
import eu.amadeos.apps.imageeditor.domain.Edit;
import eu.amadeos.apps.imageeditor.domain.Picture;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRasterTest {
    @TempDir
    Path dir;

    private Picture layered() {
        Picture p = Picture.of(TestPictures.quarters(200, 100), "Background");
        p = PictureOperations.apply(p, new Edit.Stroke(Edit.Target.above(0, "Painting"),
                List.of(new Dot(40, 20), new Dot(160, 20)), new Brush(8, 100, 100), Color.BLACK, false));
        p = PictureOperations.apply(p, new Edit.Text(1, false, PictureOperationsTest.text("Lake", 100, 70)));
        p = PictureOperations.apply(p, new Edit.MoveLayer(1, 5, 7));
        return PictureOperations.apply(p, new Edit.LayerProperties(1, "Painting", true, 60));
    }

    @Test
    void writesWhatOtherProgramsExpect() throws IOException {
        Path file = dir.resolve("lake.ora");
        OpenRaster.write(layered(), file);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<String> names = new ArrayList<>();
            zip.stream().forEach(e -> names.add(e.getName()));
            assertEquals("mimetype", names.getFirst());
            ZipEntry mimetype = zip.getEntry("mimetype");
            assertEquals(ZipEntry.STORED, mimetype.getMethod());
            assertEquals("image/openraster", new String(zip.getInputStream(mimetype).readAllBytes(),
                    StandardCharsets.US_ASCII));
            assertTrue(names.contains("stack.xml"));
            assertTrue(names.contains("mergedimage.png"));
            assertTrue(names.contains("Thumbnails/thumbnail.png"));
            String stack = new String(zip.getInputStream(zip.getEntry("stack.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(stack.indexOf("Lake") < stack.indexOf("Background"), "the top layer comes first");
            assertTrue(stack.contains("opacity=\"0.6"), stack);
        }
        assertTrue(OpenRaster.recognises(file));
        assertFalse(Files.exists(dir.resolve("lake.ora.part")));
    }

    @Test
    void readsBackLayersPlacesAndText() throws IOException {
        Picture original = layered();
        Path file = dir.resolve("lake.ora");
        OpenRaster.write(original, file);
        Picture read = OpenRaster.read(file);
        assertEquals(200, read.width());
        assertEquals(100, read.height());
        assertEquals(3, read.size());
        assertEquals(List.of("Background", "Painting", "Lake"),
                read.layers().stream().map(l -> l.name()).toList());
        assertEquals(5, read.layer(1).x());
        assertEquals(7, read.layer(1).y());
        assertEquals(60, read.layer(1).opacity());
        TextBox text = read.layer(2).text().orElseThrow();
        assertEquals(original.layer(2).text().orElseThrow(), text);
        for (int[] at : new int[][]{{10, 10}, {100, 27}, {150, 80}}) {
            assertEquals(original.composite().getRGB(at[0], at[1]), read.composite().getRGB(at[0], at[1]),
                    "pixel " + at[0] + "," + at[1]);
        }
    }

    @Test
    void aZipThatIsNoOpenRasterIsRefused() throws IOException {
        Path file = dir.resolve("other.ora");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("application/zip".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        assertFalse(OpenRaster.recognises(file));
        assertThrows(IOException.class, () -> OpenRaster.read(file));
    }

    @Test
    void aDoctypeIsNotFollowed() throws IOException {
        Path file = dir.resolve("evil.ora");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write("image/openraster".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("stack.xml"));
            zip.write(("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                    + "<image w=\"1\" h=\"1\"><stack>&e;</stack></image>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> OpenRaster.read(file));
    }
}
