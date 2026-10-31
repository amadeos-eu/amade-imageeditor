package eu.amadeos.apps.imageeditor.domain;

/**
 * How a picture is written: the format, the quality of lossy formats (0 to 100), WebP without loss, and which
 * metadata of the original comes along (only from a JPEG into a JPEG).
 */
public record SaveOptions(ImageFormat format, int quality, boolean lossless, boolean keepCamera,
                          boolean keepLocation, boolean keepDescription) {
    public static final int DEFAULT_JPEG_QUALITY = 90;
    public static final int DEFAULT_WEBP_QUALITY = 85;

    public SaveOptions {
        if (!format.writable()) throw new IllegalArgumentException(format + " cannot be saved");
        quality = Math.clamp(quality, 0, 100);
    }

    public static SaveOptions defaults(ImageFormat format) {
        return new SaveOptions(format, format == ImageFormat.WEBP ? DEFAULT_WEBP_QUALITY : DEFAULT_JPEG_QUALITY,
                false, true, true, true);
    }

    public SaveOptions withFormat(ImageFormat value) {
        return new SaveOptions(value, quality, lossless, keepCamera, keepLocation, keepDescription);
    }

    /** True if any metadata is to be kept. */
    public boolean keepsMetadata() {
        return keepCamera || keepDescription;
    }
}
