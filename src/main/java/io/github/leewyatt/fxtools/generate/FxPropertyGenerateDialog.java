package io.github.leewyatt.fxtools.generate;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertiesIntegrator;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertyDescriptor;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for generating JavaFX Property fields with getter/setter/property methods.
 */
public class FxPropertyGenerateDialog extends DialogWrapper {

    private static final int LABEL_COLUMN_WIDTH = 100;
    private static final int FIELD_COLUMN_WIDTH = 270;
    private static final int INDENTED_FIELD_COLUMN_WIDTH = 236;
    private static final int DEFAULT_FIELD_HEIGHT = 28;

    private final Project project;
    private final String className;
    private final String superClassName;
    private final boolean useControlCssMetaData;
    private final boolean hasStyleableProperties;
    private final JBTextField nameField = new JBTextField();
    private final ComboBox<FxPropertyType> typeCombo = new ComboBox<>(FxPropertyType.values());
    private final JBLabel valueTypeLabel = new JBLabel();
    private final JBTextField defaultValueField = new JBTextField();
    private final BooleanDefaultSelector booleanDefaultSelector = new BooleanDefaultSelector();
    private final JPanel defaultValueCard = new JPanel(new CardLayout());
    private final JBTextField genericField = new JBTextField();
    private final JBTextField genericKeyField = new JBTextField();
    private final JBTextField genericValueField = new JBTextField();
    private final JBLabel genericLabel = new JBLabel();
    private final JBLabel genericKeyLabel = new JBLabel();
    private final JBLabel genericValueLabel = new JBLabel();
    private final JBLabel converterModeLabel = new JBLabel();
    private final ComboBox<CssConverterMode> converterModeCombo = new ComboBox<>(CssConverterMode.values());
    private final ButtonGroup accessModeGroup = new ButtonGroup();
    private final JRadioButton readonlyRadio;
    private final JRadioButton standardRadio;
    private final JRadioButton styleableRadio;
    private final JCheckBox lazyCheck;
    private final JCheckBox provideDefaultCheck;
    private final JCheckBox constantCheck;
    private final JBLabel cssNameLabel = new JBLabel();
    private final JBTextField cssNameField = new JBTextField();
    private final JTextArea previewArea = new JTextArea();
    private final JTextArea lineNumberArea = new JTextArea("1");
    private String generatedCode = "";
    private boolean autoUpdateCssName = true;

    /**
     * Creates the JavaFX Property generation dialog.
     */
    public FxPropertyGenerateDialog(@NotNull Project project,
                                    @NotNull String className,
                                    @NotNull String superClassName,
                                    boolean useControlCssMetaData,
                                    boolean hasStyleableProperties) {
        super(project, true);
        this.project = project;
        this.className = className;
        this.superClassName = superClassName;
        this.useControlCssMetaData = useControlCssMetaData;
        this.hasStyleableProperties = hasStyleableProperties;
        readonlyRadio = new JRadioButton(FxToolsBundle.message("generate.fx.property.access.readonly"), false);
        standardRadio = new JRadioButton(FxToolsBundle.message("generate.fx.property.access.standard"), true);
        styleableRadio = new JRadioButton(FxToolsBundle.message("generate.fx.property.access.styleable"), false);
        accessModeGroup.add(readonlyRadio);
        accessModeGroup.add(standardRadio);
        accessModeGroup.add(styleableRadio);
        lazyCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.lazy"), false);
        provideDefaultCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.provide.default"), false);
        constantCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.constant"), false);

        setTitle(FxToolsBundle.message("generate.fx.property.title"));
        init();
        initValidation();

        DocumentAdapter docListener = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updatePreview();
            }
        };
        nameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                if (autoUpdateCssName && styleableRadio.isSelected()) {
                    String name = nameField.getText().trim();
                    cssNameField.setText(name.isEmpty() ? "" : FxNamingUtil.toFxKebabCase(name));
                    SwingUtilities.invokeLater(cssNameField::selectAll);
                }
                updatePreview();
            }
        });
        defaultValueField.getDocument().addDocumentListener(docListener);
        genericField.getDocument().addDocumentListener(docListener);
        genericKeyField.getDocument().addDocumentListener(docListener);
        genericValueField.getDocument().addDocumentListener(docListener);

        defaultValueCard.add(defaultValueField, "text");
        defaultValueCard.add(booleanDefaultSelector, "boolean");
        booleanDefaultSelector.addChangeListener(this::updatePreview);
        cssNameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                autoUpdateCssName = false;
                updatePreview();
            }
        });
        converterModeCombo.addActionListener(e -> updatePreview());

        typeCombo.addActionListener(e -> {
            FxPropertyType current = getSelectedType();
            // Type-driven UX: changing the property type always refills the default value
            // with the new type's smart default. Custom values typed for one type are usually
            // invalid for another, so platform convention favours type-driven over input-preservation.
            defaultValueField.setText(current.getSmartDefault());
            if (current == FxPropertyType.BOOLEAN) {
                booleanDefaultSelector.setValue(false);
            }
            showDefaultValueComponent(current);
            updateGenericFieldsVisibility();
            updateAccessModeAvailability();
            updatePreview();
        });
        readonlyRadio.addActionListener(e -> handleAccessModeChange());
        standardRadio.addActionListener(e -> handleAccessModeChange());
        styleableRadio.addActionListener(e -> handleAccessModeChange());
        lazyCheck.addActionListener(e -> updatePreview());
        provideDefaultCheck.addActionListener(e -> {
            updateDefaultValueState();
            updatePreview();
        });
        constantCheck.addActionListener(e -> updatePreview());

        updateGenericFieldsVisibility();
        updateAccessModeAvailability();
        updateDefaultValueState();
        showDefaultValueComponent(getSelectedType());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FxPropertyType) {
                    setText(((FxPropertyType) value).getDisplayName());
                }
                return this;
            }
        });
        converterModeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CssConverterMode) {
                    setText(((CssConverterMode) value).getDisplayName());
                }
                return this;
            }
        });

        configurePreviewAreas();
        // IntelliJ ComboBox reports its own preferred width; keep it aligned with the fixed form column.
        typeCombo.setMinimumAndPreferredWidth(JBUI.scale(FIELD_COLUMN_WIDTH));
        converterModeCombo.setMinimumAndPreferredWidth(JBUI.scale(FIELD_COLUMN_WIDTH));

        genericLabel.setText(FxToolsBundle.message("generate.fx.property.generic.type"));
        genericKeyLabel.setText(FxToolsBundle.message("generate.fx.property.generic.key.type"));
        genericValueLabel.setText(FxToolsBundle.message("generate.fx.property.generic.value.type"));
        cssNameLabel.setText(FxToolsBundle.message("generate.fx.property.css.name"));
        converterModeLabel.setText(FxToolsBundle.message("generate.fx.property.css.converter"));

        JPanel root = new JPanel(new BorderLayout());
        root.setPreferredSize(JBUI.size(850, 500));

        JPanel leftColumn = new JPanel(new MigLayout(
                "wrap 1, fillx, insets 14 16 14 20, gapy 0, hidemode 3"));
        leftColumn.setPreferredSize(JBUI.size(450, 490));

        // ==================== Property section ====================
        leftColumn.add(makeSectionTitle(FxToolsBundle.message("generate.fx.property.section.property")), "growx");
        JPanel propertySection = makeSectionPanel();
        propertySection.add(new JBLabel(FxToolsBundle.message("generate.fx.property.name")));
        propertySection.add(nameField);
        propertySection.add(new JBLabel(FxToolsBundle.message("generate.fx.property.type")));
        // Wrap IntelliJ ComboBox so MigLayout treats it like the text fields in the fixed-width column.
        propertySection.add(wrapComboBox(typeCombo));
        propertySection.add(genericLabel);
        propertySection.add(genericField);
        propertySection.add(genericKeyLabel);
        propertySection.add(genericKeyField);
        propertySection.add(genericValueLabel);
        propertySection.add(genericValueField);
        propertySection.add(lazyCheck, "span 2, growx, gaptop 4");
        leftColumn.add(propertySection, "growx");
        leftColumn.add(new JSeparator(), "growx, gaptop 12, gapbottom 12");

        // ==================== Property Access section ====================
        leftColumn.add(makeSectionTitle(FxToolsBundle.message("generate.fx.property.section.access")), "growx");
        JPanel accessSection = makeSectionPanel();
        JPanel accessRadioPanel = new JPanel(new MigLayout("insets 0, gap 16, flowx"));
        accessRadioPanel.setOpaque(false);
        accessRadioPanel.add(readonlyRadio);
        accessRadioPanel.add(standardRadio);
        accessRadioPanel.add(styleableRadio);
        accessSection.add(accessRadioPanel, "span 2, growx");
        accessSection.add(cssNameLabel);
        accessSection.add(cssNameField);
        accessSection.add(converterModeLabel);
        accessSection.add(wrapComboBox(converterModeCombo));
        leftColumn.add(accessSection, "growx");
        leftColumn.add(new JSeparator(), "growx, gaptop 12, gapbottom 12");

        // ==================== Default Value section ====================
        leftColumn.add(makeSectionTitle(FxToolsBundle.message("generate.fx.property.section.default")), "growx");
        JPanel defaultSection = makeSectionPanel();
        defaultSection.add(provideDefaultCheck, "span 2, growx");

        JPanel defaultIndented = new JPanel(new MigLayout(
                "wrap 2, insets 0, gapy 6, hidemode 3",
                "[" + LABEL_COLUMN_WIDTH + "!,left]10[" + INDENTED_FIELD_COLUMN_WIDTH + "!,fill]"));
        defaultIndented.setOpaque(false);
        defaultIndented.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0,
                        new JBColor(new Color(0xB8B8B8), new Color(0x5A5D63))),
                JBUI.Borders.emptyLeft(12)));
        defaultIndented.add(new JBLabel(FxToolsBundle.message("generate.fx.property.value.type")));
        defaultIndented.add(valueTypeLabel);
        defaultIndented.add(new JBLabel(FxToolsBundle.message("generate.fx.property.default.value")));
        defaultValueCard.setOpaque(false);
        // CardLayout reports a short preferred height for the text field card; keep it aligned with other fields.
        defaultIndented.add(defaultValueCard, "height " + DEFAULT_FIELD_HEIGHT + "!");
        defaultSection.add(defaultIndented, "span 2, gapleft 20");

        defaultSection.add(constantCheck, "span 2, growx");
        leftColumn.add(defaultSection, "growx");

        root.add(leftColumn, BorderLayout.WEST);
        root.add(createPreviewPanel(), BorderLayout.CENTER);

        return root;
    }

    @NotNull
    private JBLabel makeSectionTitle(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        label.setBorder(JBUI.Borders.empty(0, 2, 4, 2));
        return label;
    }

    @NotNull
    private JPanel makeSectionPanel() {
        JPanel panel = new JPanel(new MigLayout(
                "wrap 2, fillx, insets 4 14 4 14, gapy 6, hidemode 3",
                "[" + LABEL_COLUMN_WIDTH + "!,left]10[" + FIELD_COLUMN_WIDTH + "!,fill]"));
        panel.setOpaque(false);
        return panel;
    }

    @NotNull
    private JPanel wrapComboBox(@NotNull JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    private void configurePreviewAreas() {
        Font previewFont = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        Color previewBackground = colorOrDefault("TextArea.background", JBColor.WHITE);
        Color gutterBackground = colorOrDefault("Panel.background", JBColor.PanelBackground);
        Color disabledForeground = colorOrDefault("Label.disabledForeground", JBColor.GRAY);

        previewArea.setEditable(false);
        previewArea.setFont(previewFont);
        previewArea.setRows(24);
        previewArea.setTabSize(4);
        previewArea.setLineWrap(false);
        previewArea.setBackground(previewBackground);
        previewArea.setBorder(JBUI.Borders.empty(8, 10));

        lineNumberArea.setEditable(false);
        lineNumberArea.setFont(previewFont);
        lineNumberArea.setBackground(gutterBackground);
        lineNumberArea.setForeground(disabledForeground);
        lineNumberArea.setBorder(JBUI.Borders.empty(8, 10, 8, 8));
        lineNumberArea.setFocusable(false);
    }

    @NotNull
    private JPanel createPreviewPanel() {
        JPanel previewPanel = new JPanel(new BorderLayout());
        Color dividerColor = new JBColor(new Color(0xE5E5E5), new Color(0x393B40));
        previewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, dividerColor),
                JBUI.Borders.empty(0, 14, 0, 0)));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(8, 12, 6, 12));
        header.add(new JBLabel(FxToolsBundle.message("generate.fx.property.preview")), BorderLayout.WEST);

        JBLabel liveLabel = new JBLabel(FxToolsBundle.message("generate.fx.property.preview.live"));
        liveLabel.setForeground(new JBColor(new Color(0x5E9F65), new Color(0x6CB76C)));
        header.add(liveLabel, BorderLayout.EAST);
        previewPanel.add(header, BorderLayout.NORTH);

        JBScrollPane scrollPane = new JBScrollPane(previewArea);
        scrollPane.setRowHeaderView(lineNumberArea);
        previewPanel.add(scrollPane, BorderLayout.CENTER);
        return previewPanel;
    }

    @NotNull
    private Color colorOrDefault(@NotNull String key, @NotNull Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
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
                    FxToolsBundle.message("generate.fx.property.error.empty.name"), nameField);
        }
        if (!PsiNameHelper.getInstance(project).isIdentifier(name)) {
            return new ValidationInfo(
                    FxToolsBundle.message("generate.fx.property.error.invalid.name"), nameField);
        }
        return null;
    }

    /**
     * Returns the generated code text.
     */
    @NotNull
    public String getGeneratedCode() {
        return generatedCode;
    }

    /**
     * Returns whether the generated property uses JavaFX CSS metadata.
     */
    public boolean isStyleableGenerated() {
        return styleableRadio.isSelected() && getSelectedType().isStyleableSupported();
    }

    /**
     * Returns the generated property name.
     */
    @NotNull
    public String getPropertyName() {
        return nameField.getText().trim();
    }

    /**
     * Returns the generated property type metadata.
     */
    @NotNull
    public FxPropertyType getPropertyType() {
        return getSelectedType();
    }

    /**
     * Returns the CSS property name used by the generated styleable property.
     */
    @NotNull
    public String getCssName() {
        String cssName = cssNameField.getText().trim();
        if (!cssName.isEmpty()) {
            return cssName;
        }
        return FxNamingUtil.toFxKebabCase(getPropertyName());
    }

    /**
     * Returns the default expression used by generated CssMetaData, or an empty string.
     */
    @NotNull
    public String getCssDefaultReference() {
        String defaultVal = computeDefaultValue();
        if (defaultVal.isEmpty()) {
            return "";
        }
        if (constantCheck.isSelected()) {
            return FxNamingUtil.toUpperSnakeCase("DEFAULT_" + getPropertyName());
        }
        return defaultVal;
    }

    @NotNull
    private String computeDefaultValue() {
        if (!provideDefaultCheck.isSelected()) {
            return "";
        }
        FxPropertyType type = getSelectedType();
        if (type == FxPropertyType.BOOLEAN) {
            return booleanDefaultSelector.getValue() ? "true" : "false";
        }
        String raw = defaultValueField.getText().trim();
        if (raw.isEmpty()) {
            return "";
        }
        return type.normalizeDefaultLiteral(normalizeDefaultValue(raw, type));
    }

    private void showDefaultValueComponent(@NotNull FxPropertyType type) {
        CardLayout layout = (CardLayout) defaultValueCard.getLayout();
        layout.show(defaultValueCard, type == FxPropertyType.BOOLEAN ? "boolean" : "text");
    }

    /**
     * Returns the descriptor used by the StyleableProperties integrator.
     */
    @NotNull
    public StyleablePropertyDescriptor getStyleableDescriptor() {
        FxPropertyType type = getPropertyType();
        String converterExpression = type.getConverterExpression();
        String propertyName = getPropertyName();
        String genericParam = genericField.getText().trim();
        String cssValueType = type.getCssValueType(genericParam);
        if (type == FxPropertyType.OBJECT) {
            if (converterModeCombo.getSelectedItem() == CssConverterMode.CUSTOM) {
                converterExpression = FxPropertyType.TODO_STYLE_CONVERTER_EXPRESSION;
            } else {
                StyleableConverterResolver.Result result =
                        StyleableConverterResolver.resolveObjectProperty(genericParam, project);
                cssValueType = result.cssValueType();
                converterExpression = result.converterExpression();
            }
        } else if (converterExpression == null) {
            converterExpression = FxPropertyType.TODO_STYLE_CONVERTER_EXPRESSION;
        }
        return new StyleablePropertyDescriptor(
                propertyName,
                cssValueType,
                converterExpression,
                getCssName(),
                getCssDefaultReference(),
                FxNamingUtil.toUpperSnakeCase(propertyName));
    }

    private void updateGenericFieldsVisibility() {
        FxPropertyType type = getSelectedType();
        boolean singleParam = type.isNeedsTypeParam();
        boolean twoParams = type.isNeedsTwoTypeParams();

        genericLabel.setVisible(singleParam);
        genericField.setVisible(singleParam);
        genericKeyLabel.setVisible(twoParams);
        genericKeyField.setVisible(twoParams);
        genericValueLabel.setVisible(twoParams);
        genericValueField.setVisible(twoParams);

        if (singleParam && genericField.getText().trim().isEmpty()) {
            genericField.setText("Object");
        }
        if (twoParams) {
            if (genericKeyField.getText().trim().isEmpty()) {
                genericKeyField.setText("Object");
            }
            if (genericValueField.getText().trim().isEmpty()) {
                genericValueField.setText("Object");
            }
        }

        String gp = genericField.getText().trim();
        String gk = genericKeyField.getText().trim();
        String gv = genericValueField.getText().trim();
        valueTypeLabel.setText(type.getEffectiveValueType(
                singleParam ? (gp.isEmpty() ? "Object" : gp) : (twoParams ? (gk.isEmpty() ? "Object" : gk) : ""),
                twoParams ? (gv.isEmpty() ? "Object" : gv) : ""));
    }

    private void updateDefaultValueState() {
        boolean enabled = provideDefaultCheck.isSelected();
        defaultValueField.setEnabled(enabled);
        booleanDefaultSelector.setEnabled(enabled);
        constantCheck.setEnabled(enabled);
        if (enabled && defaultValueField.getText().trim().isEmpty()) {
            defaultValueField.setText(getSelectedType().getSmartDefault());
        }
        if (!enabled) {
            constantCheck.setSelected(false);
        }
    }

    private void updateAccessModeAvailability() {
        FxPropertyType type = getSelectedType();
        boolean styleableSupported = type.isStyleableSupported();
        if (!styleableSupported && styleableRadio.isSelected()) {
            standardRadio.setSelected(true);
        }
        styleableRadio.setEnabled(styleableSupported);

        boolean cssVisible = styleableRadio.isSelected();
        cssNameLabel.setVisible(cssVisible);
        cssNameField.setVisible(cssVisible);
        boolean converterModeVisible = cssVisible && type == FxPropertyType.OBJECT;
        converterModeLabel.setVisible(converterModeVisible);
        converterModeCombo.setVisible(converterModeVisible);
    }

    private void handleAccessModeChange() {
        updateAccessModeAvailability();
        if (styleableRadio.isSelected()) {
            autoUpdateCssName = true;
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                cssNameField.setText(FxNamingUtil.toFxKebabCase(name));
                SwingUtilities.invokeLater(cssNameField::selectAll);
            }
        }
        updatePreview();
    }

    private void updatePreview() {
        updateGenericFieldsVisibility();
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            previewArea.setText("");
            updateLineNumbers("");
            generatedCode = "";
            return;
        }
        generatedCode = generatePropertyCode(name);
        String previewCode = generatePreviewCode(generatedCode);
        previewArea.setText(previewCode);
        previewArea.setCaretPosition(0);
        updateLineNumbers(previewCode);
    }

    private void updateLineNumbers(@NotNull String text) {
        int lines = text.isEmpty() ? 1 : text.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            if (i > 1) {
                sb.append('\n');
            }
            sb.append(i);
        }
        lineNumberArea.setText(sb.toString());
    }

    @NotNull
    private String generatePreviewCode(@NotNull String propertyCode) {
        if (!isStyleableGenerated()) {
            return propertyCode;
        }

        StringBuilder sb = new StringBuilder(propertyCode);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(StyleablePropertiesIntegrator.generatePreviewCode(
                className,
                superClassName,
                useControlCssMetaData,
                hasStyleableProperties,
                getStyleableDescriptor()));
        return sb.toString();
    }

    @NotNull
    private String generatePropertyCode(@NotNull String propName) {
        FxPropertyType type = getSelectedType();
        boolean readonly = readonlyRadio.isSelected();
        boolean lazy = lazyCheck.isSelected();
        boolean styleable = styleableRadio.isSelected() && type.isStyleableSupported();
        String defaultVal = computeDefaultValue();
        String gp = genericField.getText().trim();
        String gk = genericKeyField.getText().trim();
        String gv = genericValueField.getText().trim();

        String genericSuffix = "";
        if (type.isNeedsTypeParam()) {
            genericSuffix = "<" + (gp.isEmpty() ? "Object" : gp) + ">";
        } else if (type.isNeedsTwoTypeParams()) {
            genericSuffix = "<" + (gk.isEmpty() ? "Object" : gk) + ", " + (gv.isEmpty() ? "Object" : gv) + ">";
        }

        String valueType = type.getEffectiveValueType(
                type.isNeedsTypeParam() ? (gp.isEmpty() ? "Object" : gp) : (type.isNeedsTwoTypeParams() ? (gk.isEmpty() ? "Object" : gk) : ""),
                type.isNeedsTwoTypeParams() ? (gv.isEmpty() ? "Object" : gv) : "");

        String capName = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        String getterName = type.getGetterPrefix() + capName;
        String defaultValueLiteral = getDefaultValueLiteral(type, defaultVal);

        boolean useConstant = constantCheck.isSelected() && !defaultVal.isEmpty();
        String constantName = useConstant ? FxNamingUtil.toUpperSnakeCase("DEFAULT_" + propName) : null;
        String defaultRef = useConstant ? constantName : defaultVal;
        String defaultLiteralRef = useConstant ? constantName : defaultValueLiteral;

        // Determine field and impl types
        String fieldType;
        String implClass;
        if (styleable) {
            String simpleStyleableName = type.getSimpleStyleablePropertyName();
            fieldType = type.getPropertyFqn() + genericSuffix;
            implClass = "javafx.css." + simpleStyleableName + (genericSuffix.isEmpty() ? "" : "<>");
        } else if (readonly) {
            fieldType = type.getReadOnlyWrapperFqn() + genericSuffix;
            implClass = type.getReadOnlyWrapperFqn() + (genericSuffix.isEmpty() ? "" : "<>");
        } else {
            fieldType = type.getPropertyFqn() + genericSuffix;
            implClass = type.getSimpleFqn() + (genericSuffix.isEmpty() ? "" : "<>");
        }

        StringBuilder sb = new StringBuilder();

        // Constant
        if (useConstant) {
            sb.append("private static final ").append(valueType).append(" ").append(constantName)
                    .append(" = ").append(defaultVal).append(";\n\n");
        }

        if (styleable) {
            return generateStyleableCode(sb, propName, capName, getterName, readonly, lazy,
                    fieldType, implClass, valueType, defaultRef, defaultLiteralRef);
        }

        if (lazy) {
            return generateLazyCode(sb, propName, capName, getterName, type, readonly,
                    fieldType, implClass, valueType, genericSuffix, defaultRef, defaultLiteralRef);
        }

        return generateEagerCode(sb, propName, capName, getterName, type, readonly,
                fieldType, implClass, valueType, genericSuffix, defaultRef);
    }

    @NotNull
    private String generateEagerCode(@NotNull StringBuilder sb,
                                     @NotNull String propName, @NotNull String capName,
                                     @NotNull String getterName, @NotNull FxPropertyType type,
                                     boolean readonly,
                                     @NotNull String fieldType, @NotNull String implClass,
                                     @NotNull String valueType, @NotNull String genericSuffix,
                                     @NotNull String defaultRef) {
        sb.append("private final ").append(fieldType).append(" ").append(propName);
        sb.append(" = new ").append(implClass).append("(this, \"").append(propName).append("\"");
        if (!defaultRef.isEmpty()) {
            sb.append(", ").append(defaultRef);
        }
        sb.append(");\n\n");

        appendGetter(sb, propName, getterName, valueType, false, null);
        if (!readonly) {
            appendSetter(sb, propName, capName, valueType, false);
        }
        appendPropertyAccessor(sb, propName, type, fieldType, genericSuffix, readonly);

        return sb.toString();
    }

    @NotNull
    private String generateLazyCode(@NotNull StringBuilder sb,
                                    @NotNull String propName, @NotNull String capName,
                                    @NotNull String getterName, @NotNull FxPropertyType type,
                                    boolean readonly,
                                    @NotNull String fieldType, @NotNull String implClass,
                                    @NotNull String valueType, @NotNull String genericSuffix,
                                    @NotNull String defaultRef, @NotNull String defaultLiteralRef) {
        sb.append("private ").append(fieldType).append(" ").append(propName).append(";\n");
        sb.append("\n");

        appendGetter(sb, propName, getterName, valueType, true, defaultLiteralRef);
        if (!readonly) {
            appendSetter(sb, propName, capName, valueType, true);
        }

        if (readonly) {
            String roType = type.getReadOnlyPropertyFqn() + genericSuffix;
            sb.append("public final ").append(roType).append(" ").append(propName).append("Property() {\n");
        } else {
            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
        }
        sb.append("    if (").append(propName).append(" == null) {\n");
        sb.append("        ").append(propName).append(" = new ").append(implClass)
                .append("(this, \"").append(propName).append("\"");
        if (!defaultRef.isEmpty()) {
            sb.append(", ").append(defaultRef);
        }
        sb.append(");\n");
        sb.append("    }\n");
        sb.append("    return ").append(readonly ? propName + ".getReadOnlyProperty()" : propName).append(";\n");
        sb.append("}\n");

        return sb.toString();
    }

    @NotNull
    private String generateStyleableCode(@NotNull StringBuilder sb,
                                         @NotNull String propName, @NotNull String capName,
                                         @NotNull String getterName,
                                         boolean readonly, boolean lazy,
                                         @NotNull String fieldType, @NotNull String implClass,
                                         @NotNull String valueType, @NotNull String defaultRef,
                                         @NotNull String defaultLiteralRef) {
        String metaName = FxNamingUtil.toUpperSnakeCase(propName);

        if (lazy) {
            sb.append("private ").append(fieldType).append(" ").append(propName).append(";\n");
            sb.append("\n");
            appendGetter(sb, propName, getterName, valueType, true, defaultLiteralRef);
            if (!readonly) {
                appendSetter(sb, propName, capName, valueType, true);
            }

            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
            sb.append("    if (").append(propName).append(" == null) {\n");
            sb.append("        ").append(propName).append(" = new ").append(implClass)
                    .append("(StyleableProperties.").append(metaName)
                    .append(", this, \"").append(propName).append("\"");
            if (!defaultRef.isEmpty()) {
                sb.append(", ").append(defaultRef);
            }
            sb.append(");\n");
            sb.append("    }\n");
            sb.append("    return ").append(propName).append(";\n");
            sb.append("}\n");
        } else {
            sb.append("private final ").append(fieldType).append(" ").append(propName)
                    .append(" = new ").append(implClass)
                    .append("(StyleableProperties.").append(metaName)
                    .append(", this, \"").append(propName).append("\"");
            if (!defaultRef.isEmpty()) {
                sb.append(", ").append(defaultRef);
            }
            sb.append(");\n\n");

            appendGetter(sb, propName, getterName, valueType, false, null);
            if (!readonly) {
                appendSetter(sb, propName, capName, valueType, false);
            }

            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
            sb.append("    return ").append(propName).append(";\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    private void appendGetter(@NotNull StringBuilder sb, @NotNull String propName,
                              @NotNull String getterName, @NotNull String valueType,
                              boolean lazy, @Nullable String defaultLiteral) {
        sb.append("public final ").append(valueType).append(" ").append(getterName).append("() {\n");
        if (lazy) {
            sb.append("    return ").append(propName).append(" == null ? ")
                    .append(defaultLiteral).append(" : ").append(propName).append(".get();\n");
        } else {
            sb.append("    return ").append(propName).append(".get();\n");
        }
        sb.append("}\n\n");
    }

    private void appendSetter(@NotNull StringBuilder sb, @NotNull String propName,
                              @NotNull String capName, @NotNull String valueType,
                              boolean lazy) {
        sb.append("public final void set").append(capName).append("(").append(valueType).append(" value) {\n");
        if (lazy) {
            sb.append("    ").append(propName).append("Property().set(value);\n");
        } else {
            sb.append("    ").append(propName).append(".set(value);\n");
        }
        sb.append("}\n\n");
    }

    private void appendPropertyAccessor(@NotNull StringBuilder sb, @NotNull String propName,
                                        @NotNull FxPropertyType type, @NotNull String fieldType,
                                        @NotNull String genericSuffix, boolean readonly) {
        if (readonly) {
            String roType = type.getReadOnlyPropertyFqn() + genericSuffix;
            sb.append("public final ").append(roType).append(" ").append(propName).append("Property() {\n");
            sb.append("    return ").append(propName).append(".getReadOnlyProperty();\n");
        } else {
            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
            sb.append("    return ").append(propName).append(";\n");
        }
        sb.append("}\n");
    }

    @NotNull
    private String normalizeDefaultValue(@NotNull String raw, @NotNull FxPropertyType type) {
        if (raw.isEmpty()) {
            return raw;
        }
        if (type == FxPropertyType.STRING) {
            // null is a valid Java expression for any reference type — don't wrap as a literal.
            if ("null".equals(raw)) {
                return raw;
            }
            if (!raw.startsWith("\"")) {
                return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return raw;
    }

    @NotNull
    private String getDefaultValueLiteral(@NotNull FxPropertyType type, @NotNull String userDefault) {
        if (!userDefault.isEmpty()) {
            return userDefault;
        }
        switch (type) {
            case INTEGER: return "0";
            case LONG: return "0L";
            case FLOAT: return "0.0f";
            case DOUBLE: return "0.0";
            case BOOLEAN: return "false";
            default: return "null";
        }
    }

    @NotNull
    private FxPropertyType getSelectedType() {
        Object selected = typeCombo.getSelectedItem();
        return selected instanceof FxPropertyType ? (FxPropertyType) selected : FxPropertyType.STRING;
    }

    private enum CssConverterMode {
        AUTO("generate.fx.property.css.converter.auto"),
        CUSTOM("generate.fx.property.css.converter.custom");

        private final String messageKey;

        CssConverterMode(@NotNull String messageKey) {
            this.messageKey = messageKey;
        }

        @NotNull
        String getDisplayName() {
            return FxToolsBundle.message(messageKey);
        }
    }

    /**
     * Two-segment toggle for boolean defaults: a true / false pill with selected-side
     * tinted background and colored dot, mimicking the macOS-style segmented control.
     */
    private static class BooleanDefaultSelector extends JPanel {

        private static final JBColor GREEN_TINT = new JBColor(new Color(0xE3F4E5), new Color(0x2C3D2D));
        private static final JBColor RED_TINT = new JBColor(new Color(0xF8E2E0), new Color(0x3D2A28));
        private static final JBColor GREEN_DOT = new JBColor(new Color(0x49A85B), new Color(0x6CB76C));
        private static final JBColor RED_DOT = new JBColor(new Color(0xC15B5C), new Color(0xD46668));
        private static final JBColor INACTIVE_DOT = new JBColor(new Color(0xB0B0B0), new Color(0x6E6E6E));

        private boolean value = false;
        private final List<Runnable> changeListeners = new ArrayList<>();

        BooleanDefaultSelector() {
            super(new GridLayout(1, 2, 0, 0));
            setBorder(BorderFactory.createLineBorder(JBColor.border(), 1, true));
            add(new Segment(true));
            add(new Segment(false));
        }

        boolean getValue() {
            return value;
        }

        void setValue(boolean v) {
            if (this.value != v) {
                this.value = v;
                repaint();
                changeListeners.forEach(Runnable::run);
            }
        }

        void addChangeListener(@NotNull Runnable listener) {
            changeListeners.add(listener);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, JBUI.scale(28)));
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (Component c : getComponents()) {
                c.setEnabled(enabled);
            }
            repaint();
        }

        private class Segment extends JComponent {

            private final boolean represents;

            Segment(boolean represents) {
                this.represents = represents;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (isEnabled()) {
                            setValue(represents);
                        }
                    }
                });
            }

            @Override
            public Dimension getPreferredSize() {
                return JBUI.size(80, 28);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                boolean selected = (value == represents);
                boolean enabled = isEnabled();

                if (selected && enabled) {
                    g2.setColor(represents ? GREEN_TINT : RED_TINT);
                    g2.fillRect(0, 0, w, h);
                }

                int dotSize = 8;
                int dotGap = 6;
                String text = represents ? "true" : "false";
                g2.setFont(getFont() != null ? getFont() : UIUtil.getLabelFont());
                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(text);
                int totalW = dotSize + dotGap + textW;
                int dotX = (w - totalW) / 2;
                int dotY = (h - dotSize) / 2;
                Color dotColor;
                if (!enabled || !selected) {
                    dotColor = INACTIVE_DOT;
                } else if (represents) {
                    dotColor = GREEN_DOT;
                } else {
                    dotColor = RED_DOT;
                }
                g2.setColor(dotColor);
                g2.fillOval(dotX, dotY, dotSize, dotSize);

                g2.setColor(enabled ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
                int textX = dotX + dotSize + dotGap;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, textX, textY);

                if (represents) {
                    g2.setColor(JBColor.border());
                    g2.drawLine(w - 1, 0, w - 1, h);
                }

                g2.dispose();
            }
        }
    }
}
