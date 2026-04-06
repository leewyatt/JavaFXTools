package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the real font family name(s) from selected .ttf/.otf files to the clipboard.
 * Supports multi-selection: each unique family name on its own line.
 */
public class CopyFontFamilyNameAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<VirtualFile> fontFiles = FontFileUtil.getSelectedFontFiles(e);
        if (fontFiles.isEmpty()) {
            return;
        }

        List<String> families = new ArrayList<>();
        for (VirtualFile file : fontFiles) {
            String family = FontFileUtil.extractFontFamily(file);
            if (family != null && !families.contains(family)) {
                families.add(family);
            }
        }

        if (!families.isEmpty()) {
            String text = String.join("\n", families);
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(FontFileUtil.hasFontFileSelected(e));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
