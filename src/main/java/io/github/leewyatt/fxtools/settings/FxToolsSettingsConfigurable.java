package io.github.leewyatt.fxtools.settings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.notification.JfxLinksNotifierService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Settings page under Settings &gt; Tools &gt; JavaFX Tools.
 * Provides notification toggle, gutter preview toggles with a three-state
 * master checkbox, and a gutter icon size selector.
 */
public class FxToolsSettingsConfigurable implements Configurable {

    private static final int[] SCALE_OPTIONS = {50, 75, 100, 125, 150};

    private JBCheckBox enableNotificationCheckbox;
    private ThreeStateCheckBox gutterMasterCheckbox;
    private JBCheckBox enableColorGutterCheckbox;
    private JBCheckBox enableEffectGutterCheckbox;
    private JBCheckBox enableShapeGutterCheckbox;
    private JBCheckBox enableIkonliGutterCheckbox;
    private ComboBox<String> iconSizeCombo;

    /** Guard flag to prevent recursive updates between master and sub checkboxes. */
    private boolean updatingCheckboxes;

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

        // ==================== Gutter Previews Section ====================
        JPanel gutterSection = createSection(
                FxToolsBundle.message("settings.section.gutter"));

        JBLabel gutterTip = new JBLabel(FxToolsBundle.message("settings.gutter.tip"));
        gutterTip.setForeground(UIUtil.getContextHelpForeground());
        gutterTip.setAlignmentX(Component.LEFT_ALIGNMENT);
        gutterSection.add(gutterTip);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(6)));

        // Icon size row (top-level, same indent as master checkbox)
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        sizeRow.add(new JBLabel(FxToolsBundle.message("settings.gutter.iconSize")));

        String[] scaleLabels = new String[SCALE_OPTIONS.length];
        int selectedIndex = 2;
        for (int i = 0; i < SCALE_OPTIONS.length; i++) {
            scaleLabels[i] = SCALE_OPTIONS[i] + "%";
            if (SCALE_OPTIONS[i] == settings.gutterIconScale) {
                selectedIndex = i;
            }
        }
        iconSizeCombo = new ComboBox<>(scaleLabels);
        iconSizeCombo.setSelectedIndex(selectedIndex);
        sizeRow.add(iconSizeCombo);
        gutterSection.add(sizeRow);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(6)));

        // Master toggle
        gutterMasterCheckbox = new ThreeStateCheckBox(
                FxToolsBundle.message("settings.gutter.master"));
        gutterMasterCheckbox.setThirdStateEnabled(false);
        gutterMasterCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        gutterSection.add(gutterMasterCheckbox);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(6)));

        // Sub-toggles (indented)
        enableColorGutterCheckbox = createSubCheckBox(
                FxToolsBundle.message("settings.gutter.color"),
                settings.enableColorGutterPreviews);
        gutterSection.add(enableColorGutterCheckbox);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(4)));

        enableEffectGutterCheckbox = createSubCheckBox(
                FxToolsBundle.message("settings.gutter.effect"),
                settings.enableEffectGutterPreviews);
        gutterSection.add(enableEffectGutterCheckbox);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(4)));

        enableShapeGutterCheckbox = createSubCheckBox(
                FxToolsBundle.message("settings.gutter.shape"),
                settings.enableShapeGutterPreviews);
        gutterSection.add(enableShapeGutterCheckbox);
        gutterSection.add(Box.createVerticalStrut(JBUI.scale(4)));

        enableIkonliGutterCheckbox = createSubCheckBox(
                FxToolsBundle.message("settings.gutter.ikonli"),
                settings.enableIkonliGutterPreviews);
        gutterSection.add(enableIkonliGutterCheckbox);

        // Wire master ↔ sub synchronization
        gutterMasterCheckbox.addActionListener(e -> onMasterToggled());
        enableColorGutterCheckbox.addActionListener(e -> syncMasterFromSubs());
        enableEffectGutterCheckbox.addActionListener(e -> syncMasterFromSubs());
        enableShapeGutterCheckbox.addActionListener(e -> syncMasterFromSubs());
        enableIkonliGutterCheckbox.addActionListener(e -> syncMasterFromSubs());
        syncMasterFromSubs();

        mainPanel.add(gutterSection);

        // ==================== About Section ====================
        mainPanel.add(createAboutSection());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(mainPanel, BorderLayout.NORTH);
        return wrapper;
    }

    @Override
    public boolean isModified() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        return enableNotificationCheckbox.isSelected() != settings.enableLinksNotification
                || enableColorGutterCheckbox.isSelected() != settings.enableColorGutterPreviews
                || enableEffectGutterCheckbox.isSelected() != settings.enableEffectGutterPreviews
                || enableShapeGutterCheckbox.isSelected() != settings.enableShapeGutterPreviews
                || enableIkonliGutterCheckbox.isSelected() != settings.enableIkonliGutterPreviews
                || getSelectedScale() != settings.gutterIconScale;
    }

    @Override
    public void apply() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        boolean wasNotifEnabled = settings.enableLinksNotification;

        boolean gutterChanged =
                enableColorGutterCheckbox.isSelected() != settings.enableColorGutterPreviews
                || enableEffectGutterCheckbox.isSelected() != settings.enableEffectGutterPreviews
                || enableShapeGutterCheckbox.isSelected() != settings.enableShapeGutterPreviews
                || enableIkonliGutterCheckbox.isSelected() != settings.enableIkonliGutterPreviews
                || getSelectedScale() != settings.gutterIconScale;

        settings.enableLinksNotification = enableNotificationCheckbox.isSelected();
        settings.enableColorGutterPreviews = enableColorGutterCheckbox.isSelected();
        settings.enableEffectGutterPreviews = enableEffectGutterCheckbox.isSelected();
        settings.enableShapeGutterPreviews = enableShapeGutterCheckbox.isSelected();
        settings.enableIkonliGutterPreviews = enableIkonliGutterCheckbox.isSelected();
        settings.gutterIconScale = getSelectedScale();

        if (!wasNotifEnabled && settings.enableLinksNotification) {
            JfxLinksNotifierService.getInstance().start();
        }

        if (gutterChanged) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                DaemonCodeAnalyzer.getInstance(project).restart();
            }
        }
    }

    @Override
    public void reset() {
        FxToolsSettingsState settings = FxToolsSettingsState.getInstance();
        enableNotificationCheckbox.setSelected(settings.enableLinksNotification);
        enableColorGutterCheckbox.setSelected(settings.enableColorGutterPreviews);
        enableEffectGutterCheckbox.setSelected(settings.enableEffectGutterPreviews);
        enableShapeGutterCheckbox.setSelected(settings.enableShapeGutterPreviews);
        enableIkonliGutterCheckbox.setSelected(settings.enableIkonliGutterPreviews);
        selectScale(settings.gutterIconScale);
        syncMasterFromSubs();
    }

    // ==================== Master ↔ Sub Synchronization ====================

    /**
     * Called when the master checkbox is clicked.
     * Unchecked or indeterminate → select all; checked → deselect all.
     */
    private void onMasterToggled() {
        if (updatingCheckboxes) {
            return;
        }
        updatingCheckboxes = true;
        try {
            boolean selectAll = gutterMasterCheckbox.getState() != ThreeStateCheckBox.State.NOT_SELECTED;
            enableColorGutterCheckbox.setSelected(selectAll);
            enableEffectGutterCheckbox.setSelected(selectAll);
            enableShapeGutterCheckbox.setSelected(selectAll);
            enableIkonliGutterCheckbox.setSelected(selectAll);
            syncMasterState();
        } finally {
            updatingCheckboxes = false;
        }
    }

    /**
     * Called when any sub-checkbox changes. Updates the master to reflect
     * the aggregate state.
     */
    private void syncMasterFromSubs() {
        if (updatingCheckboxes) {
            return;
        }
        updatingCheckboxes = true;
        try {
            syncMasterState();
        } finally {
            updatingCheckboxes = false;
        }
    }

    private void syncMasterState() {
        int checked = countCheckedSubs();
        if (checked == 0) {
            gutterMasterCheckbox.setState(ThreeStateCheckBox.State.NOT_SELECTED);
        } else if (checked == 4) {
            gutterMasterCheckbox.setState(ThreeStateCheckBox.State.SELECTED);
        } else {
            gutterMasterCheckbox.setState(ThreeStateCheckBox.State.DONT_CARE);
        }
    }

    private int countCheckedSubs() {
        int count = 0;
        if (enableColorGutterCheckbox.isSelected()) {
            count++;
        }
        if (enableEffectGutterCheckbox.isSelected()) {
            count++;
        }
        if (enableShapeGutterCheckbox.isSelected()) {
            count++;
        }
        if (enableIkonliGutterCheckbox.isSelected()) {
            count++;
        }
        return count;
    }

    // ==================== Scale Helpers ====================

    private int getSelectedScale() {
        int idx = iconSizeCombo.getSelectedIndex();
        if (idx >= 0 && idx < SCALE_OPTIONS.length) {
            return SCALE_OPTIONS[idx];
        }
        return 100;
    }

    private void selectScale(int scale) {
        for (int i = 0; i < SCALE_OPTIONS.length; i++) {
            if (SCALE_OPTIONS[i] == scale) {
                iconSizeCombo.setSelectedIndex(i);
                return;
            }
        }
        iconSizeCombo.setSelectedIndex(2);
    }

    // ==================== About Section ====================

    private static final String URL_GITHUB = "https://github.com/leewyatt";
    private static final String URL_BLUESKY = "https://bsky.app/profile/leewyatt.bsky.social";
    private static final String URL_YOUTUBE = "https://www.youtube.com/@leewyatt2298";
    private static final String URL_BILIBILI = "https://space.bilibili.com/397562730";

    private JPanel createAboutSection() {
        JPanel section = createSection(FxToolsBundle.message("settings.section.about"));

        // ---- Header: plugin icon + name + version ----
        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        Icon pluginIcon = IconLoader.getIcon("/META-INF/pluginIcon.svg", getClass());
        JBLabel iconLabel = new JBLabel(pluginIcon);
        headerRow.add(iconLabel);

        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);

        JBLabel nameLabel = new JBLabel("JavaFX Tools");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, (float) JBUI.scale(14)));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        namePanel.add(nameLabel);

        JBLabel versionLabel = new JBLabel(FxToolsBundle.message("settings.about.version") + " · " + FxToolsBundle.message("settings.about.author"));
        versionLabel.setForeground(UIUtil.getContextHelpForeground());
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        namePanel.add(versionLabel);

        headerRow.add(namePanel);
        section.add(headerRow);
        section.add(Box.createVerticalStrut(JBUI.scale(12)));

        // ---- Connect links (single row) ----
        JPanel linksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linksRow.setOpaque(false);
        linksRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[][] links = {
                {FxToolsBundle.message("settings.about.github"), URL_GITHUB},
                {FxToolsBundle.message("settings.about.bluesky"), URL_BLUESKY},
                {FxToolsBundle.message("settings.about.youtube"), URL_YOUTUBE},
                {FxToolsBundle.message("settings.about.bilibili"), URL_BILIBILI},
                {FxToolsBundle.message("settings.about.email"), "mailto:leewyatt7788@gmail.com"},
        };
        for (int i = 0; i < links.length; i++) {
            if (i > 0) {
                JBLabel sep = new JBLabel(" · ");
                sep.setForeground(UIUtil.getContextHelpForeground());
                linksRow.add(sep);
            }
            linksRow.add(createHyperlinkLabel(links[i][0], links[i][1]));
        }

        section.add(linksRow);
        section.add(Box.createVerticalStrut(JBUI.scale(12)));

        // ---- Available for JavaFX work ----
        JPanel availPanel = new JPanel();
        availPanel.setLayout(new BoxLayout(availPanel, BoxLayout.Y_AXIS));
        availPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        availPanel.setOpaque(true);
        availPanel.setBackground(new JBColor(new Color(0xEBF2FC), new Color(0x2D3548)));
        availPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new JBColor(new Color(0xC4D7F2), new Color(0x3D4A66)), 1, true),
                JBUI.Borders.empty(10, 12)));

        JBLabel availTitle = new JBLabel(
                FxToolsBundle.message("settings.about.available.title"));
        availTitle.setFont(availTitle.getFont().deriveFont(Font.BOLD));
        availTitle.setForeground(new JBColor(new Color(0x2160C4), new Color(0x6BA3F5)));
        availTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        availPanel.add(availTitle);
        availPanel.add(Box.createVerticalStrut(JBUI.scale(4)));

        JBLabel availDesc = new JBLabel(
                FxToolsBundle.message("settings.about.available.desc"));
        availDesc.setForeground(new JBColor(new Color(0x4A6A9A), new Color(0x9BB3D4)));
        availDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        availPanel.add(availDesc);

        section.add(availPanel);
        section.add(Box.createVerticalStrut(JBUI.scale(12)));

        return section;
    }

    private JBLabel createHyperlinkLabel(String text, String url) {
        JBLabel label = new JBLabel("<html><a href=''>" + text + "</a></html>");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtil.browse(url);
            }
        });
        return label;
    }

    // ==================== UI Helpers ====================

    private JBCheckBox createSubCheckBox(String text, boolean selected) {
        JBCheckBox cb = new JBCheckBox(text);
        cb.setSelected(selected);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setBorder(JBUI.Borders.emptyLeft(24));
        return cb;
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
