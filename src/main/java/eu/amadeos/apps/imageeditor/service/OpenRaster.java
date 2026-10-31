package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.Layer;
import eu.amadeos.apps.imageeditor.domain.Picture;
import eu.amadeos.apps.imageeditor.domain.TextBox;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * OpenRaster ({@code .ora}), the open layered format of GIMP, Krita and MyPaint: a ZIP file with the stored
 * {@code mimetype} first, {@code stack.xml} describing the layers from the top down, one PNG per layer, the merged
 * picture and a thumbnail.
 *
 * <p>Text layers are written as their pixels, which every program shows, plus the text and its look as
 * attributes of our own namespace on the layer. Other programs ignore those; this editor reads them back, so the
 * text can be edited again.
 */
public final class OpenRaster {
    public static final String MIME = "image/openraster";
    /** The namespace of the editor's own layer attributes. */
    public static final String NS = "https://amadeos.eu/ns/imageeditor/1";
    private static final int THUMBNAIL = 256;

    private OpenRaster() {
    }

    // --8<-- [start:write]
    public static void write(Picture picture, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // The mimetype comes first and uncompressed, so that a program can recognise the file by its start.
            byte[] mime = MIME.getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(mime);
            zip.closeEntry();

            List<Layer> layers = picture.layers();
            for (int i = 0; i < layers.size(); i++) {
                put(zip, "data/layer" + i + ".png", png(layers.get(i).image()));
            }
            put(zip, "stack.xml", stack(picture));
            BufferedImage merged = picture.composite();
            put(zip, "mergedimage.png", png(merged));
            put(zip, "Thumbnails/thumbnail.png", png(thumbnail(merged)));
        }
    }
    // --8<-- [end:write]

    /** Writes next to the target first and then moves the file into place, like {@link ImageSaver}. */
    public static void write(Picture picture, Path target) throws IOException {
        Path part = target.resolveSibling("." + target.getFileName() + ".part");
        try {
            try (OutputStream out = Files.newOutputStream(part)) {
                write(picture, out);
            }
            Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) throw new IOException("No PNG writer");
        return bytes.toByteArray();
    }

    private static BufferedImage thumbnail(BufferedImage image) {
        double scale = Math.min(1, (double) THUMBNAIL / Math.max(image.getWidth(), image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return ImageOperations.resize(image, w, h, eu.amadeos.apps.imageeditor.domain.ResizeQuality.BEST);
    }

    // --8<-- [start:stack]
    static byte[] stack(Picture picture) throws IOException {
        try {
            Document doc = builder().newDocument();
            Element image = doc.createElement("image");
            image.setAttribute("version", "0.0.6");
            image.setAttribute("w", Integer.toString(picture.width()));
            image.setAttribute("h", Integer.toString(picture.height()));
            image.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:amade", NS);
            doc.appendChild(image);
            Element stack = doc.createElement("stack");
            image.appendChild(stack);
            List<Layer> layers = picture.layers();
            // stack.xml lists the top layer first.
            for (int i = layers.size() - 1; i >= 0; i--) {
                Layer layer = layers.get(i);
                Element e = doc.createElement("layer");
                e.setAttribute("name", layer.name());
                e.setAttribute("src", "data/layer" + i + ".png");
                e.setAttribute("x", Integer.toString(layer.x()));
                e.setAttribute("y", Integer.toString(layer.y()));
                e.setAttribute("opacity", String.format(Locale.ROOT, "%.3f", layer.opacity() / 100.0));
                e.setAttribute("visibility", layer.visible() ? "visible" : "hidden");
                e.setAttribute("composite-op", "svg:src-over");
                layer.text().ifPresent(box -> textAttributes(e, box));
                stack.appendChild(e);
            }
            Transformer out = TransformerFactory.newInstance().newTransformer();
            out.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            out.setOutputProperty(OutputKeys.INDENT, "yes");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            out.transform(new DOMSource(doc), new StreamResult(bytes));
            return bytes.toByteArray();
        } catch (ParserConfigurationException | TransformerException e) {
            throw new IOException("Cannot write stack.xml", e);
        }
    }

    private static void textAttributes(Element e, TextBox box) {
        // Line breaks in an attribute are written as &#10;, so they survive reading.
        e.setAttributeNS(NS, "amade:text", box.text());
        e.setAttributeNS(NS, "amade:font", box.font());
        e.setAttributeNS(NS, "amade:size", Float.toString(box.size()));
        e.setAttributeNS(NS, "amade:bold", Boolean.toString(box.bold()));
        e.setAttributeNS(NS, "amade:italic", Boolean.toString(box.italic()));
        e.setAttributeNS(NS, "amade:align", box.align().name().toLowerCase(Locale.ROOT));
        e.setAttributeNS(NS, "amade:colour", hex(box.colour()));
        e.setAttributeNS(NS, "amade:effect", box.effect().name().toLowerCase(Locale.ROOT));
        e.setAttributeNS(NS, "amade:effect-colour", hex(box.effectColour()));
        e.setAttributeNS(NS, "amade:centre-x", Double.toString(box.centreX()));
        e.setAttributeNS(NS, "amade:centre-y", Double.toString(box.centreY()));
        e.setAttributeNS(NS, "amade:rotation", Double.toString(box.rotation()));
    }
    // --8<-- [end:stack]

    static String hex(Color c) {
        return String.format(Locale.ROOT, "#%08x", c.getRGB());
    }

    static Color colour(String hex) {
        return new Color((int) Long.parseLong(hex.substring(1), 16), true);
    }

    // ---------------------------------------------------------------- reading

    /** True if the file starts like an OpenRaster file. */
    public static boolean recognises(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(30 + 8 + MIME.length());
            return new String(head, StandardCharsets.ISO_8859_1).endsWith("mimetype" + MIME);
        } catch (IOException e) {
            return false;
        }
    }

    // --8<-- [start:read]
    /**
     * Reads the layers. Stacks inside the stack are laid out flat; layer modes other than normal are read as
     * normal. Layers without our text attributes are pixels, also if another program made them from text.
     */
    public static Picture read(Path file) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        byte[] stackXml = entries.get("stack.xml");
        if (stackXml == null) throw new IOException("Not an OpenRaster file (no stack.xml): " + file.getFileName());
        try {
            Document doc = builder().parse(new ByteArrayInputStream(stackXml));
            Element image = doc.getDocumentElement();
            int w = Integer.parseInt(image.getAttribute("w")), h = Integer.parseInt(image.getAttribute("h"));
            List<Layer> topDown = new ArrayList<>();
            collect(image, entries, topDown);
            if (topDown.isEmpty()) throw new IOException("No layers in " + file.getFileName());
            return new Picture(w, h, topDown.reversed());
        } catch (org.xml.sax.SAXException | ParserConfigurationException | NumberFormatException e) {
            throw new IOException("Broken stack.xml in " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
    // --8<-- [end:read]

    private static void collect(Element parent, Map<String, byte[]> entries, List<Layer> topDown) throws IOException {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e)) continue;
            switch (e.getTagName()) {
                case "stack" -> collect(e, entries, topDown);
                case "layer" -> {
                    byte[] png = entries.get(e.getAttribute("src"));
                    if (png == null) continue;
                    BufferedImage pixels = ImageIO.read(new ByteArrayInputStream(png));
                    if (pixels == null) continue;
                    pixels = ImageOperations.normalize(pixels);
                    int x = number(e, "x"), y = number(e, "y");
                    String opacityText = e.getAttribute("opacity");
                    int opacity = opacityText.isEmpty() ? 100
                            : (int) Math.round(Math.clamp(Double.parseDouble(opacityText), 0, 1) * 100);
                    boolean visible = !"hidden".equals(e.getAttribute("visibility"));
                    String name = e.getAttribute("name").isEmpty() ? "Layer" : e.getAttribute("name");
                    topDown.add(new Layer(name, pixels, x, y, visible, opacity, text(e)));
                }
                default -> {
                }
            }
        }
    }

    private static int number(Element e, String attribute) {
        String v = e.getAttribute(attribute);
        return v.isEmpty() ? 0 : (int) Math.round(Double.parseDouble(v));
    }

    /** The text of a layer this editor wrote; null for other layers or if the attributes do not make sense. */
    static TextBox text(Element e) {
        if (!e.hasAttributeNS(NS, "text")) return null;
        try {
            return new TextBox(e.getAttributeNS(NS, "text"), e.getAttributeNS(NS, "font"),
                    Float.parseFloat(e.getAttributeNS(NS, "size")),
                    Boolean.parseBoolean(e.getAttributeNS(NS, "bold")),
                    Boolean.parseBoolean(e.getAttributeNS(NS, "italic")),
                    TextBox.Align.valueOf(e.getAttributeNS(NS, "align").toUpperCase(Locale.ROOT)),
                    colour(e.getAttributeNS(NS, "colour")),
                    TextBox.Effect.valueOf(e.getAttributeNS(NS, "effect").toUpperCase(Locale.ROOT)),
                    colour(e.getAttributeNS(NS, "effect-colour")),
                    Double.parseDouble(e.getAttributeNS(NS, "centre-x")),
                    Double.parseDouble(e.getAttributeNS(NS, "centre-y")),
                    Double.parseDouble(e.getAttributeNS(NS, "rotation")));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static DocumentBuilder builder() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // stack.xml comes from any file: no DTDs, no outside entities.
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder();
    }
}
