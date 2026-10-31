package eu.amadeos.apps.imageeditor.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.imageeditor.domain.ImageFormat;
import eu.amadeos.apps.imageeditor.domain.ResizeQuality;
import eu.amadeos.apps.imageeditor.domain.SaveOptions;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Choices of amade-imageeditor that last, in {@code ~/.config/amadeos/imageeditor/imageeditor.properties}: the
 * quality per format, what metadata to keep, the resize quality, the last folder. Each change is written at once;
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
