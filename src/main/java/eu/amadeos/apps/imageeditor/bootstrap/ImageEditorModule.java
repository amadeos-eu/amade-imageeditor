package eu.amadeos.apps.imageeditor.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.imageeditor.repository.EditorPreferences;
import eu.amadeos.apps.imageeditor.service.ImageLoader;
import eu.amadeos.apps.imageeditor.service.ImageSaver;
import eu.amadeos.apps.imageeditor.ui.main.MainPresenter;
import eu.amadeos.apps.imageeditor.ui.main.MainWindow;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * amade-imageeditor bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ImageEditorModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.imageeditor.messages";

    @Override
    protected void configure() {
        bind(EditorPreferences.class).in(Scopes.SINGLETON);
        bind(ImageLoader.class).in(Scopes.SINGLETON);
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ImageEditorModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    MainWindow window(Messages msg, LocaleSettings locales) {
        return new MainWindow(msg, locales);
    }

    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, EditorPreferences preferences,
                            ImageLoader loader, XdgDirectories xdg, ExecutorService background, @Ui Executor ui) {
        return new MainPresenter(window, msg, locales, new MainPresenter.Services(preferences, loader,
                new ImageSaver(CommandRunner.system()), background, ui, xdg.pictures(), () -> System.exit(0)));
    }
}
