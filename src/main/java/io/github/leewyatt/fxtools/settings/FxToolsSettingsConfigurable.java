package io.github.leewyatt.fxtools.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.notification.JfxLinksNotifierService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Settings page under Settings &gt; Tools &gt; JavaFX Tools.
 * Provides notification toggle and feature toggles.
 */
public class FxToolsSettingsConfigurable implements Configurable {

    private JBCheckBox enableNotificationCheckbox;
    private JBCheckBox enableGutterPreviewsCheckbox;
    private JBCheckBox enableIkonliCompletionCheckbox;

    @Override
    public String getDisplayName() {
        return FxToolsBundle.message("settings.displayName");
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();

        // ==================== Notifications Section ====================
        JPanel notifSection = createSection(
                FxToolsBundle.message("settings.section.notifications"));

        enableNotificationCheckbox = new JBCheckBox(
                FxToolsBundle.message("settings.notification.enable"));
        enableNotificationCheckbox.setSelected(settings.enableLinksNotification);
        enableNotificationCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        notifSection.add(enableNotificationCheckbox);
        notifSection.add(createTipLabel(
                FxToolsBundle.message("settings.notification.tip")));
        mainPanel.add(notifSection);

        // ==================== Features Section ====================
        JPanel featuresSection = createSection(
                FxToolsBundle.message("settings.section.features"));

        enableGutterPreviewsCheckbox = new JBCheckBox(
                FxToolsBundle.message("settings.feature.gutter"));
        enableGutterPreviewsCheckbox.setSelected(settings.enableGutterPreviews);
        enableGutterPreviewsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        featuresSection.add(enableGutterPreviewsCheckbox);
        featuresSection.add(createTipLabel(
                FxToolsBundle.message("settings.feature.gutter.tip")));
        featuresSection.add(Box.createVerticalStrut(JBUI.scale(8)));

        enableIkonliCompletionCheckbox = new JBCheckBox(
                FxToolsBundle.message("settings.feature.ikonli"));
        enableIkonliCompletionCheckbox.setSelected(settings.enableIkonliCompletion);
        enableIkonliCompletionCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        featuresSection.add(enableIkonliCompletionCheckbox);
        featuresSection.add(createTipLabel(
                FxToolsBundle.message("settings.feature.ikonli.tip")));

        mainPanel.add(featuresSection);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(mainPanel, BorderLayout.NORTH);
        return wrapper;
    }

    @Override
    public boolean isModified() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        return enableNotificationCheckbox.isSelected() != settings.enableLinksNotification
                || enableGutterPreviewsCheckbox.isSelected() != settings.enableGutterPreviews
                || enableIkonliCompletionCheckbox.isSelected() != settings.enableIkonliCompletion;
    }

    @Override
    public void apply() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        boolean wasNotifEnabled = settings.enableLinksNotification;

        settings.enableLinksNotification = enableNotificationCheckbox.isSelected();
        settings.enableGutterPreviews = enableGutterPreviewsCheckbox.isSelected();
        settings.enableIkonliCompletion = enableIkonliCompletionCheckbox.isSelected();

        if (!wasNotifEnabled && settings.enableLinksNotification) {
            JfxLinksNotifierService.getInstance().start();
        }
    }

    @Override
    public void reset() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        enableNotificationCheckbox.setSelected(settings.enableLinksNotification);
        enableGutterPreviewsCheckbox.setSelected(settings.enableGutterPreviews);
        enableIkonliCompletionCheckbox.setSelected(settings.enableIkonliCompletion);
    }

    private JPanel createSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setOpaque(false);
        section.setBorder(IdeBorderFactory.createTitledBorder(title));
        return section;
    }

    private JBLabel createTipLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setBorder(JBUI.Borders.emptyLeft(24));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
