package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.leewyatt.fxtools.util.SvgPathExtractor;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies merged SVG path data from selected .svg files to the clipboard.
 * Supports multi-selection: each file's path data on its own line.
 * Shapes are automatically converted to paths.
 */
public class CopySvgPathDataAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<VirtualFile> svgFiles = SvgFileUtil.getSelectedSvgFiles(e);
        if (svgFiles.isEmpty()) {
            return;
        }

        List<String> paths = new ArrayList<>();
        for (VirtualFile file : svgFiles) {
            String pathData = SvgPathExtractor.extract(new File(file.getPath()), true);
            if (pathData != null && !pathData.isBlank()) {
                paths.add(pathData);
            }
        }

        if (!paths.isEmpty()) {
            String text = String.join("\n", paths);
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(SvgFileUtil.hasSvgFileSelected(e));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
