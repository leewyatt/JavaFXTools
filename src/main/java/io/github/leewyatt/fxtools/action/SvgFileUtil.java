package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for SVG file actions.
 */
final class SvgFileUtil {

    private SvgFileUtil() {
    }

    /**
     * Returns selected SVG files sorted by name, or empty list if none selected.
     */
    @NotNull
    static List<VirtualFile> getSelectedSvgFiles(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null) {
            return List.of();
        }
        List<VirtualFile> svgFiles = new ArrayList<>();
        for (VirtualFile file : files) {
            if (!file.isDirectory() && isSvgExtension(file.getExtension())) {
                svgFiles.add(file);
            }
        }
        svgFiles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return svgFiles;
    }

    static boolean hasSvgFileSelected(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null) {
            return false;
        }
        for (VirtualFile file : files) {
            if (!file.isDirectory() && isSvgExtension(file.getExtension())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSvgExtension(@Nullable String ext) {
        return "svg".equalsIgnoreCase(ext);
    }
}
