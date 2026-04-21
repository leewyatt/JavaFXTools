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
 * "Dev Debug" toolbar button: launches the selected Run Configuration in debug mode
 * with {@code -Dfxmlkit.devmode=true} injected. Visible only in projects with FxmlKit.
 */
public final class FxmlKitDevDebugExecutor extends Executor {

    private static final Icon ICON =
            IconLoader.getIcon("/icons/fxmlkitDevModeDebug.svg", FxmlKitDevDebugExecutor.class);

    @Override
    public @NotNull String getId() {
        return FxmlKitDevModeConstants.EXECUTOR_ID_DEBUG;
    }

    @Override
    public @NotNull String getToolWindowId() {
        return ToolWindowId.DEBUG;
    }

    @Override
    public @NotNull Icon getToolWindowIcon() {
        return AllIcons.Toolwindows.ToolWindowDebugger;
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
        return FxToolsBundle.message("executor.fxmlkit.devDebug.description");
    }

    @Override
    public @NotNull String getActionName() {
        return FxToolsBundle.message("executor.fxmlkit.devDebug.actionName");
    }

    @Override
    public @NotNull String getStartActionText() {
        return FxToolsBundle.message("executor.fxmlkit.devDebug.startActionText");
    }

    @Override
    public @NotNull String getContextActionId() {
        // Stable id reserved for a future context-menu action (see FxmlKitDevExecutor).
        return "FxmlKit.DevModeDebug.Context";
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
