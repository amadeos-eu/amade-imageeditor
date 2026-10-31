package eu.amadeos.apps.imageeditor.service;

import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.Orientation;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.ProgramMissingException;
import eu.amadeos.commons.process.ProgramResult;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Writes the edited picture. The file is first written next to the target as a hidden {@code .part} file and
 * then moved over it, so that a failure never leaves half a picture behind.
 * <ul>
 *     <li>JPEG with ImageIO at the chosen quality; the original JPEG's metadata comes along
 *     ({@link JpegMetadata}). If the only edits are quarter turns and flips, {@code jpegtran} moves the
 *     compressed blocks instead, without any loss.</li>
 *     <li>PNG, BMP with ImageIO; TIFF with LZW compression.</li>
 *     <li>WebP with {@code cwebp} (package webp), from a temporary PNG.</li>
 * </ul>
 */
public class ImageSaver {
    private final CommandRunner runner;

    public ImageSaver(CommandRunner runner) {
        this.runner = runner;
    }

    /** Where the picture came from. */
    public record Source(Path file, ImageFormat format, Orientation stored) {
    }

    /**
     * How the save went.
     *
     * @param lossless true if jpegtran wrote the JPEG without decoding it
     * @param trimmed  true if jpegtran had to drop a strip of up to 15 pixels at the right or bottom edge
     */
    public record Result(boolean lossless, boolean trimmed) {
    }

    /** cwebp is not installed. */
    public static final class CwebpMissingException extends IOException {
        public CwebpMissingException() {
            super("cwebp is not installed");
        }
    }

    /**
     * @param image     the edited picture, upright
     * @param onlyTurns the quarter turns and flips, if they are the only edits
     */
    public Result save(BufferedImage image, Optional<Orientation> onlyTurns, Source source, Path target,
                       SaveOptions options) throws IOException, InterruptedException {
        Path part = target.resolveSibling("." + target.getFileName() + ".part");
        try {
            Result result = write(image, onlyTurns, source, part, options);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return result;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private Result write(BufferedImage image, Optional<Orientation> onlyTurns, Source source, Path part,
                         SaveOptions options) throws IOException, InterruptedException {
        boolean fromJpeg = source != null && source.format() == ImageFormat.JPEG && Files.isRegularFile(source.file());
        return switch (options.format()) {
            case JPEG -> {
                if (fromJpeg && onlyTurns.isPresent()) {
                    Optional<Result> lossless = losslessJpeg(source, source.stored().then(onlyTurns.get()), part,
                            options, image.getWidth(), image.getHeight());
                    if (lossless.isPresent()) yield lossless.get();
                }
                byte[] jpeg = encodeJpeg(ImageOperations.flatten(image), options.quality());
                if (fromJpeg && options.keepsMetadata()) {
                    JpegMetadata.Segments segments = JpegMetadata.read(Files.readAllBytes(source.file()));
                    jpeg = JpegMetadata.insert(jpeg, segments, options, image.getWidth(), image.getHeight());
                }
                Files.write(part, jpeg);
                yield new Result(false, false);
            }
            case PNG -> {
                writeWith("png", image, part);
                yield new Result(false, false);
            }
            case BMP -> {
                writeWith("bmp", ImageOperations.flatten(image), part);
                yield new Result(false, false);
            }
            case TIFF -> {
                writeTiff(image, part);
                yield new Result(false, false);
            }
            case WEBP -> {
                writeWebp(image, part, options);
                yield new Result(false, false);
            }
            default -> throw new IOException("Cannot save " + options.format().label());
        };
    }

    // --8<-- [start:lossless]
    /** jpegtran turns the original's blocks; empty if jpegtran is missing or fails, then the JPEG is encoded. */
    private Optional<Result> losslessJpeg(Source source, Orientation total, Path part, SaveOptions options,
                                          int width, int height) throws IOException, InterruptedException {
        boolean trimmed = false;
        try {
            // -perfect refuses pictures whose edge blocks cannot be turned; -trim then drops those blocks.
            ProgramResult result = jpegtran(total, "-perfect", source.file(), part);
            if (!result.succeeded() && total != Orientation.NORMAL) {
                result = jpegtran(total, "-trim", source.file(), part);
                trimmed = true;
            }
            if (!result.succeeded()) return Optional.empty();
        } catch (ProgramMissingException e) {
            return Optional.empty();
        }
        byte[] jpeg = Files.readAllBytes(part);
        if (trimmed) {
            BufferedImage size = ImageIO.read(part.toFile());
            if (size != null) {
                width = size.getWidth();
                height = size.getHeight();
            }
        }
        JpegMetadata.Segments segments = options.keepsMetadata()
                ? JpegMetadata.read(Files.readAllBytes(source.file())) : JpegMetadata.Segments.NONE;
        Files.write(part, JpegMetadata.insert(jpeg, segments, options, width, height));
        return Optional.of(new Result(true, trimmed));
    }

    private ProgramResult jpegtran(Orientation turn, String edges, Path in, Path out)
            throws IOException, InterruptedException {
        // The ICC profile describes the colours of the unchanged blocks, so it stays; the rest comes from
        // JpegMetadata, corrected.
        List<String> command = new ArrayList<>(List.of("jpegtran", "-copy", "icc", edges));
        command.addAll(turn.jpegtranOptions());
        command.addAll(List.of("-outfile", out.toString(), in.toString()));
        return runner.run(command, Duration.ofSeconds(60));
    }
    // --8<-- [end:lossless]

    static byte[] encodeJpeg(BufferedImage image, int quality) throws IOException {
        ImageWriter writer = writer("jpeg");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static void writeTiff(BufferedImage image, Path part) throws IOException {
        ImageWriter writer = writer("tiff");
        try (ImageOutputStream out = ImageIO.createImageOutputStream(part.toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && Arrays.asList(types).contains("LZW")) param.setCompressionType("LZW");
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static void writeWith(String format, BufferedImage image, Path part) throws IOException {
        if (!ImageIO.write(image, format, part.toFile())) throw new IOException("No writer for " + format);
    }

    // --8<-- [start:webp]
    private void writeWebp(BufferedImage image, Path part, SaveOptions options)
            throws IOException, InterruptedException {
        Path png = Files.createTempFile("amade-imageeditor-", ".png");
        try {
            writeWith("png", image, png);
            List<String> command = new ArrayList<>(List.of("cwebp", "-quiet", "-mt", "-metadata", "none"));
            if (options.lossless()) command.addAll(List.of("-lossless", "-exact"));
            else command.addAll(List.of("-q", String.valueOf(options.quality())));
            command.addAll(List.of(png.toString(), "-o", part.toString()));
            ProgramResult result;
            try {
                result = runner.run(command, Duration.ofMinutes(2));
            } catch (ProgramMissingException e) {
                throw new CwebpMissingException();
            }
            if (!result.succeeded()) throw new IOException(result.failure("cwebp"));
        } finally {
            Files.deleteIfExists(png);
        }
    }
    // --8<-- [end:webp]

    private static ImageWriter writer(String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("No writer for " + format);
        return writers.next();
    }
}
