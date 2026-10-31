package eu.amadeos.apps.imageeditor.bootstrap;

import eu.amadeos.apps.imageeditor.ui.main.MainPresenter;
import eu.amadeos.apps.imageeditor.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AmadeImageEditorApplication {
    public static final AppInfo APP = new AppInfo("amade-imageeditor", "Image Editor", "imageeditor");

    private AmadeImageEditorApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeImageEditorApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-imageeditor " + version());
            return;
        }
        List<Path> paths = paths(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(paths);
        }, new ImageEditorModule());
    }
    // --8<-- [end:main]

    /** The files on the command line; the desktop entry passes them with %f, as paths. */
    static List<Path> paths(String[] args) {
        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.isBlank() || arg.startsWith("-")) continue;
            paths.add(Path.of(arg.startsWith("file://") ? java.net.URI.create(arg).getPath() : arg));
        }
        return paths;
    }
}
