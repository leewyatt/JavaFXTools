package io.github.leewyatt.fxtools.fxmlkit.dialog;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.psi.PsiDirectory;
import io.github.leewyatt.fxtools.FxToolsBundle;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.SourceVersion;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Dialog for creating a new FxmlKit View with associated files.
 */
public class NewFxmlKitViewDialog extends DialogWrapper {

    private static final JBColor VIEW_COLOR =
            new JBColor(new Color(0x3574F0), new Color(0x5A95F5));
    private static final JBColor FXML_COLOR =
            new JBColor(new Color(0xCC7832), new Color(0xCC7832));
    private static final JBColor CSS_COLOR =
            new JBColor(new Color(0x9876AA), new Color(0xB07BDB));
    private static final JBColor PROPERTIES_COLOR =
            new JBColor(new Color(0x1D9E75), new Color(0x2EAF86));

    // Summary bar colors — configured (green)
    private static final JBColor SUMMARY_GREEN_BG =
            new JBColor(new Color(0xE1F5EE), new Color(0x1E3A2F));
    private static final JBColor SUMMARY_GREEN_BORDER =
            new JBColor(new Color(0x1D9E75), new Color(0x2EAF86));
    private static final JBColor SUMMARY_GREEN_FG =
            new JBColor(new Color(0x0F6E56), new Color(0x5CC9A7));
    // Summary bar colors — not configured (yellow)
    private static final JBColor SUMMARY_YELLOW_BG =
            new JBColor(new Color(0xFAEEDA), new Color(0x3D3226));
    private static final JBColor SUMMARY_YELLOW_BORDER =
            new JBColor(new Color(0xBA7517), new Color(0xD4943F));
    private static final JBColor SUMMARY_YELLOW_FG =
            new JBColor(new Color(0x854F0B), new Color(0xD4943F));

    private final Project project;
    private final JBTextField nameField = new JBTextField();
    private final SegmentedControl segmentedControl;
    private final FileTypeCard viewCard;
    private final FileTypeCard fxmlCard;
    private final FileTypeCard controllerCard;
    private final FileTypeCard cssCard;
    private final FileTypeCard propertiesCard;
    private final FileTreePreview treePreview;
    private final I18nSummaryBar summaryBar;
    private I18nConfig i18nConfig;
    private final String javaRelativePath;
    private final String resourceRelativePath;
    private final PsiDirectory javaDir;
    private final PsiDirectory resourceDir;

    /**
     * Creates a new dialog for FxmlKit View creation.
     *
     * @param project              the current project
     * @param packageName          the target package name
     * @param javaRelativePath     relative path of the Java source directory
     * @param resourceRelativePath relative path of the resources directory
     * @param javaDir              the Java source directory for file existence checking
     * @param resourceDir          the resource directory for file existence checking, may be null
     */
    public NewFxmlKitViewDialog(@NotNull Project project,
                                @NotNull String packageName,
                                @NotNull String javaRelativePath,
                                @NotNull String resourceRelativePath,
                                @NotNull PsiDirectory javaDir,
                                @Nullable PsiDirectory resourceDir) {
        super(project, true);
        this.project = project;
        this.javaRelativePath = javaRelativePath;
        this.resourceRelativePath = resourceRelativePath;
        this.javaDir = javaDir;
        this.resourceDir = resourceDir;

        segmentedControl = new SegmentedControl(
                FxToolsBundle.message("dialog.new.fxmlkit.view.type.view"),
                FxToolsBundle.message("dialog.new.fxmlkit.view.type.provider")
        );
        viewCard = new FileTypeCard("View", VIEW_COLOR, FileIconType.CONTROLLER, true);
        fxmlCard = new FileTypeCard(
                FxToolsBundle.message("dialog.new.fxmlkit.view.create.fxml"),
                FXML_COLOR, FileIconType.FXML, true);
        controllerCard = new FileTypeCard(
                FxToolsBundle.message("dialog.new.fxmlkit.view.create.controller"),
                VIEW_COLOR, FileIconType.CONTROLLER, false);
        cssCard = new FileTypeCard(
                FxToolsBundle.message("dialog.new.fxmlkit.view.create.css"),
                CSS_COLOR, FileIconType.CSS, false);
        propertiesCard = new FileTypeCard(
                FxToolsBundle.message("dialog.new.fxmlkit.view.create.properties"),
                PROPERTIES_COLOR, FileIconType.I18N, false);
        propertiesCard.setSelected(false);

        treePreview = new FileTreePreview();
        summaryBar = new I18nSummaryBar();

        setTitle(FxToolsBundle.message("dialog.new.fxmlkit.view.title"));
        init();
        initValidation();

        nameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updatePreview();
            }
        });

        segmentedControl.setOnSelectionChanged(() -> {
            boolean isProvider = segmentedControl.getSelectedIndex() == 1;
            viewCard.setLabel(isProvider ? "Provider" : "View");
            updatePreview();
        });
        controllerCard.setOnSelectionChanged(this::updatePreview);
        cssCard.setOnSelectionChanged(this::updatePreview);
        propertiesCard.setOnSelectionChanged(() -> {
            if (propertiesCard.isCardSelected()) {
                if (i18nConfig == null) {
                    // First time: must complete config, cancel reverts to unchecked
                    openI18nDialogOrRevert();
                } else {
                    // Re-checked with existing config, just show summary
                    summaryBar.updateState(true, i18nConfig);
                    summaryBar.setVisible(true);
                    updatePreview();
                }
            } else {
                summaryBar.setVisible(false);
                updatePreview();
            }
        });
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        int gap = JBUI.scale(8);
        JPanel panel = new JPanel(new MigLayout(
                "wrap 1, fillx, insets " + JBUI.scale(10) + ", gap 0",
                "[grow, fill]"
        ));
        panel.setMinimumSize(new Dimension(JBUI.scale(525), JBUI.scale(500)));

        // Area 1: Name label + hint on same line, above text field
        JPanel nameLabelPanel = new JPanel(new MigLayout("insets 0, gap 0", "[]push[]"));
        nameLabelPanel.setOpaque(false);
        JBLabel nameLabel = new JBLabel(FxToolsBundle.message("dialog.new.fxmlkit.view.name"));
        nameLabelPanel.add(nameLabel);
        JBLabel hintLabel = new JBLabel(
                FxToolsBundle.message("dialog.new.fxmlkit.view.placeholder"));
        hintLabel.setForeground(UIUtil.getContextHelpForeground());
        hintLabel.setFont(getMonoFont(JBUI.scale(11)));
        nameLabelPanel.add(hintLabel);
        panel.add(nameLabelPanel, "gapbottom " + JBUI.scale(2));

        nameField.setFont(getMonoFont(JBUI.scale(13)));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(JBUI.scale(2), getBorderColor()),
                JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(1))
        ));
        nameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                nameField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedBorder(JBUI.scale(2), getFocusColor()),
                        JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(1))
                ));
            }

            @Override
            public void focusLost(FocusEvent e) {
                nameField.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedBorder(JBUI.scale(2), getBorderColor()),
                        JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(1))
                ));
            }
        });
        panel.add(nameField, "growx");

        // Area 2: Segmented control
        panel.add(segmentedControl, "growx, gaptop " + gap + ", gapbottom " + gap);

        // Area 3: File type cards (View, FXML mandatory; Controller, CSS optional)
        JPanel cardsPanel = new JPanel(new MigLayout(
                "fillx, insets 0, gap " + JBUI.scale(6),
                "[grow, fill][grow, fill][grow, fill][grow, fill][grow, fill]"
        ));
        cardsPanel.setOpaque(false);
        cardsPanel.add(viewCard);
        cardsPanel.add(fxmlCard);
        cardsPanel.add(controllerCard);
        cardsPanel.add(cssCard);
        cardsPanel.add(propertiesCard);
        panel.add(cardsPanel, "growx, gapbottom " + gap);

        // i18n summary bar (below cards, initially hidden)
        summaryBar.setVisible(false);
        panel.add(summaryBar, "growx, gapbottom " + gap + ", hidemode 3");

        // Area 4: File tree preview (wrapped in scroll pane, fills remaining space)
        JBScrollPane scrollPane =
                new JBScrollPane(treePreview);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, "growx, pushy, growy");

        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameField;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            return new ValidationInfo(
                    FxToolsBundle.message("dialog.new.fxmlkit.view.error.empty.name"), nameField);
        }
        if (!SourceVersion.isIdentifier(name)) {
            return new ValidationInfo(
                    FxToolsBundle.message("dialog.new.fxmlkit.view.error.invalid.name"), nameField);
        }
        String existsMsg = findExistingFiles(name);
        if (existsMsg != null) {
            return new ValidationInfo(existsMsg, nameField);
        }
        return null;
    }

    private @Nullable String findExistingFiles(String name) {
        boolean isProvider = segmentedControl.getSelectedIndex() == 1;
        String viewClassName = computeViewClassName(name, isProvider);
        String baseName = computeBaseName(viewClassName);

        java.util.List<String> conflicts = new java.util.ArrayList<>();

        if (javaDir.findFile(viewClassName + ".java") != null) {
            conflicts.add(viewClassName + ".java");
        }
        if (controllerCard.isCardSelected()
                && javaDir.findFile(baseName + "Controller.java") != null) {
            conflicts.add(baseName + "Controller.java");
        }
        if (resourceDir != null) {
            if (resourceDir.findFile(viewClassName + ".fxml") != null) {
                conflicts.add(viewClassName + ".fxml");
            }
            if (cssCard.isCardSelected()
                    && resourceDir.findFile(viewClassName + ".css") != null) {
                conflicts.add(viewClassName + ".css");
            }
        }

        if (conflicts.isEmpty()) {
            return null;
        }
        return FxToolsBundle.message("dialog.new.fxmlkit.view.error.file.exists",
                String.join(", ", conflicts));
    }

    private void updatePreview() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            treePreview.clearEntries();
            treePreview.repaint();
            return;
        }

        boolean isProvider = segmentedControl.getSelectedIndex() == 1;
        String viewClassName = computeViewClassName(name, isProvider);
        String baseName = computeBaseName(viewClassName);
        String controllerName = baseName + "Controller";

        treePreview.clearEntries();

        // Split paths: fixed prefix + package suffix
        String[] javaParts = splitPath(javaRelativePath);
        String[] resParts = splitPath(resourceRelativePath);

        String viewTag = isProvider ? "[provider]" : "[view]";
        treePreview.addDirectory(javaParts[0]);
        if (!javaParts[1].isEmpty()) {
            treePreview.addSubDirectory(javaParts[1]);
        }
        treePreview.addFile(viewClassName + ".java", VIEW_COLOR, viewTag, VIEW_COLOR);
        if (controllerCard.isCardSelected()) {
            treePreview.addFile(controllerName + ".java", VIEW_COLOR, "[ctrl]", VIEW_COLOR);
        }

        treePreview.addDirectory(resParts[0]);
        if (!resParts[1].isEmpty()) {
            treePreview.addSubDirectory(resParts[1]);
        }
        treePreview.addFile(viewClassName + ".fxml", FXML_COLOR, "[layout]", FXML_COLOR);
        if (cssCard.isCardSelected()) {
            treePreview.addFile(viewClassName + ".css", CSS_COLOR, "[style]", CSS_COLOR);
        }
        if (i18nConfig != null && i18nConfig.getMode() == I18nConfig.Mode.CREATE_NEW) {
            String bundleName = i18nConfig.getBundleName();
            int variantCount = i18nConfig.getSelectedLocales().size();
            String propTag = variantCount > 0
                    ? "(+" + variantCount + " locale variants)" : "[default]";

            // Check if properties path differs from FXML/CSS path
            String bundlePath = i18nConfig.getBundlePath();
            boolean sameAsResPath = bundlePath.isEmpty()
                    ? resParts[1].isEmpty()
                    : resParts[1].equals(bundlePath)
                            || (resParts[0] + resParts[1]).equals(
                                    bundlePath.endsWith("/") ? bundlePath : bundlePath + "/");

            if (sameAsResPath) {
                // Same directory as FXML/CSS — add in current section
                treePreview.addFile(bundleName + ".properties",
                        PROPERTIES_COLOR, propTag, PROPERTIES_COLOR);
            } else {
                // Different directory — add a separate section
                String propDir = bundlePath.isEmpty() ? resParts[0] : bundlePath;
                if (!propDir.endsWith("/")) {
                    propDir += "/";
                }
                String[] propParts = splitPath(propDir);
                treePreview.addDirectory(propParts[0]);
                if (!propParts[1].isEmpty()) {
                    treePreview.addSubDirectory(propParts[1]);
                }
                treePreview.addFile(bundleName + ".properties",
                        PROPERTIES_COLOR, propTag, PROPERTIES_COLOR);
            }
        }
        treePreview.revalidate();
        treePreview.repaint();
    }

    /**
     * Splits a relative path like "module/src/main/java/com/example" into
     * prefix "src/main/java/" and package suffix "com/example/".
     */
    private static String[] splitPath(String path) {
        // Find the src/main/java or src/main/resources boundary
        for (String marker : new String[]{"src/main/java/", "src/main/resources/",
                "src/main/java", "src/main/resources"}) {
            int idx = path.indexOf(marker);
            if (idx >= 0) {
                String prefix = path.substring(idx, idx + marker.length());
                if (!prefix.endsWith("/")) {
                    prefix += "/";
                }
                String suffix = path.substring(idx + marker.length());
                if (!suffix.isEmpty() && !suffix.endsWith("/")) {
                    suffix += "/";
                }
                return new String[]{prefix, suffix};
            }
        }
        return new String[]{path + "/", ""};
    }

    /**
     * Returns the computed view class name based on current input and mode.
     */
    public String getViewClassName() {
        return computeViewClassName(nameField.getText().trim(),
                segmentedControl.getSelectedIndex() == 1);
    }

    /**
     * Returns the computed controller class name.
     */
    public String getControllerClassName() {
        return computeBaseName(getViewClassName()) + "Controller";
    }

    /**
     * Returns whether the "Create Controller" option is selected.
     */
    public boolean isCreateController() {
        return controllerCard.isCardSelected();
    }

    /**
     * Returns whether to create the FXML file. Always true — FXML is mandatory for FxmlKit.
     */
    public boolean isCreateFxml() {
        return true;
    }

    /**
     * Returns whether the "Create CSS" option is selected.
     */
    public boolean isCreateCss() {
        return cssCard.isCardSelected();
    }

    /**
     * Returns whether FxmlViewProvider mode is selected.
     */
    public boolean isProviderMode() {
        return segmentedControl.getSelectedIndex() == 1;
    }

    /**
     * Computes the view class name from user input, applying smart suffix handling.
     */
    static String computeViewClassName(String input, boolean isProvider) {
        if (isProvider) {
            if (input.endsWith("ViewProvider")) {
                return input;
            }
            if (input.endsWith("View")) {
                return input + "Provider";
            }
            return input + "ViewProvider";
        } else {
            if (input.endsWith("ViewProvider")) {
                return input.substring(0, input.length() - "Provider".length());
            }
            if (input.endsWith("View")) {
                return input;
            }
            return input + "View";
        }
    }

    /**
     * Extracts the base name by stripping known view suffixes.
     */
    static String computeBaseName(String viewClassName) {
        if (viewClassName.endsWith("ViewProvider")) {
            return viewClassName.substring(0, viewClassName.length() - "ViewProvider".length());
        }
        if (viewClassName.endsWith("View")) {
            return viewClassName.substring(0, viewClassName.length() - "View".length());
        }
        return viewClassName;
    }

    /**
     * Returns the i18n configuration, or null if not configured.
     */
    public @Nullable I18nConfig getI18nConfig() {
        return i18nConfig;
    }

    private void openI18nDialogOrRevert() {
        I18nConfigDialog dialog = new I18nConfigDialog(project, i18nConfig, javaDir);
        if (dialog.showAndGet()) {
            i18nConfig = dialog.getResult();
            summaryBar.updateState(true, i18nConfig);
            summaryBar.setVisible(true);
        } else {
            propertiesCard.setSelected(false);
            summaryBar.setVisible(false);
        }
        updatePreview();
    }

    private void openI18nDialog() {
        I18nConfigDialog dialog = new I18nConfigDialog(project, i18nConfig, javaDir);
        if (dialog.showAndGet()) {
            i18nConfig = dialog.getResult();
            summaryBar.updateState(true, i18nConfig);
            summaryBar.setVisible(propertiesCard.isCardSelected());
            updatePreview();
        }
    }

    // ---- Utility methods ----

    private static Color getFocusColor() {
        return JBUI.CurrentTheme.Focus.focusColor();
    }

    private static Color getBorderColor() {
        return JBUI.CurrentTheme.DefaultTabs.borderColor();
    }

    private static Color getDefaultButtonBackground() {
        try {
            Color c = JBColor.namedColor("Button.default.startBackground", null);
            if (c != null) {
                return c;
            }
        } catch (Exception ignored) {
        }
        return new JBColor(new Color(0x3574F0), new Color(0x3574F0));
    }

    private static Font getMonoFont(int size) {
        try {
            String fontName = EditorColorsManager.getInstance()
                    .getGlobalScheme().getEditorFontName();
            if (fontName != null) {
                return new Font(fontName, Font.PLAIN, size);
            }
        } catch (Exception ignored) {
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    // ---- Inner classes ----

    /**
     * Rounded border with configurable radius and color.
     */
    private static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return JBUI.emptyInsets();
        }
    }

    /**
     * Two-option segmented toggle control.
     */
    private static class SegmentedControl extends JPanel {
        private int selectedIndex;
        private final String[] labels;
        private Runnable onSelectionChanged;

        SegmentedControl(String label0, String label1) {
            this.labels = new String[]{label0, label1};
            this.selectedIndex = 0;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMinimumSize(new Dimension(-1, JBUI.scale(32)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int half = getWidth() / 2;
                    int newIndex = e.getX() < half ? 0 : 1;
                    if (newIndex != selectedIndex) {
                        selectedIndex = newIndex;
                        repaint();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.run();
                        }
                    }
                }
            });
        }

        void setOnSelectionChanged(Runnable r) {
            this.onSelectionChanged = r;
        }

        int getSelectedIndex() {
            return selectedIndex;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int outerRadius = JBUI.scale(6);
            int innerRadius = JBUI.scale(4);
            int pad = JBUI.scale(3);

            // Outer container
            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, outerRadius, outerRadius));
            g2.setColor(getBorderColor());
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, outerRadius, outerRadius));

            int half = w / 2;
            Font boldFont = getFont().deriveFont(Font.BOLD, (float) JBUI.scale(13));
            g2.setFont(boldFont);

            for (int i = 0; i < 2; i++) {
                int x0 = i == 0 ? pad : half + 1;
                int segW = i == 0 ? (half - pad) : (w - half - pad - 1);

                FontMetrics fm = g2.getFontMetrics(boldFont);
                if (i == selectedIndex) {
                    g2.setColor(getDefaultButtonBackground());
                    g2.fill(new RoundRectangle2D.Float(x0, pad, segW, h - pad * 2,
                            innerRadius, innerRadius));
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(UIUtil.getLabelForeground());
                }

                int textW = fm.stringWidth(labels[i]);
                int textX = x0 + (segW - textW) / 2;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(labels[i], textX, textY);
            }

            g2.dispose();
        }
    }

    private enum FileIconType {
        CONTROLLER, FXML, CSS, I18N
    }

    /**
     * Card for a file type option. Mandatory cards are always selected and non-clickable.
     */
    private static class FileTypeCard extends JPanel {
        private boolean selected;
        private boolean hovered;
        private String label;
        private final Color accentColor;
        private final FileIconType iconType;
        private final boolean mandatory;
        private Runnable onSelectionChanged;

        FileTypeCard(String label, Color accentColor, FileIconType iconType, boolean mandatory) {
            this.label = label;
            this.accentColor = accentColor;
            this.iconType = iconType;
            this.selected = true;
            this.mandatory = mandatory;
            setOpaque(false);
            setMinimumSize(new Dimension(-1, JBUI.scale(80)));

            if (!mandatory) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        selected = !selected;
                        repaint();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.run();
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }
        }

        void setLabel(String label) {
            this.label = label;
            repaint();
        }

        void setOnSelectionChanged(Runnable r) {
            this.onSelectionChanged = r;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        boolean isCardSelected() {
            return selected;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int radius = JBUI.scale(6);
            boolean active = selected || mandatory;
            boolean highlighted = active || hovered;

            // Card background and border
            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
            g2.setColor(highlighted ? accentColor : getBorderColor());
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius, radius));

            // Check indicator (top-right) — only for optional cards
            if (!mandatory) {
                int checkSize = JBUI.scale(14);
                int checkX = w - checkSize - JBUI.scale(6);
                int checkY = JBUI.scale(6);
                int checkR = JBUI.scale(3);
                if (selected) {
                    g2.setColor(accentColor);
                    g2.fill(new RoundRectangle2D.Float(checkX, checkY, checkSize, checkSize,
                            checkR, checkR));
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(JBUI.scale(2), BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    int cx = checkX + checkSize / 2;
                    int cy = checkY + checkSize / 2;
                    g2.drawLine(cx - JBUI.scale(3), cy, cx - JBUI.scale(1), cy + JBUI.scale(2));
                    g2.drawLine(cx - JBUI.scale(1), cy + JBUI.scale(2), cx + JBUI.scale(3),
                            cy - JBUI.scale(2));
                } else {
                    g2.setColor(getBorderColor());
                    g2.draw(new RoundRectangle2D.Float(checkX, checkY, checkSize, checkSize,
                            checkR, checkR));
                }
            }

            // File icon in center
            Color iconColor = highlighted ? accentColor : UIUtil.getLabelDisabledForeground();
            drawFileIcon(g2, w, h, iconColor);

            // Label at bottom
            Font labelFont = getFont().deriveFont((float) JBUI.scale(11));
            g2.setFont(labelFont);
            g2.setColor(highlighted ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(label);
            g2.drawString(label, (w - textW) / 2, h - JBUI.scale(8));

            g2.dispose();
        }

        private void drawFileIcon(Graphics2D g2, int w, int h, Color color) {
            int iconW = JBUI.scale(24);
            int iconH = JBUI.scale(30);
            int fold = JBUI.scale(7);
            int cx = w / 2;
            int cy = h / 2 - JBUI.scale(4);
            int left = cx - iconW / 2;
            int top = cy - iconH / 2;

            int[] xPts = {left, left + iconW - fold, left + iconW, left + iconW, left};
            int[] yPts = {top, top, top + fold, top + iconH, top + iconH};
            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fillPolygon(xPts, yPts, 5);
            g2.setColor(color);
            g2.setStroke(new BasicStroke((float) JBUI.scale(2)));
            g2.drawPolygon(xPts, yPts, 5);
            g2.drawLine(left + iconW - fold, top, left + iconW - fold, top + fold);
            g2.drawLine(left + iconW - fold, top + fold, left + iconW, top + fold);

            g2.setColor(color);
            String text;
            float fontSize;
            switch (iconType) {
                case CONTROLLER:
                    text = "C";
                    fontSize = JBUI.scale(14);
                    break;
                case FXML:
                    text = "fx";
                    fontSize = JBUI.scale(10);
                    break;
                case CSS:
                    text = "css";
                    fontSize = JBUI.scale(8);
                    break;
                default:
                    text = "i18n";
                    fontSize = JBUI.scale(8);
                    break;
            }
            Font iconFont = getFont().deriveFont(Font.BOLD, fontSize);
            g2.setFont(iconFont);
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(text);
            int textX = cx - textW / 2;
            int textY = cy + fm.getAscent() / 2 + JBUI.scale(2);
            g2.drawString(text, textX, textY);
        }
    }

    /**
     * Live-updating file tree preview panel.
     */
    private static class FileTreePreview extends JPanel {
        private final java.util.List<TreeEntry> entries = new java.util.ArrayList<>();

        FileTreePreview() {
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            int pad = JBUI.scale(12);
            int lineHeight = JBUI.scale(20);
            Font titleFont = getFont().deriveFont((float) JBUI.scale(10));
            int titleH = getFontMetrics(titleFont).getHeight();
            int contentH = pad + titleH + JBUI.scale(6)
                    + entries.size() * lineHeight + pad;
            return new Dimension(JBUI.scale(440), Math.max(JBUI.scale(100), contentH));
        }

        void clearEntries() {
            entries.clear();
        }

        void addDirectory(String name) {
            entries.add(new TreeEntry(name, TreeEntryType.DIRECTORY, null, null, null));
        }

        void addSubDirectory(String name) {
            entries.add(new TreeEntry(name, TreeEntryType.SUB_DIRECTORY, null, null, null));
        }

        void addFile(String name, Color iconColor, String tag, Color tagColor) {
            entries.add(new TreeEntry(name, TreeEntryType.FILE, iconColor, tag, tagColor));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int radius = JBUI.scale(6);
            int pad = JBUI.scale(12);

            // Background
            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
            g2.setColor(getBorderColor());
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius, radius));

            // Title
            Font titleFont = getFont().deriveFont((float) JBUI.scale(10));
            g2.setFont(titleFont);
            g2.setColor(UIUtil.getContextHelpForeground());
            g2.drawString(FxToolsBundle.message("dialog.new.fxmlkit.view.preview"),
                    pad, pad + g2.getFontMetrics().getAscent());

            int y = pad + g2.getFontMetrics().getHeight() + JBUI.scale(6);
            Font monoFont = getMonoFont(JBUI.scale(12));
            Font smallMonoFont = getMonoFont(JBUI.scale(11));
            Font tagFont = getFont().deriveFont((float) JBUI.scale(9));
            int lineHeight = JBUI.scale(20);
            int iconSize = JBUI.scale(16);
            Icon folderIcon = AllIcons.Nodes.Folder;

            for (TreeEntry entry : entries) {
                int x = pad;

                switch (entry.type) {
                    case DIRECTORY: {
                        folderIcon.paintIcon(this, g2, x, y + (lineHeight - iconSize) / 2);
                        x += iconSize + JBUI.scale(4);
                        g2.setFont(smallMonoFont);
                        g2.setColor(UIUtil.getContextHelpForeground());
                        g2.drawString(entry.name, x,
                                y + (lineHeight + g2.getFontMetrics().getAscent()
                                        - g2.getFontMetrics().getDescent()) / 2);
                        break;
                    }
                    case SUB_DIRECTORY: {
                        x += JBUI.scale(20);
                        g2.setFont(smallMonoFont);
                        g2.setColor(UIUtil.getLabelForeground());
                        g2.drawString(entry.name, x,
                                y + (lineHeight + g2.getFontMetrics().getAscent()
                                        - g2.getFontMetrics().getDescent()) / 2);
                        break;
                    }
                    case FILE: {
                        x += JBUI.scale(24);
                        int dotSize = JBUI.scale(8);
                        int dotY = y + (lineHeight - dotSize) / 2;
                        g2.setColor(entry.iconColor);
                        g2.fillOval(x + (iconSize - dotSize) / 2, dotY, dotSize, dotSize);
                        x += iconSize + JBUI.scale(4);

                        g2.setFont(monoFont);
                        g2.setColor(UIUtil.getLabelForeground());
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(entry.name, x,
                                y + (lineHeight + fm.getAscent() - fm.getDescent()) / 2);

                        // Right-aligned tag
                        if (entry.tag != null) {
                            g2.setFont(tagFont);
                            FontMetrics tfm = g2.getFontMetrics();
                            int tagW = tfm.stringWidth(entry.tag) + JBUI.scale(8);
                            int tagH = tfm.getHeight() + JBUI.scale(2);
                            int tagX = w - pad - tagW;
                            int tagY = y + (lineHeight - tagH) / 2;

                            Color tagBg = new Color(
                                    entry.tagColor.getRed(),
                                    entry.tagColor.getGreen(),
                                    entry.tagColor.getBlue(), 30);
                            g2.setColor(tagBg);
                            g2.fill(new RoundRectangle2D.Float(tagX, tagY, tagW, tagH,
                                    JBUI.scale(4), JBUI.scale(4)));

                            g2.setColor(entry.tagColor);
                            g2.drawString(entry.tag,
                                    tagX + JBUI.scale(4),
                                    tagY + (tagH + tfm.getAscent() - tfm.getDescent()) / 2);
                        }
                        break;
                    }
                }
                y += lineHeight;
            }

            g2.dispose();
        }

        private enum TreeEntryType {
            DIRECTORY, SUB_DIRECTORY, FILE
        }

        private static class TreeEntry {
            final String name;
            final TreeEntryType type;
            final Color iconColor;
            final String tag;
            final Color tagColor;

            TreeEntry(String name, TreeEntryType type, Color iconColor,
                      String tag, Color tagColor) {
                this.name = name;
                this.type = type;
                this.iconColor = iconColor;
                this.tag = tag;
                this.tagColor = tagColor;
            }
        }
    }

    /**
     * Summary bar displayed below the cards when Properties is checked.
     * Shows either a yellow warning (not configured) or green info (configured).
     * A small triangle arrow visually connects to the Properties card above.
     */
    private class I18nSummaryBar extends JPanel {
        private static final int ARROW_H = 8;

        private boolean configured;
        private final JBLabel iconLabel;
        private final JBLabel textLabel;
        private final JButton actionButton;

        I18nSummaryBar() {
            setOpaque(false);
            int arrowH = JBUI.scale(ARROW_H);
            int insetH = JBUI.scale(8);
            int insetV = JBUI.scale(5);
            setLayout(new MigLayout(
                    "insets " + (arrowH + insetV) + " " + insetH + " " + insetV + " " + insetH
                            + ", fillx",
                    "[]" + JBUI.scale(4) + "[grow][]"
            ));

            iconLabel = new JBLabel(AllIcons.General.Warning);
            textLabel = new JBLabel();
            actionButton = new JButton();
            actionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            actionButton.putClientProperty("JButton.buttonType", "borderless");
            actionButton.addActionListener(e -> openI18nDialog());

            add(iconLabel);
            add(textLabel, "growx");
            add(actionButton);
        }

        void updateState(boolean configured, @Nullable I18nConfig config) {
            this.configured = configured;
            if (configured && config != null) {
                iconLabel.setIcon(AllIcons.General.InspectionsOK);
                textLabel.setForeground(SUMMARY_GREEN_FG);

                java.util.List<String> locales = config.getSelectedLocales();
                int totalLocales = locales.size() + 1;
                String localeStr;
                if (locales.size() > 3) {
                    localeStr = String.join(", ", locales.subList(0, 3)) + ", ...";
                } else {
                    localeStr = String.join(", ", locales);
                }
                String localeDisplay = localeStr.isEmpty()
                        ? totalLocales + " locales"
                        : totalLocales + " locales (" + localeStr + ")";
                textLabel.setText("<html><b>" + config.getBundleName()
                        + "</b> \u2014 " + localeDisplay + "</html>");
                actionButton.setText(FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.edit"));
            } else {
                iconLabel.setIcon(AllIcons.General.Warning);
                textLabel.setForeground(SUMMARY_YELLOW_FG);
                textLabel.setText(FxToolsBundle.message(
                        "dialog.new.fxmlkit.view.i18n.not.configured"));
                actionButton.setText(FxToolsBundle.message(
                        "dialog.new.fxmlkit.view.i18n.configure"));
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arrowH = JBUI.scale(ARROW_H);
            int arrowW = JBUI.scale(12);
            int radius = JBUI.scale(6);

            Color bgColor = configured ? SUMMARY_GREEN_BG : SUMMARY_YELLOW_BG;
            Color borderColor = configured ? SUMMARY_GREEN_BORDER : SUMMARY_YELLOW_BORDER;

            // Calculate arrow X position (center of Properties card)
            int arrowX = w * 9 / 10;
            if (propertiesCard.isShowing() && isShowing()) {
                try {
                    java.awt.Point p = SwingUtilities.convertPoint(
                            propertiesCard, propertiesCard.getWidth() / 2, 0, this);
                    arrowX = p.x;
                } catch (Exception ignored) {
                }
            }

            // Bar background (below arrow)
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Float(0, arrowH, w, h - arrowH, radius, radius));

            // Arrow triangle (filled, covers bar's top border)
            int[] xPts = {arrowX - arrowW / 2, arrowX, arrowX + arrowW / 2};
            int[] yPts = {arrowH, 0, arrowH};
            g2.fillPolygon(xPts, yPts, 3);

            // Bar border
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1));
            g2.draw(new RoundRectangle2D.Float(0, arrowH, w - 1, h - arrowH - 1,
                    radius, radius));

            // Arrow border (left and right edges only)
            g2.drawLine(xPts[0], yPts[0], xPts[1], yPts[1]);
            g2.drawLine(xPts[1], yPts[1], xPts[2], yPts[2]);

            g2.dispose();
        }
    }
}
