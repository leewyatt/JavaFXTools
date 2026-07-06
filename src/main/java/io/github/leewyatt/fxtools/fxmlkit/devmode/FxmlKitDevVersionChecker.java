package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitInstalledVersionReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import java.awt.BorderLayout;

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

        DoNotAskOption option = new DoNotAskOption.Adapter() {
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

        return new VersionWarningDialog(project, title, message, launchButton, cancelButton, option)
                .showAndGet();
    }

    /**
     * Modal warning dialog built on {@link DialogWrapper} directly, avoiding the
     * removed dialog helpers. Presents the multi-line message with a warning icon and wires the
     * "don't show again" checkbox through {@link #setDoNotAskOption}.
     */
    private static final class VersionWarningDialog extends DialogWrapper {

        private final String message;

        // FQN is required here: inside a DialogWrapper subclass the simple name DoNotAskOption
        // resolves to the inherited, deprecated DialogWrapper.DoNotAskOption, which shadows the
        // imported top-level com.intellij.openapi.ui.DoNotAskOption.
        VersionWarningDialog(@NotNull Project project, @NotNull String title, @NotNull String message,
                             @NotNull String okText, @NotNull String cancelText,
                             @NotNull com.intellij.openapi.ui.DoNotAskOption option) {
            super(project, true);
            this.message = message;
            setTitle(title);
            setOKButtonText(okText);
            setCancelButtonText(cancelText);
            setDoNotAskOption(option);
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(JBUI.scale(12), 0));

            JBLabel iconLabel = new JBLabel(Messages.getWarningIcon());
            iconLabel.setVerticalAlignment(JBLabel.TOP);
            panel.add(iconLabel, BorderLayout.WEST);

            JTextPane pane = Messages.configureMessagePaneUi(new JTextPane(), message);
            panel.add(pane, BorderLayout.CENTER);

            return panel;
        }
    }
}
