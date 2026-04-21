package io.github.leewyatt.fxtools.fxmlkit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the FxmlKit version installed on a module's classpath.
 *
 * <p>Unlike {@link FxmlKitVersionResolver} which queries Maven Central for the latest
 * published version (used by Add Dependency Dialog), this reader inspects the module's
 * own libraries to determine what version is actually in use. Pure computation, no
 * network I/O, safe on any thread.
 *
 * <p>Priority order (FxmlKit ships as a plain library {@code com.dlsc.fxmlkit:fxmlkit},
 * no Gradle plugin equivalent to {@code org.openjfx.javafxplugin}):
 * <ol>
 *   <li>Library name regex matching {@code fxmlkit-<version>}</li>
 *   <li>JAR path regex matching Maven repository layout</li>
 *   <li>JAR {@code META-INF/MANIFEST.MF} {@code Implementation-Version} (fallback)</li>
 * </ol>
 */
public final class FxmlKitInstalledVersionReader {

    private static final Logger LOG = Logger.getInstance(FxmlKitInstalledVersionReader.class);

    private static final Pattern FXMLKIT_VERSION_IN_NAME =
            Pattern.compile("fxmlkit[-:]([\\d][\\w.\\-]+)");
    private static final Pattern FXMLKIT_VERSION_IN_PATH =
            Pattern.compile("com[/\\\\]dlsc[/\\\\]fxmlkit[/\\\\]fxmlkit[/\\\\]([\\d][\\w.\\-]+?)[/\\\\]");

    private FxmlKitInstalledVersionReader() {
    }

    /**
     * Returns the FxmlKit version installed on the module's classpath, or {@code null}
     * if FxmlKit is not present or the version cannot be determined.
     */
    @Nullable
    public static String read(@NotNull Module module) {
        String[] result = {null};
        try {
            ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(library -> {
                String found = readFromLibrary(library);
                if (found != null) {
                    result[0] = found;
                    return false;
                }
                return true;
            });
        } catch (Exception e) {
            LOG.info("Failed to read FxmlKit version from module " + module.getName(), e);
        }
        return result[0];
    }

    @Nullable
    private static String readFromLibrary(@NotNull Library library) {
        String name = library.getName();
        if (name != null) {
            Matcher m = FXMLKIT_VERSION_IN_NAME.matcher(name);
            if (m.find()) {
                return m.group(1);
            }
        }
        for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
            String path = file.getPath();
            if (!path.contains("fxmlkit")) {
                continue;
            }
            Matcher pathMatcher = FXMLKIT_VERSION_IN_PATH.matcher(path);
            if (pathMatcher.find()) {
                return pathMatcher.group(1);
            }
            String fromManifest = readFromManifest(file);
            if (fromManifest != null) {
                return fromManifest;
            }
        }
        return null;
    }

    @Nullable
    private static String readFromManifest(@NotNull VirtualFile jarRoot) {
        VirtualFile manifestFile = jarRoot.findFileByRelativePath("META-INF/MANIFEST.MF");
        if (manifestFile == null) {
            return null;
        }
        try {
            Manifest manifest = new Manifest(manifestFile.getInputStream());
            Attributes attrs = manifest.getMainAttributes();
            String version = attrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (version != null && !version.isBlank()) {
                return version.trim();
            }
        } catch (IOException e) {
            LOG.info("Failed to read MANIFEST.MF from " + jarRoot.getPath(), e);
        }
        return null;
    }
}
