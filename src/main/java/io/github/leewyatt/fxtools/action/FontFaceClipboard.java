package io.github.leewyatt.fxtools.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds font file path metadata from the last "Copy @font-face CSS" action.
 * Used by {@link FontFacePasteProcessor} to resolve relative paths on paste.
 *
 * <p>Keys are font file names (e.g., "Rubik-Bold.ttf"), values are absolute paths
 * (e.g., "/project/src/main/resources/fonts/Rubik-Bold.ttf").</p>
 */
final class FontFaceClipboard {

    private static Map<String, String> fontFilePaths = Collections.emptyMap();

    private FontFaceClipboard() {
    }

    /**
     * Stores font file paths from a copy action.
     *
     * @param paths map of fileName → absolutePath
     */
    static void store(@NotNull Map<String, String> paths) {
        fontFilePaths = Map.copyOf(paths);
    }

    /**
     * Returns the absolute path for a font file name, or null if not found.
     */
    @Nullable
    static String getPath(@NotNull String fileName) {
        return fontFilePaths.get(fileName);
    }

}
