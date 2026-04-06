package io.github.leewyatt.fxtools.fxmlkit.dialog;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dialog for configuring i18n Resource Bundle.
 * Supports selecting an existing bundle or creating a new one.
 */
public class I18nConfigDialog extends DialogWrapper {

    private static final JBColor SELECTED_BUNDLE_BORDER =
            new JBColor(new Color(0x3574F0), new Color(0x5A95F5));
    private static final JBColor SELECTED_BUNDLE_BG =
            new JBColor(new Color(0xF0F5FF), new Color(0x2B3548));
    private static final JBColor LOCALE_CHECKED_BG =
            new JBColor(new Color(0xE8F5E9), new Color(0x1E3A2F));

    private final Project project;
    private final List<BundleInfo> existingBundles;
    private final @Nullable I18nConfig previousConfig;
    private final @Nullable PsiDirectory contextDir;
    private @Nullable String defaultLocation;

    // UI — tabs
    private JBTabbedPane tabbedPane;

    // UI — existing tab
    private final ButtonGroup bundleGroup = new ButtonGroup();
    private final List<JPanel> bundleCards = new ArrayList<>();
    private final List<BundleInfo> bundleCardInfos = new ArrayList<>();
    private BundleInfo selectedBundle;
    private JBLabel existingStatusLabel;

    // UI — create new tab
    private JBTextField bundleNameField;
    private JBTextField locationField;
    private JBLabel localeCountLabel;
    private JBLabel bundleConflictLabel;
    private final List<LocaleRow> localeRows = new ArrayList<>();
    private boolean showSelectedOnly;
    private SearchTextField localeSearchField;

    /**
     * Creates the dialog for i18n configuration.
     *
     * @param project        the current project
     * @param previousConfig previous configuration to pre-populate, may be null
     * @param contextDir     the source directory for module resolution, may be null
     */
    public I18nConfigDialog(@NotNull Project project,
                            @Nullable I18nConfig previousConfig,
                            @Nullable PsiDirectory contextDir) {
        super(project, true);
        this.project = project;
        this.previousConfig = previousConfig;
        this.contextDir = contextDir;
        this.defaultLocation = computeDefaultLocation();
        this.existingBundles = ReadAction.compute(() -> scanExistingBundles(project));
        setTitle(FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.dialog.title"));
        init();
        initValidation();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        if (existingBundles.isEmpty()) {
            // Mode B: no existing bundles, show create panel directly
            JPanel wrapper = new JPanel(new MigLayout("wrap 1, fillx, insets 0", "[grow,fill]"));
            JBLabel title = new JBLabel(
                    FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.create.title"));
            title.setForeground(UIUtil.getContextHelpForeground());
            title.setFont(title.getFont().deriveFont((float) JBUI.scale(10)));
            wrapper.add(title, "gapbottom " + JBUI.scale(6));
            wrapper.add(createNewBundlePanel(), "growx, pushy, growy");
            wrapper.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(400)));
            return wrapper;
        }

        // Mode A: has existing bundles, show tabs
        tabbedPane = new JBTabbedPane();
        tabbedPane.addTab(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.tab.existing"),
                createExistingPanel());
        tabbedPane.addTab(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.tab.create"),
                createNewBundlePanel());

        // Pre-select tab from previous config
        if (previousConfig != null && previousConfig.getMode() == I18nConfig.Mode.CREATE_NEW) {
            tabbedPane.setSelectedIndex(1);
        }

        tabbedPane.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(400)));
        return tabbedPane;
    }

    private JPanel createExistingPanel() {
        JPanel panel = new JPanel(new MigLayout("wrap 1, fillx, insets 8", "[grow,fill]"));

        for (BundleInfo bundle : existingBundles) {
            JPanel card = createBundleCard(bundle);
            bundleCards.add(card);
            bundleCardInfos.add(bundle);
            panel.add(card, "growx, gapbottom " + JBUI.scale(4));
        }

        JBScrollPane scrollPane = new JBScrollPane(panel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Status label above the bundle list
        existingStatusLabel = new JBLabel();
        updateExistingStatusLabel();

        JPanel wrapper = new JPanel(new MigLayout("wrap 1, fillx, insets 0", "[grow,fill]"));
        wrapper.add(existingStatusLabel, "growx, gapbottom " + JBUI.scale(4));
        wrapper.add(scrollPane, "growx, pushy, growy");
        return wrapper;
    }

    private JPanel createBundleCard(BundleInfo bundle) {
        JPanel card = new JPanel(new MigLayout(
                "insets " + JBUI.scale(8) + ", fillx",
                "[]" + JBUI.scale(8) + "[grow][]"
        ));
        card.setOpaque(true);
        card.setBackground(UIUtil.getTextFieldBackground());

        JRadioButton radio = new JRadioButton();
        radio.setOpaque(false);
        bundleGroup.add(radio);

        // Name (bold)
        JBLabel nameLabel = new JBLabel(bundle.baseName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        // Locale info
        String localeStr = formatLocales(bundle.locales);
        JBLabel localeLabel = new JBLabel(localeStr);
        localeLabel.setForeground(UIUtil.getContextHelpForeground());

        // Path (truncated with tooltip for full path)
        String displayPath = StringUtil.shortenPathWithEllipsis(bundle.displayPath, 35);
        JBLabel pathLabel = new JBLabel(displayPath);
        pathLabel.setToolTipText(bundle.displayPath);
        pathLabel.setForeground(UIUtil.getContextHelpForeground());

        // Right panel (name + locales)
        JPanel infoPanel = new JPanel(new MigLayout("insets 0, gap 0, fillx", "[grow]"));
        infoPanel.setOpaque(false);
        infoPanel.add(nameLabel, "growx, wrap");
        infoPanel.add(localeLabel, "growx");

        card.add(radio, "span 1 2, top");
        card.add(infoPanel, "growx");
        card.add(pathLabel, "right, top");

        // Default border
        updateCardBorder(card, false);

        // Pre-select from previous config
        boolean preSelect = previousConfig != null
                && previousConfig.getMode() == I18nConfig.Mode.EXISTING
                && bundle.baseName.equals(previousConfig.getBundleName());
        if (preSelect) {
            radio.setSelected(true);
            selectedBundle = bundle;
            updateCardBorder(card, true);
        }

        // Selection listener
        radio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectedBundle = bundle;
                updateAllCardBorders();
                updateExistingStatusLabel();
                setOKActionEnabled(true);
            }
        });

        // Click anywhere on card to select
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                radio.setSelected(true);
            }
        });

        return card;
    }

    private void updateAllCardBorders() {
        for (int i = 0; i < bundleCards.size(); i++) {
            boolean selected = bundleCardInfos.get(i) == selectedBundle;
            updateCardBorder(bundleCards.get(i), selected);
        }
    }

    private static void updateCardBorder(JPanel card, boolean selected) {
        int borderWidth = JBUI.scale(1);
        int radius = JBUI.scale(6);
        if (selected) {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SELECTED_BUNDLE_BORDER, borderWidth),
                    JBUI.Borders.empty(JBUI.scale(2))
            ));
            card.setBackground(SELECTED_BUNDLE_BG);
        } else {
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(
                            JBUI.CurrentTheme.DefaultTabs.borderColor(), borderWidth),
                    JBUI.Borders.empty(JBUI.scale(2))
            ));
            card.setBackground(UIUtil.getTextFieldBackground());
        }
    }

    private void updateExistingStatusLabel() {
        if (existingStatusLabel == null) {
            return;
        }
        if (selectedBundle != null) {
            existingStatusLabel.setIcon(AllIcons.General.InspectionsOK);
            existingStatusLabel.setText(
                    FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.bundle.selected",
                            selectedBundle.baseName));
            existingStatusLabel.setForeground(UIUtil.getLabelForeground());
        } else {
            existingStatusLabel.setIcon(AllIcons.General.Warning);
            existingStatusLabel.setText(
                    FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.error.select.bundle"));
            existingStatusLabel.setForeground(UIUtil.getLabelForeground());
        }
    }

    private JPanel createNewBundlePanel() {
        JPanel panel = new JPanel(new MigLayout(
                "wrap 1, fillx, insets " + JBUI.scale(8) + ", gap 0",
                "[grow,fill]"
        ));
        int gap = JBUI.scale(8);

        // Bundle name (label above input)
        panel.add(new JBLabel(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.bundle.name")),
                "growx, gapbottom " + JBUI.scale(2));
        bundleNameField = new JBTextField();
        bundleNameField.getEmptyText().setText("e.g. messages, labels, strings");
        if (previousConfig != null && previousConfig.getMode() == I18nConfig.Mode.CREATE_NEW) {
            bundleNameField.setText(previousConfig.getBundleName());
        }
        panel.add(bundleNameField, "growx, gapbottom " + JBUI.scale(2));

        // Bundle conflict error label
        bundleConflictLabel = new JBLabel(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.error.bundle.exists"));
        bundleConflictLabel.setForeground(JBColor.RED);
        bundleConflictLabel.setVisible(false);
        bundleConflictLabel.setFont(bundleConflictLabel.getFont().deriveFont(
                (float) JBUI.scale(11)));
        panel.add(bundleConflictLabel, "growx, hidemode 3, gapbottom " + JBUI.scale(2));

        // Trigger conflict check on name change
        bundleNameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkBundleConflict();
            }
        });

        // Location (label above input + browse button)
        panel.add(new JBLabel(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.location")),
                "growx, gapbottom " + JBUI.scale(2));
        JPanel locRow = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill][]"));
        locRow.setOpaque(false);
        locationField = new JBTextField();
        if (previousConfig != null && previousConfig.getMode() == I18nConfig.Mode.CREATE_NEW
                && !previousConfig.getBundlePath().isEmpty()) {
            locationField.setText(previousConfig.getBundlePath());
        } else if (defaultLocation != null && !defaultLocation.isEmpty()) {
            locationField.setText(defaultLocation);
        }
        // Trigger conflict check on location change
        locationField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                checkBundleConflict();
            }
        });
        locRow.add(locationField, "growx");
        JButton browseBtn = new JButton(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.browse"));
        browseBtn.addActionListener(e -> browseLocation());
        locRow.add(browseBtn);
        panel.add(locRow, "growx, gapbottom " + gap);

        // Locales title
        localeCountLabel = new JBLabel(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.locales", 1));
        panel.add(localeCountLabel, "growx, gapbottom " + JBUI.scale(2));

        // Toolbar: search + select all / invert / show selected
        JPanel toolbar = new JPanel(new MigLayout("insets 0, fillx", "[grow][][][]"));
        toolbar.setOpaque(false);
        localeSearchField = new SearchTextField(false);
        localeSearchField.getTextEditor().getEmptyText().setText(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.search"));
        localeSearchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                applyLocaleFilter();
            }
        });
        toolbar.add(localeSearchField, "growx");

        JButton selectAllBtn = new JButton(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.select.all"));
        selectAllBtn.addActionListener(e -> setAllLocales(true));
        toolbar.add(selectAllBtn);

        JButton invertBtn = new JButton(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.invert"));
        invertBtn.addActionListener(e -> invertLocales());
        toolbar.add(invertBtn);

        JButton showSelectedBtn = new JButton(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.show.selected"));
        showSelectedBtn.addActionListener(e -> {
            showSelectedOnly = !showSelectedOnly;
            showSelectedBtn.setText(showSelectedOnly
                    ? FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.show.all")
                    : FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.show.selected"));
            applyLocaleFilter();
        });
        toolbar.add(showSelectedBtn);

        panel.add(toolbar, "growx, gapbottom " + JBUI.scale(2));

        // Locale list
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(UIUtil.getTextFieldBackground());
        buildLocaleRows(listPanel);

        JBScrollPane localeScroll = new JBScrollPane(listPanel);
        localeScroll.setBorder(BorderFactory.createLineBorder(
                JBUI.CurrentTheme.DefaultTabs.borderColor()));
        localeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(localeScroll, "growx, pushy, growy, h " + JBUI.scale(250) + "!");

        return panel;
    }

    private void buildLocaleRows(JPanel listPanel) {
        // Pre-selected locales from previous config
        Set<String> preSelected = new HashSet<>();
        if (previousConfig != null && previousConfig.getMode() == I18nConfig.Mode.CREATE_NEW) {
            preSelected.addAll(previousConfig.getSelectedLocales());
        }

        // Default row (always first, always checked, disabled)
        LocaleRow defaultRow = new LocaleRow("default",
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.locale.always"),
                true, true);
        localeRows.add(defaultRow);
        listPanel.add(defaultRow);

        // Build available locales
        List<LocaleEntry> entries = buildLocaleEntries();

        // Sort: pre-selected first, then alphabetical by code
        entries.sort((a, b) -> {
            boolean aSelected = preSelected.contains(a.code);
            boolean bSelected = preSelected.contains(b.code);
            if (aSelected != bSelected) {
                return aSelected ? -1 : 1;
            }
            return a.code.compareTo(b.code);
        });

        // Create rows
        for (LocaleEntry entry : entries) {
            boolean checked = preSelected.contains(entry.code);
            LocaleRow row = new LocaleRow(entry.code, entry.displayName, false, checked);
            localeRows.add(row);
            listPanel.add(row);
        }

        updateLocaleCount();
    }

    private void applyLocaleFilter() {
        String filter = localeSearchField.getText().trim();
        String lower = filter.toLowerCase(Locale.ROOT);
        for (LocaleRow row : localeRows) {
            if (row.isDefault) {
                row.setVisible(true);
                continue;
            }
            boolean matchesSearch = lower.isEmpty()
                    || row.localeCode.toLowerCase(Locale.ROOT).contains(lower)
                    || row.displayName.toLowerCase(Locale.ROOT).contains(lower);
            boolean matchesFilter = !showSelectedOnly || row.isChecked();
            row.setVisible(matchesSearch && matchesFilter);
        }
    }

    private void setAllLocales(boolean selected) {
        for (LocaleRow row : localeRows) {
            if (!row.isDefault) {
                row.setChecked(selected);
            }
        }
        updateLocaleCount();
    }

    private void invertLocales() {
        for (LocaleRow row : localeRows) {
            if (!row.isDefault) {
                row.setChecked(!row.isChecked());
            }
        }
        updateLocaleCount();
    }

    private void updateLocaleCount() {
        int count = 0;
        for (LocaleRow row : localeRows) {
            if (row.isChecked()) {
                count++;
            }
        }
        localeCountLabel.setText(
                FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.locales", count));
    }

    private void browseLocation() {
        VirtualFile baseDir = project.getBaseDir();
        VirtualFile startDir = resolveLocationDir();
        if (startDir == null) {
            startDir = baseDir;
        }
        VirtualFile chosen = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project, startDir);
        if (chosen != null && baseDir != null) {
            String rel = VfsUtilCore.getRelativePath(chosen, baseDir);
            locationField.setText(rel != null ? rel + "/" : chosen.getPath());
        }
    }

    private @Nullable VirtualFile resolveLocationDir() {
        String loc = locationField.getText().trim().replaceAll("/+$", "");
        VirtualFile baseDir = project.getBaseDir();
        if (!loc.isEmpty() && baseDir != null) {
            VirtualFile dir = baseDir.findFileByRelativePath(loc);
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private boolean isCreateNewMode() {
        if (existingBundles.isEmpty()) {
            return true;
        }
        return tabbedPane != null && tabbedPane.getSelectedIndex() == 1;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (isCreateNewMode()) {
            String name = bundleNameField.getText().trim();
            if (name.isEmpty()) {
                return new ValidationInfo(
                        FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.error.empty.name"),
                        bundleNameField);
            }
            String location = locationField.getText().trim();
            if (location.isEmpty()) {
                return new ValidationInfo(
                        FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.error.empty.location"),
                        locationField);
            }
            if (hasBundleConflict(name, location)) {
                return new ValidationInfo(
                        FxToolsBundle.message("dialog.new.fxmlkit.view.i18n.error.bundle.exists"),
                        bundleNameField);
            }
        } else {
            if (selectedBundle == null) {
                setOKActionEnabled(false);
                return null;
            }
        }
        setOKActionEnabled(true);
        return null;
    }

    /**
     * Returns the configured I18nConfig based on dialog input.
     */
    public @NotNull I18nConfig getResult() {
        if (isCreateNewMode()) {
            String name = bundleNameField.getText().trim();
            String location = locationField.getText().trim();
            List<String> locales = new ArrayList<>();
            for (LocaleRow row : localeRows) {
                if (!row.isDefault && row.isChecked()) {
                    locales.add(row.localeCode);
                }
            }
            return new I18nConfig(I18nConfig.Mode.CREATE_NEW, name, location, locales);
        } else {
            return new I18nConfig(I18nConfig.Mode.EXISTING,
                    selectedBundle.baseName, selectedBundle.displayPath,
                    selectedBundle.locales);
        }
    }

    // ---- Locale data ----

    private static List<LocaleEntry> buildLocaleEntries() {
        Set<String> seen = new TreeSet<>();
        List<LocaleEntry> entries = new ArrayList<>();

        for (Locale locale : Locale.getAvailableLocales()) {
            if (locale.getLanguage().isEmpty()) {
                continue;
            }
            if (!locale.getVariant().isEmpty() || !locale.getScript().isEmpty()) {
                continue;
            }
            String code = locale.getLanguage();
            if (!locale.getCountry().isEmpty()) {
                code += "_" + locale.getCountry();
            }
            if (seen.add(code)) {
                String displayName = locale.getDisplayLanguage(Locale.ENGLISH);
                if (!locale.getCountry().isEmpty()) {
                    displayName += " (" + locale.getDisplayCountry(Locale.ENGLISH) + ")";
                }
                entries.add(new LocaleEntry(code, displayName));
            }
        }
        return entries;
    }

    private static Set<String> buildKnownLocales() {
        Set<String> locales = new HashSet<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            if (locale.getLanguage().isEmpty()) {
                continue;
            }
            if (!locale.getVariant().isEmpty() || !locale.getScript().isEmpty()) {
                continue;
            }
            String tag = locale.getLanguage();
            if (!locale.getCountry().isEmpty()) {
                tag += "_" + locale.getCountry();
            }
            locales.add(tag);
        }
        return locales;
    }

    private @Nullable String computeDefaultLocation() {
        if (contextDir == null) {
            return null;
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(contextDir);
        if (module == null) {
            return null;
        }
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }
        List<VirtualFile> resRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);
        VirtualFile target;
        if (!resRoots.isEmpty()) {
            target = resRoots.get(0);
        } else {
            VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
            target = contentRoots.length > 0 ? contentRoots[0] : null;
        }
        if (target == null) {
            return null;
        }
        String rel = VfsUtilCore.getRelativePath(target, baseDir);
        return rel != null ? rel + "/" : null;
    }

    private void checkBundleConflict() {
        String name = bundleNameField.getText().trim();
        String location = locationField.getText().trim();
        boolean conflict = !name.isEmpty() && !location.isEmpty()
                && hasBundleConflict(name, location);
        bundleConflictLabel.setVisible(conflict);
    }

    private boolean hasBundleConflict(String bundleName, String location) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return false;
        }
        String dirPath = location.replaceAll("/+$", "");
        if (dirPath.isEmpty()) {
            return false;
        }
        VirtualFile dir = baseDir.findFileByRelativePath(dirPath);
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                continue;
            }
            String fileName = child.getNameWithoutExtension();
            if ("properties".equals(child.getExtension())
                    && (fileName.equals(bundleName)
                    || fileName.startsWith(bundleName + "_"))) {
                return true;
            }
        }
        return false;
    }

    // ---- Bundle scanning ----

    static List<BundleInfo> scanExistingBundles(@NotNull Project project) {
        Set<String> knownLocales = buildKnownLocales();
        List<BundleInfo> result = new ArrayList<>();

        // Collect .properties files grouped by directory
        Map<VirtualFile, List<String>> dirToNames = new HashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (!file.isDirectory() && "properties".equals(file.getExtension())) {
                VirtualFile parent = file.getParent();
                if (parent != null) {
                    dirToNames.computeIfAbsent(parent, k -> new ArrayList<>())
                            .add(file.getNameWithoutExtension());
                }
            }
            return true;
        });

        VirtualFile baseDir = project.getBaseDir();

        for (Map.Entry<VirtualFile, List<String>> entry : dirToNames.entrySet()) {
            VirtualFile dir = entry.getKey();
            List<String> names = entry.getValue();
            Set<String> nameSet = new HashSet<>(names);

            // Find locale variants: baseName → locales
            Map<String, List<String>> bundles = new HashMap<>();
            for (String name : names) {
                String locale = extractLocale(name, knownLocales);
                if (locale != null) {
                    String baseName = name.substring(0,
                            name.length() - locale.length() - 1);
                    if (nameSet.contains(baseName)) {
                        bundles.computeIfAbsent(baseName, k -> new ArrayList<>())
                                .add(locale);
                    }
                }
            }

            for (Map.Entry<String, List<String>> bundle : bundles.entrySet()) {
                List<String> locales = bundle.getValue();
                Collections.sort(locales);
                String displayPath = "";
                if (baseDir != null) {
                    String rel = VfsUtilCore.getRelativePath(dir, baseDir);
                    displayPath = rel != null ? rel + "/" : dir.getPath() + "/";
                }
                result.add(new BundleInfo(bundle.getKey(), displayPath, locales, dir));
            }
        }
        return result;
    }

    private static @Nullable String extractLocale(String nameWithoutExt,
                                                   Set<String> knownLocales) {
        // Try each underscore from right to left to find a known locale suffix
        for (int i = nameWithoutExt.length() - 1; i >= 0; i--) {
            if (nameWithoutExt.charAt(i) == '_') {
                String suffix = nameWithoutExt.substring(i + 1);
                if (knownLocales.contains(suffix)) {
                    return suffix;
                }
            }
        }
        return null;
    }

    private static String formatLocales(List<String> locales) {
        String joined;
        if (locales.size() > 3) {
            joined = String.join(", ", locales.subList(0, 3)) + ", ...";
        } else {
            joined = String.join(", ", locales);
        }
        return locales.size() + " locales (" + joined + ")";
    }

    // ---- Inner classes ----

    static class BundleInfo {
        final String baseName;
        final String displayPath;
        final List<String> locales;
        final VirtualFile directory;

        BundleInfo(String baseName, String displayPath, List<String> locales,
                   VirtualFile directory) {
            this.baseName = baseName;
            this.displayPath = displayPath;
            this.locales = locales;
            this.directory = directory;
        }
    }

    private static class LocaleEntry {
        final String code;
        final String displayName;

        LocaleEntry(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
    }

    /**
     * A single row in the locale checkbox list.
     */
    private class LocaleRow extends JPanel {
        final String localeCode;
        final String displayName;
        final boolean isDefault;
        private final JBCheckBox checkBox;
        private final JBLabel codeLabel;

        LocaleRow(String localeCode, String displayName, boolean isDefault, boolean checked) {
            this.localeCode = localeCode;
            this.displayName = displayName;
            this.isDefault = isDefault;

            setLayout(new MigLayout(
                    "insets " + JBUI.scale(2) + " " + JBUI.scale(6) + " "
                            + JBUI.scale(2) + " " + JBUI.scale(6) + ", fillx",
                    "[]" + JBUI.scale(4) + "[grow][]"
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(28)));

            checkBox = new JBCheckBox();
            checkBox.setSelected(checked || isDefault);
            checkBox.setEnabled(!isDefault);
            checkBox.setOpaque(false);

            codeLabel = new JBLabel(localeCode);
            if (checked || isDefault) {
                codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD));
            }

            JBLabel nameLabel = new JBLabel(displayName);
            nameLabel.setForeground(UIUtil.getContextHelpForeground());

            add(checkBox);
            add(codeLabel, "growx");
            add(nameLabel);

            if (isDefault) {
                setOpaque(true);
                setBackground(UIUtil.getDecoratedRowColor());
            } else {
                setOpaque(checked);
                if (checked) {
                    setBackground(LOCALE_CHECKED_BG);
                }
                checkBox.addItemListener(e -> {
                    boolean sel = checkBox.isSelected();
                    applyCheckedStyle(sel);
                    updateLocaleCount();
                });

                // Click anywhere on row to toggle
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        checkBox.setSelected(!checkBox.isSelected());
                    }
                });
            }
        }

        boolean isChecked() {
            return checkBox.isSelected();
        }

        void setChecked(boolean checked) {
            checkBox.setSelected(checked);
        }

        private void applyCheckedStyle(boolean sel) {
            setOpaque(sel);
            setBackground(sel ? LOCALE_CHECKED_BG : null);
            codeLabel.setFont(codeLabel.getFont().deriveFont(
                    sel ? Font.BOLD : Font.PLAIN));
            repaint();
        }
    }
}
