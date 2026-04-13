package io.github.leewyatt.fxtools.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconBrowserPanel;
import io.github.leewyatt.fxtools.toolwindow.svgtool.SvgPathToolPanel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Factory for the "JavaFX Tools" tool window.
 */
public class FxToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory cf = ContentFactory.getInstance();

        IconBrowserPanel iconBrowser = new IconBrowserPanel(project);
        Content iconContent = cf.createContent(
                iconBrowser, FxToolsBundle.message("icon.browser.tab"), false);
        Disposer.register(iconContent, iconBrowser);
        toolWindow.getContentManager().addContent(iconContent);

        SvgPathToolPanel svgTool = new SvgPathToolPanel(project);
        Content svgContent = cf.createContent(
                svgTool, FxToolsBundle.message("svg.tool.tab"), false);
        toolWindow.getContentManager().addContent(svgContent);

        toolWindow.setTitleActions(List.of(new ToolWindowLinksActionGroup()));
    }
}
