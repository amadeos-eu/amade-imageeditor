package eu.amadeos.apps.imageeditor.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTest {
    @Test
    void pathsFromTheCommandLine() {
        assertEquals(List.of(Path.of("/home/anna/a b.jpg"), Path.of("rel.png")),
                AmadeImageEditorApplication.paths(new String[]{"--x", "file:///home/anna/a%20b.jpg", "", "rel.png"}));
    }

    @Test
    void versionWithoutAJar() {
        assertEquals("dev", AmadeImageEditorApplication.version());
    }

    @Test
    void appInfo() {
        assertEquals("amade-imageeditor", AmadeImageEditorApplication.APP.id());
    }
}
