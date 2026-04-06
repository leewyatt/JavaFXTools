package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies @font-face CSS blocks for selected .ttf/.otf files to the clipboard.
 * Supports multi-selection: each file gets its own block with a style comment.
 */
public class CopyFontFaceCssAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<VirtualFile> fontFiles = FontFileUtil.getSelectedFontFiles(e);
        if (fontFiles.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        Map<String, String> fontPaths = new LinkedHashMap<>();
        int successCount = 0;
        for (VirtualFile file : fontFiles) {
            String family = FontFileUtil.extractFontFamily(file);
            if (family == null) {
                continue;
            }
            if (successCount > 0) {
                sb.append("\n\n");
            }
            sb.append("@font-face {\n");
            sb.append("    font-family: \"").append(family).append("\";\n");
            sb.append("    src: url(\"").append(file.getName()).append("\");\n");
            sb.append("}");
            fontPaths.put(file.getName(), file.getPath());
            successCount++;
        }

        if (successCount > 0) {
            FontFaceClipboard.store(fontPaths);
            CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
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
