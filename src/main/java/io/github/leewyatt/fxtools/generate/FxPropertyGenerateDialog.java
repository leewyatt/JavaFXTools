package io.github.leewyatt.fxtools.generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertiesIntegrator;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertyDescriptor;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Dialog for generating JavaFX Property fields with getter/setter/property methods.
 */
public class FxPropertyGenerateDialog extends DialogWrapper {

    private final Project project;
    private final String className;
    private final String superClassName;
    private final boolean useControlCssMetaData;
    private final boolean hasStyleableProperties;
    private final JBTextField nameField = new JBTextField();
    private final JComboBox<FxPropertyType> typeCombo = new JComboBox<>(FxPropertyType.values());
    private final JBLabel valueTypeLabel = new JBLabel();
    private final JBTextField defaultValueField = new JBTextField();
    private final JBTextField genericField = new JBTextField();
    private final JBTextField genericKeyField = new JBTextField();
    private final JBTextField genericValueField = new JBTextField();
    private final JBLabel genericLabel = new JBLabel();
    private final JBLabel genericKeyLabel = new JBLabel();
    private final JBLabel genericValueLabel = new JBLabel();
    private final JCheckBox readonlyCheck;
    private final JCheckBox lazyCheck;
    private final JCheckBox constantCheck;
    private final JCheckBox styleableCheck;
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
        readonlyCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.readonly"), false);
        lazyCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.lazy"), false);
        constantCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.constant"), false);
        styleableCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.styleable"), false);

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
                if (autoUpdateCssName && styleableCheck.isSelected()) {
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
        cssNameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                autoUpdateCssName = false;
                updatePreview();
            }
        });

        typeCombo.addActionListener(e -> {
            updateGenericFieldsVisibility();
            updateStyleableVisibility();
            updatePreview();
        });
        readonlyCheck.addActionListener(e -> updatePreview());
        lazyCheck.addActionListener(e -> updatePreview());
        constantCheck.addActionListener(e -> updatePreview());
        styleableCheck.addActionListener(e -> {
            updateStyleableVisibility();
            if (styleableCheck.isSelected()) {
                autoUpdateCssName = true;
                String name = nameField.getText().trim();
                if (!name.isEmpty()) {
                    cssNameField.setText(FxNamingUtil.toFxKebabCase(name));
                    SwingUtilities.invokeLater(cssNameField::selectAll);
                }
            }
            updatePreview();
        });

        updateGenericFieldsVisibility();
        updateStyleableVisibility();
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

        configurePreviewAreas();

        genericLabel.setText(FxToolsBundle.message("generate.fx.property.generic.type"));
        genericKeyLabel.setText(FxToolsBundle.message("generate.fx.property.generic.key.type"));
        genericValueLabel.setText(FxToolsBundle.message("generate.fx.property.generic.value.type"));
        cssNameLabel.setText(FxToolsBundle.message("generate.fx.property.css.name"));

        JPanel root = new JPanel(new BorderLayout());
        root.setPreferredSize(new Dimension(960, 560));

        JPanel propertyPanel = new JPanel(new MigLayout(
                "wrap 2, fillx, insets 14 16 14 16, gapy 8",
                "[right]12[grow,fill]"));
        propertyPanel.setPreferredSize(new Dimension(420, 520));

        addSectionTitle(propertyPanel, FxToolsBundle.message("generate.fx.property.section.property"));
        propertyPanel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.name")));
        propertyPanel.add(nameField);

        propertyPanel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.type")));
        propertyPanel.add(typeCombo);

        propertyPanel.add(genericLabel);
        propertyPanel.add(genericField);
        propertyPanel.add(genericKeyLabel);
        propertyPanel.add(genericKeyField);
        propertyPanel.add(genericValueLabel);
        propertyPanel.add(genericValueField);

        propertyPanel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.value.type")));
        propertyPanel.add(valueTypeLabel);

        propertyPanel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.default.value")));
        propertyPanel.add(defaultValueField);

        addSectionTitle(propertyPanel, FxToolsBundle.message("generate.fx.property.section.options"));
        propertyPanel.add(readonlyCheck, "span 2, growx");
        propertyPanel.add(lazyCheck, "span 2, growx");
        propertyPanel.add(constantCheck, "span 2, growx");

        addSectionTitle(propertyPanel, FxToolsBundle.message("generate.fx.property.section.css"));
        propertyPanel.add(styleableCheck, "span 2, growx");
        propertyPanel.add(cssNameLabel);
        propertyPanel.add(cssNameField);

        root.add(propertyPanel, BorderLayout.WEST);
        root.add(createPreviewPanel(), BorderLayout.CENTER);

        return root;
    }

    private void configurePreviewAreas() {
        Font previewFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        Color previewBackground = colorOrDefault("TextArea.background", JBColor.WHITE);
        Color gutterBackground = colorOrDefault("Panel.background", JBColor.PanelBackground);
        Color disabledForeground = colorOrDefault("Label.disabledForeground", JBColor.GRAY);

        previewArea.setEditable(false);
        previewArea.setFont(previewFont);
        previewArea.setRows(24);
        previewArea.setTabSize(4);
        previewArea.setLineWrap(false);
        previewArea.setBackground(previewBackground);
        previewArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        lineNumberArea.setEditable(false);
        lineNumberArea.setFont(previewFont);
        lineNumberArea.setBackground(gutterBackground);
        lineNumberArea.setForeground(disabledForeground);
        lineNumberArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
        lineNumberArea.setFocusable(false);
    }

    @NotNull
    private JPanel createPreviewPanel() {
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createMatteBorder(
                0, 1, 0, 0, colorOrDefault("Separator.foreground", JBColor.border())));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 12));
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

    private void addSectionTitle(@NotNull JPanel panel, @NotNull String title) {
        JBLabel label = new JBLabel(title);
        label.setForeground(colorOrDefault("Label.disabledForeground", JBColor.GRAY));
        panel.add(label, "span 2, split 2, growx, gaptop 4");
        panel.add(new JSeparator(), "growx, gapleft 8");
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
        return styleableCheck.isSelected() && getSelectedType().isStyleableSupported();
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
        String defaultVal = normalizeDefaultValue(defaultValueField.getText().trim(), getSelectedType());
        if (defaultVal.isEmpty()) {
            return "";
        }
        if (constantCheck.isSelected()) {
            return FxNamingUtil.toUpperSnakeCase("DEFAULT_" + getPropertyName());
        }
        return defaultVal;
    }

    /**
     * Returns the descriptor used by the StyleableProperties integrator.
     */
    @NotNull
    public StyleablePropertyDescriptor getStyleableDescriptor() {
        FxPropertyType type = getPropertyType();
        String converterExpression = type.getConverterExpression();
        if (converterExpression == null) {
            converterExpression = "/* TODO: provide StyleConverter */";
        }
        String propertyName = getPropertyName();
        return new StyleablePropertyDescriptor(
                propertyName,
                type.getCssValueType(),
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

        String gp = genericField.getText().trim();
        String gk = genericKeyField.getText().trim();
        String gv = genericValueField.getText().trim();
        valueTypeLabel.setText(type.getEffectiveValueType(
                singleParam ? (gp.isEmpty() ? "Object" : gp) : (twoParams ? (gk.isEmpty() ? "Object" : gk) : ""),
                twoParams ? (gv.isEmpty() ? "Object" : gv) : ""));
    }

    private void updateStyleableVisibility() {
        FxPropertyType type = getSelectedType();
        boolean supported = type.isStyleableSupported();
        if (!supported) {
            styleableCheck.setSelected(false);
        }
        styleableCheck.setEnabled(supported);
        boolean enabled = styleableCheck.isSelected();
        cssNameLabel.setEnabled(enabled);
        cssNameField.setEnabled(enabled);
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
        boolean readonly = readonlyCheck.isSelected();
        boolean lazy = lazyCheck.isSelected();
        boolean styleable = styleableCheck.isSelected() && type.isStyleableSupported();
        String defaultVal = normalizeDefaultValue(defaultValueField.getText().trim(), type);
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
}
