package io.github.leewyatt.fxtools.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconBrowserPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for the "JavaFX Tools" tool window.
 */
public class FxToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        IconBrowserPanel iconBrowser = new IconBrowserPanel();
        Content content = ContentFactory.getInstance().createContent(
                iconBrowser, FxToolsBundle.message("icon.browser.tab"), false);
        toolWindow.getContentManager().addContent(content);
    }
}
