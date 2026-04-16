package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects the build system used by a project by inspecting build descriptor files
 * in the project base directory.
 */
public final class BuildSystemDetector {

    public enum BuildSystem { MAVEN, GRADLE, NONE }

    public enum GradleDsl { KOTLIN, GROOVY, NONE }

    private BuildSystemDetector() {
    }

    /**
     * Returns the detected build system. Gradle is preferred over Maven when both
     * descriptors are present, since Gradle projects sometimes keep a legacy pom.xml.
     */
    @NotNull
    public static BuildSystem detect(@Nullable Project project) {
        if (project == null) {
            return BuildSystem.NONE;
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            return BuildSystem.NONE;
        }
        VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (baseDir == null) {
            return BuildSystem.NONE;
        }
        if (baseDir.findChild("build.gradle") != null
                || baseDir.findChild("build.gradle.kts") != null) {
            return BuildSystem.GRADLE;
        }
        if (baseDir.findChild("pom.xml") != null) {
            return BuildSystem.MAVEN;
        }
        return BuildSystem.NONE;
    }

    /**
     * Detects the Gradle DSL variant for the given module. Checks the module's content
     * root first, falling back to the project root when the module has no build script.
     */
    @NotNull
    public static GradleDsl detectGradleDsl(@NotNull Project project, @Nullable Module module) {
        if (module != null) {
            GradleDsl moduleDsl = detectGradleDslInDir(findModuleDir(module));
            if (moduleDsl != GradleDsl.NONE) {
                return moduleDsl;
            }
        }
        return detectGradleDslInDir(findProjectBaseDir(project));
    }

    /**
     * Returns true if a Gradle version catalog file exists at the standard location.
     */
    public static boolean hasVersionCatalog(@NotNull Project project) {
        VirtualFile baseDir = findProjectBaseDir(project);
        if (baseDir == null) {
            return false;
        }
        VirtualFile gradle = baseDir.findChild("gradle");
        return gradle != null && gradle.findChild("libs.versions.toml") != null;
    }

    @NotNull
    private static GradleDsl detectGradleDslInDir(@Nullable VirtualFile dir) {
        if (dir == null) {
            return GradleDsl.NONE;
        }
        if (dir.findChild("build.gradle.kts") != null) {
            return GradleDsl.KOTLIN;
        }
        if (dir.findChild("build.gradle") != null) {
            return GradleDsl.GROOVY;
        }
        return GradleDsl.NONE;
    }

    @Nullable
    private static VirtualFile findModuleDir(@NotNull Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        return roots.length > 0 ? roots[0] : null;
    }

    @Nullable
    private static VirtualFile findProjectBaseDir(@NotNull Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(basePath);
    }
}
