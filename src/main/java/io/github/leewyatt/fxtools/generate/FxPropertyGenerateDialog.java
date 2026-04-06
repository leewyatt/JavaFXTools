package io.github.leewyatt.fxtools.generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiNameHelper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import io.github.leewyatt.fxtools.FxToolsBundle;
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
    private final JCheckBox javadocCheck;
    private final JCheckBox readonlyCheck;
    private final JCheckBox lazyCheck;
    private final JCheckBox constantCheck;
    private final JCheckBox styleableCheck;
    private final JBLabel cssNameLabel = new JBLabel();
    private final JBTextField cssNameField = new JBTextField();
    private final JTextArea previewArea = new JTextArea();
    private String generatedCode = "";
    private boolean autoUpdateCssName = true;

    /**
     * Creates the JavaFX Property generation dialog.
     */
    public FxPropertyGenerateDialog(@NotNull Project project, @NotNull String className) {
        super(project, true);
        this.project = project;
        this.className = className;
        javadocCheck = new JCheckBox(FxToolsBundle.message("generate.fx.property.generate.javadoc"), true);
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
                    cssNameField.setText(name.isEmpty() ? "" : toKebabCase(name));
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

        ActionListener updateAction = e -> {
            updateGenericFieldsVisibility();
            updatePreview();
        };
        typeCombo.addActionListener(e -> {
            updateGenericFieldsVisibility();
            updateStyleableVisibility();
            updatePreview();
        });
        javadocCheck.addActionListener(e -> updatePreview());
        readonlyCheck.addActionListener(e -> updatePreview());
        lazyCheck.addActionListener(e -> updatePreview());
        constantCheck.addActionListener(e -> updatePreview());
        styleableCheck.addActionListener(e -> {
            updateStyleableVisibility();
            if (styleableCheck.isSelected()) {
                autoUpdateCssName = true;
                String name = nameField.getText().trim();
                if (!name.isEmpty()) {
                    cssNameField.setText(toKebabCase(name));
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

        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setRows(16);
        previewArea.setBackground(UIManager.getColor("TextField.inactiveBackground"));

        genericLabel.setText(FxToolsBundle.message("generate.fx.property.generic.type"));
        genericKeyLabel.setText(FxToolsBundle.message("generate.fx.property.generic.key.type"));
        genericValueLabel.setText(FxToolsBundle.message("generate.fx.property.generic.value.type"));
        cssNameLabel.setText(FxToolsBundle.message("generate.fx.property.css.name"));

        JPanel panel = new JPanel(new MigLayout("wrap 2, fillx, insets dialog", "[right]rel[grow,fill]"));

        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.name")));
        panel.add(nameField, "wmin 300");

        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.type")));
        panel.add(typeCombo);

        panel.add(genericLabel);
        panel.add(genericField);
        panel.add(genericKeyLabel);
        panel.add(genericKeyField);
        panel.add(genericValueLabel);
        panel.add(genericValueField);

        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.value.type")));
        panel.add(valueTypeLabel);

        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.default.value")));
        panel.add(defaultValueField);

        panel.add(new JSeparator(), "span 2, growx, gaptop 5, gapbottom 5");
        panel.add(javadocCheck, "span 2");
        panel.add(readonlyCheck, "span 2");
        panel.add(lazyCheck, "span 2");
        panel.add(constantCheck, "span 2");
        panel.add(styleableCheck, "span 2");
        panel.add(cssNameLabel, "hidemode 3");
        panel.add(cssNameField, "hidemode 3");

        panel.add(new JSeparator(), "span 2, growx, gaptop 5, gapbottom 5");
        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.preview")), "span 2");
        panel.add(new JBScrollPane(previewArea), "span 2, growx, h 200::");

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
        boolean show = styleableCheck.isSelected();
        cssNameLabel.setVisible(show);
        cssNameField.setVisible(show);
    }

    private void updatePreview() {
        updateGenericFieldsVisibility();
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            previewArea.setText("");
            generatedCode = "";
            return;
        }
        generatedCode = generateCode(name);
        previewArea.setText(generatedCode);
    }

    @NotNull
    private String generateCode(@NotNull String propName) {
        FxPropertyType type = getSelectedType();
        boolean readonly = readonlyCheck.isSelected();
        boolean lazy = lazyCheck.isSelected();
        boolean javadoc = javadocCheck.isSelected();
        boolean styleable = styleableCheck.isSelected() && type.isStyleableSupported();
        String defaultVal = normalizeDefaultValue(defaultValueField.getText().trim(), type);
        String cssName = cssNameField.getText().trim();
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
        String constantName = useConstant ? toUpperSnakeCase("DEFAULT_" + propName) : null;
        String defaultRef = useConstant ? constantName : defaultVal;
        String defaultLiteralRef = useConstant ? constantName : defaultValueLiteral;

        // Determine field and impl types
        String fieldType;
        String implClass;
        if (styleable) {
            String styleableName = type.getStyleablePropertyName();
            fieldType = "javafx.css." + styleableName + genericSuffix;
            implClass = null; // styleable uses anonymous class, not Simple impl
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
            return generateStyleableCode(sb, propName, capName, getterName, type, readonly, lazy, javadoc,
                    fieldType, valueType, genericSuffix, cssName, defaultRef, defaultLiteralRef);
        }

        if (lazy) {
            return generateLazyCode(sb, propName, capName, getterName, type, readonly, javadoc,
                    fieldType, implClass, valueType, genericSuffix, defaultRef, defaultLiteralRef);
        }

        return generateEagerCode(sb, propName, capName, getterName, type, readonly, javadoc,
                fieldType, implClass, valueType, genericSuffix, defaultRef);
    }

    @NotNull
    private String generateEagerCode(@NotNull StringBuilder sb,
                                     @NotNull String propName, @NotNull String capName,
                                     @NotNull String getterName, @NotNull FxPropertyType type,
                                     boolean readonly, boolean javadoc,
                                     @NotNull String fieldType, @NotNull String implClass,
                                     @NotNull String valueType, @NotNull String genericSuffix,
                                     @NotNull String defaultRef) {
        sb.append("private final ").append(fieldType).append(" ").append(propName);
        sb.append(" = new ").append(implClass).append("(this, \"").append(propName).append("\"");
        if (!defaultRef.isEmpty()) {
            sb.append(", ").append(defaultRef);
        }
        sb.append(");\n");

        appendGetter(sb, propName, getterName, valueType, javadoc, false, null);
        if (!readonly) {
            appendSetter(sb, propName, capName, valueType, javadoc, false);
        }
        appendPropertyAccessor(sb, propName, type, fieldType, genericSuffix, javadoc, readonly, false);

        return sb.toString();
    }

    @NotNull
    private String generateLazyCode(@NotNull StringBuilder sb,
                                    @NotNull String propName, @NotNull String capName,
                                    @NotNull String getterName, @NotNull FxPropertyType type,
                                    boolean readonly, boolean javadoc,
                                    @NotNull String fieldType, @NotNull String implClass,
                                    @NotNull String valueType, @NotNull String genericSuffix,
                                    @NotNull String defaultRef, @NotNull String defaultLiteralRef) {
        sb.append("private ").append(fieldType).append(" ").append(propName).append(";\n");

        appendGetter(sb, propName, getterName, valueType, javadoc, true, defaultLiteralRef);
        if (!readonly) {
            appendSetter(sb, propName, capName, valueType, javadoc, true);
        }

        if (javadoc) {
            sb.append("\n/**\n * The ").append(propName).append(" property.\n */\n");
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
                                         @NotNull String getterName, @NotNull FxPropertyType type,
                                         boolean readonly, boolean lazy, boolean javadoc,
                                         @NotNull String fieldType, @NotNull String valueType,
                                         @NotNull String genericSuffix, @NotNull String cssName,
                                         @NotNull String defaultRef, @NotNull String defaultLiteralRef) {
        String cssValueType = type.getCssValueType();
        String metaName = toUpperSnakeCase(propName) + "_META";
        String converterExpr = type.getConverterExpression();
        if (converterExpr == null) {
            converterExpr = "/* TODO: provide StyleConverter */";
        }

        // CssMetaData static field
        sb.append("private static final javafx.css.CssMetaData<").append(className).append(", ")
                .append(cssValueType).append("> ").append(metaName).append(" =\n");
        sb.append("    new javafx.css.CssMetaData<>(\"").append(cssName).append("\",\n");
        sb.append("            ").append(converterExpr);
        if (!defaultRef.isEmpty()) {
            sb.append(", ").append(defaultRef);
        }
        sb.append(") {\n");
        sb.append("        @Override\n");
        sb.append("        public boolean isSettable(").append(className).append(" node) {\n");
        sb.append("            return node.").append(propName).append(" == null || !node.")
                .append(propName).append(".isBound();\n");
        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public javafx.css.StyleableProperty<").append(cssValueType)
                .append("> getStyleableProperty(").append(className).append(" node) {\n");
        sb.append("            return (javafx.css.StyleableProperty<").append(cssValueType)
                .append(">) node.").append(propName).append("Property();\n");
        sb.append("        }\n");
        sb.append("    };\n\n");

        // Styleable property anonymous class body
        String styleableBody = buildStyleableBody(propName, cssValueType, metaName);

        if (lazy) {
            // Lazy: field without initializer
            sb.append("private ").append(fieldType).append(" ").append(propName).append(";\n");
            appendGetter(sb, propName, getterName, valueType, javadoc, true, defaultLiteralRef);
            if (!readonly) {
                appendSetter(sb, propName, capName, valueType, javadoc, true);
            }

            if (javadoc) {
                sb.append("\n/**\n * The ").append(propName).append(" property.\n */\n");
            }
            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
            sb.append("    if (").append(propName).append(" == null) {\n");
            sb.append("        ").append(propName).append(" = new ").append(fieldType).append("(");
            if (!defaultRef.isEmpty()) {
                sb.append(defaultRef);
            }
            sb.append(") {\n");
            sb.append(styleableBody);
            sb.append("        };\n");
            sb.append("    }\n");
            sb.append("    return ").append(propName).append(";\n");
            sb.append("}\n");
        } else {
            // Eager: field with anonymous class initializer
            sb.append("private final ").append(fieldType).append(" ").append(propName)
                    .append(" = new ").append(fieldType).append("(");
            if (!defaultRef.isEmpty()) {
                sb.append(defaultRef);
            }
            sb.append(") {\n");
            sb.append(styleableBody);
            sb.append("};\n");

            appendGetter(sb, propName, getterName, valueType, javadoc, false, null);
            if (!readonly) {
                appendSetter(sb, propName, capName, valueType, javadoc, false);
            }

            if (javadoc) {
                sb.append("\n/**\n * The ").append(propName).append(" property.\n */\n");
            }
            sb.append("public final ").append(fieldType).append(" ").append(propName).append("Property() {\n");
            sb.append("    return ").append(propName).append(";\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    @NotNull
    private String buildStyleableBody(@NotNull String propName, @NotNull String cssValueType,
                                      @NotNull String metaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("            @Override\n");
        sb.append("            public javafx.css.CssMetaData<? extends javafx.css.Styleable, ")
                .append(cssValueType).append("> getCssMetaData() {\n");
        sb.append("                return ").append(metaName).append(";\n");
        sb.append("            }\n");
        sb.append("            @Override\n");
        sb.append("            public Object getBean() {\n");
        sb.append("                return ").append(className).append(".this;\n");
        sb.append("            }\n");
        sb.append("            @Override\n");
        sb.append("            public String getName() {\n");
        sb.append("                return \"").append(propName).append("\";\n");
        sb.append("            }\n");
        return sb.toString();
    }

    private void appendGetter(@NotNull StringBuilder sb, @NotNull String propName,
                              @NotNull String getterName, @NotNull String valueType,
                              boolean javadoc, boolean lazy, @Nullable String defaultLiteral) {
        if (javadoc) {
            sb.append("\n/**\n * Gets the value of {@link #").append(propName)
                    .append("Property() ").append(propName).append("}.\n */\n");
        }
        sb.append("public final ").append(valueType).append(" ").append(getterName).append("() {\n");
        if (lazy) {
            sb.append("    return ").append(propName).append(" == null ? ")
                    .append(defaultLiteral).append(" : ").append(propName).append(".get();\n");
        } else {
            sb.append("    return ").append(propName).append(".get();\n");
        }
        sb.append("}\n");
    }

    private void appendSetter(@NotNull StringBuilder sb, @NotNull String propName,
                              @NotNull String capName, @NotNull String valueType,
                              boolean javadoc, boolean lazy) {
        if (javadoc) {
            sb.append("\n/**\n * Sets the value of {@link #").append(propName)
                    .append("Property() ").append(propName).append("}.\n */\n");
        }
        sb.append("public final void set").append(capName).append("(").append(valueType).append(" value) {\n");
        if (lazy) {
            sb.append("    ").append(propName).append("Property().set(value);\n");
        } else {
            sb.append("    ").append(propName).append(".set(value);\n");
        }
        sb.append("}\n");
    }

    private void appendPropertyAccessor(@NotNull StringBuilder sb, @NotNull String propName,
                                        @NotNull FxPropertyType type, @NotNull String fieldType,
                                        @NotNull String genericSuffix, boolean javadoc,
                                        boolean readonly, boolean lazy) {
        if (javadoc) {
            sb.append("\n/**\n * The ").append(propName).append(" property.\n */\n");
        }
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
    private String toUpperSnakeCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(camelCase.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    @NotNull
    private String toKebabCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder("-fx-");
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
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
