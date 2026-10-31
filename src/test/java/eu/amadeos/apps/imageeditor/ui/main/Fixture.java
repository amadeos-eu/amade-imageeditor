package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.bootstrap.ImageEditorModule;
import eu.amadeos.apps.imageeditor.repository.EditorPreferences;
import eu.amadeos.apps.imageeditor.service.ImageLoader;
import eu.amadeos.apps.imageeditor.service.ImageSaver;
import eu.amadeos.apps.imageeditor.service.TestPictures;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** A picture folder and a presenter that does everything at once on the calling thread. */
final class Fixture {
    static final Messages MSG = Messages.load(ImageEditorModule.class, ImageEditorModule.MESSAGES, Locale.ENGLISH);

    final Path dir;
    final AtomicBoolean exited = new AtomicBoolean();
    final EditorPreferences preferences;

    Fixture(Path dir) {
        this.dir = dir;
        this.preferences = new EditorPreferences(dir.resolve("prefs.properties"));
    }

    /** lake.png, 400 × 300 in four colours. */
    Path picture() throws IOException {
        Path file = dir.resolve("lake.png");
        ImageIO.write(TestPictures.quarters(400, 300), "png", file.toFile());
        return file;
    }

    MainPresenter presenter(MainView view) {
        return presenter(view, CommandRunner.system());
    }

    MainPresenter presenter(MainView view, CommandRunner runner) {
        return new MainPresenter(view, MSG, null, new MainPresenter.Services(preferences, new ImageLoader(),
                new ImageSaver(runner), Runnable::run, Runnable::run, dir, () -> exited.set(true)));
    }
}
