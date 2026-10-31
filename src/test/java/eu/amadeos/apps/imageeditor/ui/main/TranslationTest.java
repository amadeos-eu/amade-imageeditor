package eu.amadeos.apps.imageeditor.ui.main;

import eu.amadeos.apps.imageeditor.bootstrap.ImageEditorModule;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(ImageEditorModule.class, ImageEditorModule.MESSAGES,
                Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(ImageEditorModule.class, ImageEditorModule.MESSAGES));
    }

    /** No two entries share a mnemonic letter in the menu bar or in one menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = Map.of(
                "bar", List.of("menu.file", "menu.edit", "menu.image", "menu.view", "menu.options", "menu.help"),
                "file", List.of("menu.file.open", "menu.file.save", "menu.file.save-as", "menu.file.exit"),
                "edit", List.of("menu.edit.undo", "menu.edit.redo", "menu.edit.copy"),
                "edit-steps", List.of("menu.edit.undo.step", "menu.edit.redo.step", "menu.edit.copy"),
                "image", List.of("menu.image.crop", "menu.image.straighten", "menu.image.adjust",
                        "menu.image.resize", "menu.image.auto-levels", "menu.image.rotate-left",
                        "menu.image.rotate-right", "menu.image.flip-horizontal", "menu.image.flip-vertical"),
                "view", List.of("menu.view.zoom-in", "menu.view.zoom-out", "menu.view.fit", "menu.view.actual"),
                "options", List.of("menu.options.language"),
                "help", List.of("menu.help.about"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertNull(before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void germanNames() {
        assertEquals("Bildbearbeitung", texts(Language.GERMAN).get("window.title"));
    }
}
