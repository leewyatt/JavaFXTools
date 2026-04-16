package io.github.leewyatt.fxtools.fxmlkit;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whether a project or module depends on the FxmlKit framework.
 */
public final class FxmlKitDetector {

    private static final String FXML_VIEW_CLASS = "com.dlsc.fxmlkit.fxml.FxmlView";

    private FxmlKitDetector() {
    }

    /**
     * Checks whether the given project has FxmlKit on any module's classpath.
     */
    public static boolean isFxmlKitProject(@NotNull Project project) {
        return JavaPsiFacade.getInstance(project)
                .findClass(FXML_VIEW_CLASS, GlobalSearchScope.allScope(project)) != null;
    }

    /**
     * Checks whether the given module has FxmlKit on its own classpath.
     * Use this for per-module dependency checks in multi-module projects.
     */
    public static boolean isFxmlKitModule(@NotNull Module module) {
        GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        return JavaPsiFacade.getInstance(module.getProject())
                .findClass(FXML_VIEW_CLASS, scope) != null;
    }
}
