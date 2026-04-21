package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.text.VersionComparatorUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitInstalledVersionReader;
import org.jetbrains.annotations.NotNull;

/**
 * Checks the installed FxmlKit version before launching in dev mode. Versions below
 * {@link FxmlKitDevModeConstants#MIN_SUPPORTED_VERSION} do not recognize the
 * {@code -Dfxmlkit.devmode=true} property, so hot-reload silently fails to activate.
 *
 * <p>A modal dialog warns the user. Unlike a balloon notification (which IDEA folds
 * into the event log after a few seconds and is easily missed), the dialog blocks the
 * launch until the user makes an explicit choice. A per-(project, version) "don't
 * show again" checkbox avoids nagging once the user has seen the warning.
 */
final class FxmlKitDevVersionChecker {

    /** Stores the version string the user opted to silence the dialog for. */
    private static final Key<String> SKIP_WARNING_VERSION =
            Key.create("fxmlkit.devmode.skipWarningVersion");

    private FxmlKitDevVersionChecker() {
    }

    /**
     * Reads the installed FxmlKit version and, if below the supported threshold,
     * presents a warning dialog. Returns {@code true} to proceed with launch,
     * {@code false} if the user cancelled.
     */
    static boolean check(@NotNull Project project, @NotNull Module module) {
        String installed = FxmlKitInstalledVersionReader.read(module);
        if (installed == null) {
            return true;
        }
        if (VersionComparatorUtil.compare(installed,
                FxmlKitDevModeConstants.MIN_SUPPORTED_VERSION) >= 0) {
            return true;
        }
        if (installed.equals(project.getUserData(SKIP_WARNING_VERSION))) {
            return true;
        }
        return showWarningDialog(project, installed);
    }

    private static boolean showWarningDialog(@NotNull Project project,
                                             @NotNull String installed) {
        String title = FxToolsBundle.message("dialog.fxmlkit.devmode.versionTooLow.title");
        String message = FxToolsBundle.message(
                "dialog.fxmlkit.devmode.versionTooLow.message",
                installed, FxmlKitDevModeConstants.MIN_SUPPORTED_VERSION);
        String launchButton = FxToolsBundle.message("dialog.fxmlkit.devmode.button.launchAnyway");
        String cancelButton = FxToolsBundle.message("dialog.fxmlkit.devmode.button.cancel");

        DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption.Adapter() {
            @Override
            public void rememberChoice(boolean isSelected, int exitCode) {
                if (isSelected && exitCode == 0) {
                    project.putUserData(SKIP_WARNING_VERSION, installed);
                }
            }

            @Override
            public @NotNull String getDoNotShowMessage() {
                return FxToolsBundle.message("dialog.fxmlkit.devmode.doNotShow", installed);
            }
        };

        int result = Messages.showDialog(
                project,
                message,
                title,
                new String[]{launchButton, cancelButton},
                0,
                Messages.getWarningIcon(),
                option);

        return result == 0;
    }
}
