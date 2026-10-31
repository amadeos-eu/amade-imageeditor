package eu.amadeos.apps.imageeditor.domain;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The picture formats the editor knows. The first five can be saved; the others open (through the TwelveMonkeys
 * ImageIO plugins) and are saved as one of the first five.
 */
public enum ImageFormat {
    JPEG("JPEG", "jpg", true, false, "jpg", "jpeg", "jpe", "jfif"),
    PNG("PNG", "png", true, true, "png"),
    WEBP("WebP", "webp", true, true, "webp"),
    TIFF("TIFF", "tif", true, true, "tif", "tiff"),
    BMP("BMP", "bmp", true, false, "bmp", "dib"),
    GIF("GIF", "gif", false, true, "gif"),
    PNM("PNM", "pnm", false, false, "pnm", "pbm", "pgm", "ppm", "pam"),
    TGA("TGA", "tga", false, true, "tga"),
    PSD("Photoshop", "psd", false, true, "psd"),
    IFF("IFF", "iff", false, false, "iff", "lbm", "ilbm"),
    PICT("PICT", "pct", false, false, "pct", "pict"),
    SGI("SGI", "sgi", false, false, "sgi", "rgb", "rgba", "bw"),
    ICNS("Apple icon", "icns", false, true, "icns");

    private final String label;
    private final String extension;
    private final boolean writable;
    private final boolean alpha;
    private final List<String> extensions;

    ImageFormat(String label, String extension, boolean writable, boolean alpha, String... extensions) {
        this.label = label;
        this.extension = extension;
        this.writable = writable;
        this.alpha = alpha;
        this.extensions = List.of(extensions);
    }

    /** The name shown in the Save As dialog, such as "JPEG". */
    public String label() {
        return label;
    }

    /** The extension a new file gets, without the dot. */
    public String extension() {
        return extension;
    }

    /** All extensions of the format, lower case, without dots. */
    public List<String> extensions() {
        return extensions;
    }

    /** True if the editor saves this format. */
    public boolean writable() {
        return writable;
    }

    /** True if the format keeps transparent pixels. */
    public boolean alpha() {
        return alpha;
    }

    /** True if saving loses detail, so that saving again and again makes it worse. */
    public boolean lossy() {
        return this == JPEG || this == WEBP;
    }

    /** The formats offered by Save As, in that order. */
    public static List<ImageFormat> writableFormats() {
        return Arrays.stream(values()).filter(ImageFormat::writable).toList();
    }

    public static Optional<ImageFormat> ofExtension(String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.extensions.contains(lower)).findFirst();
    }

    public static Optional<ImageFormat> of(Path file) {
        return ofExtension(extensionOf(file));
    }

    /** The extension of a file name, lower case, without the dot; empty if it has none. */
    public static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The file with this format's extension instead of its own (or added, if it has none). */
    public Path withExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        return file.resolveSibling(base + "." + extension);
    }

    /** True if the file's extension belongs to this format. */
    public boolean matches(Path file) {
        return extensions.contains(extensionOf(file));
    }

    public static boolean isImage(Path file) {
        return of(file).isPresent();
    }
}
