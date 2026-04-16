package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether JavaFX modules are present on a module's classpath and resolves
 * the version of the existing JavaFX installation.
 */
public final class JavaFxModulePresenceDetector {

    private static final Logger LOG = Logger.getInstance(JavaFxModulePresenceDetector.class);

    private static final Pattern JAVAFX_VERSION_IN_NAME =
            Pattern.compile("javafx[-.](base|graphics|controls|fxml|media|swing|web)[-.:]([\\d][\\w.\\-]+)");
    private static final Pattern JAVAFX_VERSION_IN_PATH =
            Pattern.compile("javafx[-.](base|graphics|controls|fxml|media|swing|web)[/\\\\-]([\\d][\\w.\\-]*?)\\.jar$");
    private static final Pattern JAVAFX_JAR_DIR_VERSION =
            Pattern.compile("[/\\\\]([\\d]+(?:\\.[\\d]+)*(?:[\\-.]\\w+)*)[/\\\\]javafx");

    private final boolean hasControls;
    private final boolean hasFxml;
    private final @Nullable String javafxVersion;

    private JavaFxModulePresenceDetector(boolean hasControls,
                                         boolean hasFxml,
                                         @Nullable String javafxVersion) {
        this.hasControls = hasControls;
        this.hasFxml = hasFxml;
        this.javafxVersion = javafxVersion;
    }

    /**
     * Runs marker-class detection and version resolution for the given module.
     *
     * @param module the module to inspect
     * @param javafxPluginVersion version extracted from the JavaFX Gradle plugin block,
     *                            or null if not applicable
     */
    @NotNull
    public static JavaFxModulePresenceDetector detect(@NotNull Module module,
                                                       @Nullable String javafxPluginVersion) {
        GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());

        boolean hasControls = facade.findClass(
                FxmlKitModuleConstants.JAVAFX_CONTROLS_MARKER, scope) != null;
        boolean hasFxml = facade.findClass(
                FxmlKitModuleConstants.JAVAFX_FXML_MARKER, scope) != null;

        String version = resolveVersion(module, javafxPluginVersion);
        return new JavaFxModulePresenceDetector(hasControls, hasFxml, version);
    }

    public boolean hasControls() {
        return hasControls;
    }

    public boolean hasFxml() {
        return hasFxml;
    }

    @Nullable
    public String getJavafxVersion() {
        return javafxVersion;
    }

    // ==================== Version resolution ====================

    @Nullable
    private static String resolveVersion(@NotNull Module module,
                                         @Nullable String javafxPluginVersion) {
        // Priority 1: JavaFX Gradle plugin version (passed in by caller)
        if (javafxPluginVersion != null && !javafxPluginVersion.isBlank()) {
            return javafxPluginVersion.trim();
        }

        // Priority 2 (Maven effective deps) skipped — requires Maven plugin API

        // Priority 3: library-name parsing (best-effort)
        String fromName = resolveFromLibraryNames(module);
        if (fromName != null) {
            return fromName;
        }

        // Priority 4: JAR path regex
        return resolveFromJarPaths(module);
    }

    @Nullable
    private static String resolveFromLibraryNames(@NotNull Module module) {
        try {
            String[] result = {null};
            ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(library -> {
                String name = library.getName();
                if (name == null) {
                    return true;
                }
                Matcher m = JAVAFX_VERSION_IN_NAME.matcher(name);
                if (m.find()) {
                    result[0] = m.group(2);
                    return false;
                }
                return true;
            });
            return result[0];
        } catch (Exception e) {
            LOG.info("Failed to resolve JavaFX version from library names", e);
            return null;
        }
    }

    @Nullable
    private static String resolveFromJarPaths(@NotNull Module module) {
        try {
            String[] result = {null};
            ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(library -> {
                String found = extractVersionFromLibraryFiles(library);
                if (found != null) {
                    result[0] = found;
                    return false;
                }
                return true;
            });
            return result[0];
        } catch (Exception e) {
            LOG.info("Failed to resolve JavaFX version from JAR paths", e);
            return null;
        }
    }

    @Nullable
    private static String extractVersionFromLibraryFiles(@NotNull Library library) {
        for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
            String path = file.getPath();
            if (!path.contains("javafx")) {
                continue;
            }
            Matcher m = JAVAFX_VERSION_IN_PATH.matcher(path);
            if (m.find()) {
                return m.group(2);
            }
            Matcher dirM = JAVAFX_JAR_DIR_VERSION.matcher(path);
            if (dirM.find()) {
                return dirM.group(1);
            }
        }
        return null;
    }
}
