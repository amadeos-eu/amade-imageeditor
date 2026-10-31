package eu.amadeos.apps.imageeditor.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.imageeditor.domain.Brush;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.LineStyle;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.apps.imageeditor.domain.ShapeFill;
import eu.amadeos.apps.imageeditor.domain.ShapeKind;
import eu.amadeos.apps.imageeditor.domain.PaintSettings;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.awt.Color;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Choices of amade-imageeditor that last, in {@code ~/.config/amadeos/imageeditor/imageeditor.properties}: the
 * quality per format, what metadata to keep, the resize quality, the last folder, the colours, brush and shape
 * settings of the painting tools. Each change is written at once;
 * unknown or broken values fall back to the defaults.
 */
public class EditorPreferences {
    public static final String FILE = "imageeditor.properties";

    private final Path file;
    private final Properties properties;

    @Inject
    public EditorPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public EditorPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    /** The save options last used for the format. */
    public SaveOptions saveOptions(ImageFormat format) {
        SaveOptions d = SaveOptions.defaults(format);
        String prefix = "save." + format.extension() + ".";
        return new SaveOptions(format, number(prefix + "quality", d.quality()),
                flag(prefix + "lossless", d.lossless()), flag("save.keep-camera", d.keepCamera()),
                flag("save.keep-location", d.keepLocation()), flag("save.keep-description", d.keepDescription()));
    }

    public void setSaveOptions(SaveOptions options) {
        String prefix = "save." + options.format().extension() + ".";
        properties.setProperty(prefix + "quality", String.valueOf(options.quality()));
        properties.setProperty(prefix + "lossless", String.valueOf(options.lossless()));
        properties.setProperty("save.keep-camera", String.valueOf(options.keepCamera()));
        properties.setProperty("save.keep-location", String.valueOf(options.keepLocation()));
        properties.setProperty("save.keep-description", String.valueOf(options.keepDescription()));
        store();
    }

    public ResizeQuality resizeQuality() {
        try {
            return ResizeQuality.valueOf(properties.getProperty("resize.quality", "BEST").strip());
        } catch (IllegalArgumentException e) {
            return ResizeQuality.BEST;
        }
    }

    public void setResizeQuality(ResizeQuality quality) {
        properties.setProperty("resize.quality", quality.name());
        store();
    }

    /** Straighten crops to the largest rectangle (true) or keeps the size with empty corners. */
    public boolean straightenCrops() {
        return flag("straighten.crop", true);
    }

    public void setStraightenCrops(boolean crop) {
        properties.setProperty("straighten.crop", String.valueOf(crop));
        store();
    }

    public Optional<Path> lastFolder() {
        String value = properties.getProperty("folder.last");
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(value.strip()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public void setLastFolder(Path folder) {
        properties.setProperty("folder.last", folder.toAbsolutePath().toString());
        store();
    }

    // --8<-- [start:paint]
    /** The painting settings last used; broken values fall back to {@code defaults} one by one. */
    public PaintSettings paintSettings(PaintSettings defaults) {
        return new PaintSettings(colour("paint.primary", defaults.primary()),
                colour("paint.secondary", defaults.secondary()), brush("paint.brush", defaults.brush()),
                brush("paint.eraser", defaults.eraser()), named("paint.shape", ShapeKind.class, defaults.shape()),
                line(defaults.line()), named("paint.fill", ShapeFill.class, defaults.fill()),
                Math.clamp(number("paint.tolerance", defaults.tolerance()), 0, 255));
    }

    public void setPaintSettings(PaintSettings p) {
        properties.setProperty("paint.primary", hex(p.primary()));
        properties.setProperty("paint.secondary", hex(p.secondary()));
        setBrush("paint.brush", p.brush());
        setBrush("paint.eraser", p.eraser());
        properties.setProperty("paint.shape", p.shape().name());
        properties.setProperty("paint.line.width", String.valueOf(p.line().width()));
        properties.setProperty("paint.line.dash", p.line().dash().name());
        properties.setProperty("paint.fill", p.fill().name());
        properties.setProperty("paint.tolerance", String.valueOf(p.tolerance()));
        store();
    }
    // --8<-- [end:paint]

    /** Brush strokes and shapes on a photo of one layer go to a new layer above it (default) or onto the photo. */
    public boolean paintOnNewLayer() {
        return flag("paint.new-layer", true);
    }

    public void setPaintOnNewLayer(boolean on) {
        properties.setProperty("paint.new-layer", String.valueOf(on));
        store();
    }

    private Color colour(String key, Color fallback) {
        String value = properties.getProperty(key, "").strip();
        if (!value.matches("#[0-9a-fA-F]{6}")) return fallback;
        return new Color(Integer.parseInt(value.substring(1), 16));
    }

    private static String hex(Color c) {
        return String.format(java.util.Locale.ROOT, "#%06x", c.getRGB() & 0xffffff);
    }

    private Brush brush(String key, Brush fallback) {
        try {
            return new Brush(number(key + ".size", fallback.size()), number(key + ".hardness", fallback.hardness()),
                    number(key + ".opacity", fallback.opacity()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void setBrush(String key, Brush brush) {
        properties.setProperty(key + ".size", String.valueOf(brush.size()));
        properties.setProperty(key + ".hardness", String.valueOf(brush.hardness()));
        properties.setProperty(key + ".opacity", String.valueOf(brush.opacity()));
    }

    private LineStyle line(LineStyle fallback) {
        try {
            return new LineStyle(number("paint.line.width", fallback.width()),
                    named("paint.line.dash", LineStyle.Dash.class, fallback.dash()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E named(String key, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, properties.getProperty(key, fallback.name()).strip());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int number(String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, "").strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean flag(String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : !"false".equals(value.strip());
    }

    private void store() {
        PropertiesFile.store(file, properties, "amade-imageeditor");
    }
}
