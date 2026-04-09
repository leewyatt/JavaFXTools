package io.github.leewyatt.fxtools.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import io.github.leewyatt.fxtools.toolwindow.svgtool.SvgPathToolPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Opens the first selected .svg file in the SVG Path Extractor tool window panel.
 */
public class OpenInSvgPathExtractorAction extends AnAction implements DumbAware {

    private static final String TOOL_WINDOW_ID = "JavaFX Tools";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        List<VirtualFile> svgFiles = SvgFileUtil.getSelectedSvgFiles(e);
        if (svgFiles.isEmpty()) {
            return;
        }

        VirtualFile svgFile = svgFiles.get(0);
        File file = new File(svgFile.getPath());

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }

        toolWindow.show(() -> {
            ContentManager cm = toolWindow.getContentManager();
            for (Content content : cm.getContents()) {
                JComponent component = content.getComponent();
                if (component instanceof SvgPathToolPanel svgPanel) {
                    cm.setSelectedContent(content);
                    try {
                        String svgContent = new String(svgFile.contentsToByteArray(), svgFile.getCharset());
                        svgPanel.loadFile(file, svgContent);
                    } catch (Exception ignored) {
                    }
                    return;
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = e.getProject() != null && SvgFileUtil.hasSvgFileSelected(e);
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
