package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared utilities for font file actions.
 */
final class FontFileUtil {

    private static final Set<String> FONT_EXTENSIONS = Set.of("ttf", "otf");

    private FontFileUtil() {
    }

    /**
     * Returns selected font files sorted by name, or empty list if none selected.
     */
    @NotNull
    static List<VirtualFile> getSelectedFontFiles(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null) {
            return List.of();
        }
        List<VirtualFile> fontFiles = new ArrayList<>();
        for (VirtualFile file : files) {
            if (!file.isDirectory() && isFontExtension(file.getExtension())) {
                fontFiles.add(file);
            }
        }
        fontFiles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return fontFiles;
    }

    static boolean hasFontFileSelected(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null) {
            return false;
        }
        for (VirtualFile file : files) {
            if (!file.isDirectory() && isFontExtension(file.getExtension())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static String extractFontFamily(@NotNull VirtualFile file) {
        try (InputStream is = file.getInputStream()) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            return font.getFamily();
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isFontExtension(@Nullable String ext) {
        return ext != null && FONT_EXTENSIONS.contains(ext.toLowerCase());
    }
}
