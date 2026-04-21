package io.github.leewyatt.fxtools.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAware;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Tool window title action that shows a popup menu of JavaFX documentation links.
 */
public final class ToolWindowLinksActionGroup extends DefaultActionGroup implements DumbAware {

    /**
     * The JavaFX version whose javadoc the version-specific links point to.
     * Update this when a new LTS javadoc is published on openjfx.io.
     */
    private static final String JAVAFX_DOC_VERSION = "26";
    private static final String JAVADOC_BASE = "https://openjfx.io/javadoc/" + JAVAFX_DOC_VERSION;

    public ToolWindowLinksActionGroup() {
        getTemplatePresentation().setText(FxToolsBundle.message("action.toolwindow.links.text"));
        getTemplatePresentation().setDescription(FxToolsBundle.message("action.toolwindow.links.description"));
        getTemplatePresentation().setIcon(AllIcons.Ide.External_link_arrow);
        setPopup(true);

        add(new OpenUrlAction("action.toolwindow.links.api", JAVADOC_BASE + "/"));
        add(new OpenUrlAction("action.toolwindow.links.fxml",
                JAVADOC_BASE + "/javafx.fxml/javafx/fxml/doc-files/introduction_to_fxml.html"));
        add(new OpenUrlAction("action.toolwindow.links.css",
                JAVADOC_BASE + "/javafx.graphics/javafx/scene/doc-files/cssref.html"));
        add(new OpenUrlAction("action.toolwindow.links.openjfx", "https://openjfx.io/"));
        add(Separator.getInstance());
        add(new OpenUrlAction("action.toolwindow.links.gluon", "https://docs.gluonhq.com/"));
        add(new OpenUrlAction("action.toolwindow.links.jfxcentral", "https://www.jfx-central.com/"));
    }

    private static final class OpenUrlAction extends AnAction implements DumbAware {
        private final String url;

        OpenUrlAction(@NotNull String bundleKey, @NotNull String url) {
            super(FxToolsBundle.message(bundleKey));
            this.url = url;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            BrowserUtil.browse(url);
        }
    }
}
