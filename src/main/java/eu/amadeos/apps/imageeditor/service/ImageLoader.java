package eu.amadeos.apps.imageeditor.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.Picture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Opens pictures with ImageIO (the JDK's readers and the TwelveMonkeys plugins) and turns them upright as their
 * EXIF orientation says, so that a phone photo taken upright opens upright.
 */
public class ImageLoader {
    private final String backgroundName;

    public ImageLoader() {
        this("Background");
    }

    /** {@code backgroundName} names the one layer of a picture opened from an ordinary file. */
    public ImageLoader(String backgroundName) {
        this.backgroundName = backgroundName;
    }

    /** A picture as opened; an OpenRaster file with all its layers. */
    public record Loaded(Path file, ImageFormat format, Picture picture, Orientation stored) {
        /** The picture as it shows, all layers merged. */
        public BufferedImage image() {
            return picture.composite();
        }
    }

    public Loaded load(Path file) throws IOException {
        if (ImageFormat.ORA.matches(file) || OpenRaster.recognises(file)) {
            return new Loaded(file, ImageFormat.ORA, OpenRaster.read(file), Orientation.NORMAL);
        }
        BufferedImage raw = read(file);
        Orientation stored = orientation(file);
        BufferedImage upright = ImageOperations.normalize(raw);
        if (stored != Orientation.NORMAL) upright = ImageOperations.turn(upright, stored);
        // The extension decides the format the file is saved back to; a reader found by content wins.
        ImageFormat format = ImageFormat.of(file).orElse(ImageFormat.PNG);
        return new Loaded(file, format, Picture.of(upright, backgroundName), stored);
    }

    /** The whole picture as stored; of an animated or layered file the first frame or the merged picture. */
    static BufferedImage read(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new IOException("Cannot read " + file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new UnsupportedFormatException(file);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** The EXIF orientation; normal when there is none or it cannot be read. */
    static Orientation orientation(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return Orientation.ofExif(exif.getInt(ExifIFD0Directory.TAG_ORIENTATION));
            }
        } catch (ImageProcessingException | IOException | MetadataException | RuntimeException e) {
            // A picture without readable metadata is shown as stored.
        }
        return Orientation.NORMAL;
    }

    /** No reader knows the file. */
    public static final class UnsupportedFormatException extends IOException {
        public UnsupportedFormatException(Path file) {
            super("Unknown picture format: " + file.getFileName());
        }
    }
}
