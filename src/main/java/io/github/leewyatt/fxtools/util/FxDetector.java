package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Detects whether the current project is a JavaFX project.
 * Result is cached per project using a WeakHashMap (auto-cleared when project is disposed).
 */
public final class FxDetector {

    private static final String JAVAFX_MARKER_CLASS = "javafx.scene.Node";
    private static final Map<Project, Boolean> CACHE = new WeakHashMap<>();

    private FxDetector() {
    }

    /**
     * Returns true if the project has JavaFX on its classpath.
     */
    public static boolean isJavaFxProject(@NotNull Project project) {
        if (project.isDisposed()) {
            return false;
        }
        return CACHE.computeIfAbsent(project, FxDetector::detect);
    }

    /**
     * Clears the cached result for the given project.
     * Call this if dependencies might have changed.
     */
    public static void clearCache(@NotNull Project project) {
        CACHE.remove(project);
    }

    private static boolean detect(@NotNull Project project) {
        try {
            return JavaPsiFacade.getInstance(project)
                    .findClass(JAVAFX_MARKER_CLASS, GlobalSearchScope.allScope(project)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
