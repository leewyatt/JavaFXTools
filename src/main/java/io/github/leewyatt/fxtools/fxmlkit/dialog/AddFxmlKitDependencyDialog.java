package io.github.leewyatt.fxtools.fxmlkit.dialog;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.dependency.DependencyInsertionContext;
import io.github.leewyatt.fxtools.fxmlkit.dependency.FxmlKitModuleConstants;
import io.github.leewyatt.fxtools.fxmlkit.dependency.GradleDependencyInserter;
import io.github.leewyatt.fxtools.fxmlkit.dependency.MavenDependencyInserter;
import io.github.leewyatt.fxtools.fxmlkit.dependency.ModuleInfoUpdater;
import io.github.leewyatt.fxtools.fxmlkit.dependency.ParentPomInfo;
import io.github.leewyatt.fxtools.fxmlkit.dependency.SnippetFormatter;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.BuildSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

/**
 * Shown after a successful FxmlKit view creation when the project does not
 * depend on FxmlKit yet. Provides two paths: Copy (always available) and
 * Add to project (enabled when a build file can be edited).
 */
public class AddFxmlKitDependencyDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(AddFxmlKitDependencyDialog.class);

    private static final String MAVEN_CENTRAL_URL =
            "https://central.sonatype.com/artifact/"
                    + FxmlKitModuleConstants.GROUP_ID + "/" + FxmlKitModuleConstants.ARTIFACT_ID;

    private final Project project;
    private final DependencyInsertionContext ctx;
    private final String depSnippet;
    private final String moduleInfoSnippet;

    private @Nullable JBCheckBox parentPomCheckBox;

    /**
     * Creates the dialog with full context-aware detection.
     */
    public AddFxmlKitDependencyDialog(@Nullable Project project,
                                      @NotNull DependencyInsertionContext ctx) {
        super(project);
        this.project = project;
        this.ctx = ctx;
        this.depSnippet = SnippetFormatter.formatDependencySnippet(ctx);
        this.moduleInfoSnippet = SnippetFormatter.formatModuleInfoSnippet(ctx);
        setTitle(FxToolsBundle.message("fxmlkit.dep.dialog.title"));
        init();
    }

    // ==================== Actions ====================

    @Override
    protected Action @NotNull [] createActions() {
        Action addAction = new AbstractAction(
                FxToolsBundle.message("fxmlkit.dep.dialog.add")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                doAddToProject();
            }
        };
        addAction.setEnabled(ctx.isAddToProjectEnabled());
        addAction.putValue(DEFAULT_ACTION, Boolean.TRUE);

        Action cancelAction = new AbstractAction(
                FxToolsBundle.message("fxmlkit.dep.dialog.close")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                close(CANCEL_EXIT_CODE);
            }
        };

        return new Action[]{cancelAction, addAction};
    }

    // ==================== Panel ====================

    @Override
    protected @NotNull JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        panel.setPreferredSize(new Dimension(JBUI.scale(560), JBUI.scale(420)));

        // ==================== Subtitle ====================
        JBLabel desc = new JBLabel("<html>" + buildDescriptionText() + "</html>");
        JPanel descWrapper = new JPanel(new BorderLayout());
        descWrapper.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(JBUIScale.scale(8))));
        descWrapper.setOpaque(false);
        descWrapper.add(desc, BorderLayout.CENTER);
        panel.add(descWrapper, BorderLayout.NORTH);

        // ==================== Tabbed pane ====================
        JPanel centerPanel = new JPanel(new BorderLayout());

        JBTabbedPane tabbedPane = new JBTabbedPane();
        tabbedPane.addTab(
                FxToolsBundle.message("fxmlkit.dep.dialog.tab.dependencies"),
                createCodePanel(depSnippet));

        if (!moduleInfoSnippet.isEmpty()) {
            tabbedPane.addTab(
                    FxToolsBundle.message("fxmlkit.dep.dialog.tab.moduleinfo"),
                    createCodePanel(moduleInfoSnippet));
        }
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        // Case B: parent POM checkbox
        if (shouldShowParentPomCheckbox()) {
            ParentPomInfo parentPom = ctx.getParentPom();
            String displayLabel = parentPom.getDisplayLabel();
            parentPomCheckBox = new JBCheckBox(
                    FxToolsBundle.message("fxmlkit.dep.dialog.parentpom.also", displayLabel),
                    false);
            if (parentPom.isExternal()) {
                parentPomCheckBox.setEnabled(false);
                parentPomCheckBox.setToolTipText(
                        FxToolsBundle.message("fxmlkit.dep.dialog.parentpom.external",
                                displayLabel));
            }
            JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, JBUI.scale(4)));
            checkboxPanel.add(parentPomCheckBox);
            centerPanel.add(checkboxPanel, BorderLayout.SOUTH);
        }

        panel.add(centerPanel, BorderLayout.CENTER);

        // ==================== Footer ====================
        panel.add(createFooter(), BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Creates a code display panel with editor-themed background and a Copy button
     * overlaid inside the text area at the top-right corner.
     */
    @NotNull
    private JPanel createCodePanel(@NotNull String snippet) {
        // Leave top padding for the Copy button so text doesn't hide behind it
        JTextArea textArea = new JTextArea(snippet);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12)));
        textArea.setBorder(JBUI.Borders.empty(10, 10, 8, 10));

        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        Color bg = scheme.getDefaultBackground();
        Color fg = scheme.getDefaultForeground();
        textArea.setBackground(bg);
        textArea.setForeground(fg);
        textArea.setCaretColor(fg);

        JBScrollPane scroll = new JBScrollPane(textArea);
        scroll.setBorder(JBUI.Borders.empty());

        // Copy button — transparent, floats inside the text area
        JButton copy = new JButton(FxToolsBundle.message("fxmlkit.dep.dialog.copy"),
                AllIcons.Actions.Copy);
        copy.addActionListener(e ->
                CopyPasteManager.getInstance().setContents(new StringSelection(snippet))
        );

        // Use JLayeredPane to overlay the Copy button on top of the scroll pane
        javax.swing.JLayeredPane layered = new javax.swing.JLayeredPane();
        layered.setLayout(new java.awt.LayoutManager() {
            @Override
            public void addLayoutComponent(String name, java.awt.Component comp) {
            }

            @Override
            public void removeLayoutComponent(java.awt.Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(java.awt.Container parent) {
                return scroll.getPreferredSize();
            }

            @Override
            public Dimension minimumLayoutSize(java.awt.Container parent) {
                return scroll.getMinimumSize();
            }

            @Override
            public void layoutContainer(java.awt.Container parent) {
                int w = parent.getWidth();
                int h = parent.getHeight();
                scroll.setBounds(0, 0, w, h);
                Dimension btnSize = copy.getPreferredSize();
                int btnX = w - btnSize.width - JBUI.scale(10);
                int btnY = JBUI.scale(10);
                copy.setBounds(btnX, btnY, btnSize.width, btnSize.height);
            }
        });
        layered.add(scroll, javax.swing.JLayeredPane.DEFAULT_LAYER);
        layered.add(copy, javax.swing.JLayeredPane.PALETTE_LAYER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(layered, BorderLayout.CENTER);
        return wrapper;
    }

    @NotNull
    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(JBUI.Borders.emptyTop(4));

        // Left: version + Maven Central link
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        JBLabel versionLabel = new JBLabel(
                FxmlKitModuleConstants.ARTIFACT_ID + " v" + ctx.getFxmlKitVersion());
        versionLabel.setForeground(com.intellij.util.ui.UIUtil.getLabelDisabledForeground());
        leftPanel.add(versionLabel);

        HyperlinkLabel link = new HyperlinkLabel(
                FxToolsBundle.message("fxmlkit.dep.dialog.mavencentral"));
        link.setHyperlinkTarget(MAVEN_CENTRAL_URL);
        leftPanel.add(link);
        footer.add(leftPanel, BorderLayout.WEST);

        // Disabled tooltip hint
        if (!ctx.isAddToProjectEnabled()) {
            String tooltip = getDisabledTooltip();
            if (tooltip != null) {
                JBLabel hint = new JBLabel("<html><small>" + tooltip + "</small></html>");
                hint.setForeground(com.intellij.util.ui.UIUtil.getLabelDisabledForeground());
                hint.setBorder(JBUI.Borders.emptyTop(4));
                JPanel footerWrap = new JPanel(new BorderLayout());
                footerWrap.add(footer, BorderLayout.NORTH);
                footerWrap.add(hint, BorderLayout.SOUTH);
                return footerWrap;
            }
        }

        return footer;
    }

    // ==================== Add to project ====================

    private void doAddToProject() {
        if (project == null || ctx.getModule() == null) {
            return;
        }

        boolean updateParent = parentPomCheckBox != null && parentPomCheckBox.isSelected();

        try {
            WriteCommandAction.runWriteCommandAction(project,
                    FxToolsBundle.message("fxmlkit.dep.dialog.title"), null, () -> {
                        if (ctx.getBuildSystem() == BuildSystem.MAVEN) {
                            MavenDependencyInserter.insert(ctx, updateParent);
                        } else if (ctx.getBuildSystem() == BuildSystem.GRADLE) {
                            GradleDependencyInserter.insert(ctx);
                        }

                        if (ctx.hasModuleInfo()) {
                            ModuleInfoUpdater.update(ctx);
                        }
                    });

            // Flush all documents to disk so the build system detects the changes
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments();

            triggerBuildSystemRefresh();

            String moduleName = ctx.getModule().getName();
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JavaFX Tools")
                    .createNotification(
                            FxToolsBundle.message("fxmlkit.dep.dialog.add.success", moduleName),
                            NotificationType.INFORMATION)
                    .notify(project);

            close(OK_EXIT_CODE);
        } catch (ProcessCanceledException pce) {
            throw pce;
        } catch (Exception ex) {
            LOG.warn("Failed to add FxmlKit dependency", ex);
            com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    FxToolsBundle.message("fxmlkit.dep.dialog.add.failure", ex.getMessage()),
                    FxToolsBundle.message("fxmlkit.dep.dialog.title"));
        }
    }

    // ==================== Helpers ====================

    @NotNull
    private String buildDescriptionText() {
        boolean missingControls = !ctx.hasControls();
        boolean missingFxml = !ctx.hasFxml();

        if (missingControls && missingFxml) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.desc.both");
        }
        if (missingControls) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.desc.controls");
        }
        if (missingFxml) {
            if (ctx.hasJavaFxGradlePlugin()) {
                return FxToolsBundle.message("fxmlkit.dep.dialog.desc.fxml.plugin");
            }
            return FxToolsBundle.message("fxmlkit.dep.dialog.desc.fxml");
        }
        return FxToolsBundle.message("fxmlkit.dep.dialog.desc");
    }

    private boolean shouldShowParentPomCheckbox() {
        if (ctx.getBuildSystem() != BuildSystem.MAVEN) {
            return false;
        }
        ParentPomInfo parent = ctx.getParentPom();
        return parent != null
                && parent.hasDependencyManagement()
                && !parent.managesFxmlKit();
    }

    @Nullable
    private String getDisabledTooltip() {
        if (ctx.getModule() == null) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.add.disabled.nomodule");
        }
        if (ctx.getBuildSystem() == BuildSystem.NONE) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.add.disabled.nobuildsystem");
        }
        if (ctx.isJavafxVersionRequiredButMissing()) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.add.disabled.noversion");
        }
        if (ctx.isJavaFxPluginModulesEditRequired()) {
            return FxToolsBundle.message("fxmlkit.dep.dialog.add.disabled.plugin.complex");
        }
        return null;
    }

    /**
     * Triggers a build-system project refresh after build files have been modified.
     * Uses the platform-level {@code ExternalSystemProjectTracker} which works for
     * both Maven and Gradle without requiring plugin-specific dependencies.
     * Scheduled via {@code invokeLater} to let the VFS and document manager settle first.
     */
    private void triggerBuildSystemRefresh() {
        if (project == null || project.isDisposed()) {
            return;
        }
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            try {
                com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
                        .getInstance(project).scheduleProjectRefresh();
            } catch (Exception e) {
                LOG.info("Build system refresh not available, skipping", e);
            }
        });
    }

}
