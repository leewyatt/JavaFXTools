package io.github.leewyatt.fxtools.fxmlkit;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whether a project depends on the FxmlKit framework.
 */
public final class FxmlKitDetector {

    private static final String FXML_VIEW_CLASS = "com.dlsc.fxmlkit.fxml.FxmlView";

    private FxmlKitDetector() {
    }

    /**
     * Checks whether the given project has FxmlKit on its classpath.
     */
    public static boolean isFxmlKitProject(@NotNull Project project) {
        return JavaPsiFacade.getInstance(project)
                .findClass(FXML_VIEW_CLASS, GlobalSearchScope.allScope(project)) != null;
    }
}
