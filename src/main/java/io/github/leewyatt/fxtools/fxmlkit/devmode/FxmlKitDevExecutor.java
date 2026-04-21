package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Adds a "Dev" toolbar button that launches the selected Run Configuration with
 * {@code -Dfxmlkit.devmode=true} injected. Visible only in projects that have FxmlKit
 * on any module's classpath.
 */
public final class FxmlKitDevExecutor extends Executor {

    private static final Icon ICON =
            IconLoader.getIcon("/icons/fxmlkitDevMode.svg", FxmlKitDevExecutor.class);

    @Override
    public @NotNull String getId() {
        return FxmlKitDevModeConstants.EXECUTOR_ID_RUN;
    }

    @Override
    public @NotNull String getToolWindowId() {
        return ToolWindowId.RUN;
    }

    @Override
    public @NotNull Icon getToolWindowIcon() {
        return AllIcons.Toolwindows.ToolWindowRun;
    }

    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    @Override
    public Icon getDisabledIcon() {
        return null;
    }

    @Override
    public String getDescription() {
        return FxToolsBundle.message("executor.fxmlkit.dev.description");
    }

    @Override
    public @NotNull String getActionName() {
        return FxToolsBundle.message("executor.fxmlkit.dev.actionName");
    }

    @Override
    public @NotNull String getStartActionText() {
        return FxToolsBundle.message("executor.fxmlkit.dev.startActionText");
    }

    @Override
    public @NotNull String getContextActionId() {
        // Stable id reserved for a future context-menu action (e.g. gutter "Run as
        // Dev Mode"). No <action> registration today — the id is a hook, not a gap.
        return "FxmlKit.DevMode.Context";
    }

    @Override
    public @Nullable String getHelpId() {
        return null;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
        return FxmlKitDetector.isFxmlKitProject(project);
    }
}
