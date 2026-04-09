package io.github.leewyatt.fxtools.generate.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates JavaFX Property template text with $VAR$ placeholders for Live Template expansion.
 */
public final class FxPropCodeGenerator {

    private FxPropCodeGenerator() {
    }

    /**
     * Property type metadata for code generation.
     */
    enum PropType {
        STR("fxpstr", "StringProperty", "String", "SimpleStringProperty",
                "ReadOnlyStringWrapper", "ReadOnlyStringProperty",
                "StyleableStringProperty", "SimpleStyleableStringProperty",
                "javafx.css.StyleConverter.getStringConverter()", "String",
                "get", "\"\"", "null", false, false),
        INT("fxpint", "IntegerProperty", "int", "SimpleIntegerProperty",
                "ReadOnlyIntegerWrapper", "ReadOnlyIntegerProperty",
                "StyleableIntegerProperty", "SimpleStyleableIntegerProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0", "0", false, false),
        DBL("fxpdbl", "DoubleProperty", "double", "SimpleDoubleProperty",
                "ReadOnlyDoubleWrapper", "ReadOnlyDoubleProperty",
                "StyleableDoubleProperty", "SimpleStyleableDoubleProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0.0", "0.0", false, false),
        BOOL("fxpbool", "BooleanProperty", "boolean", "SimpleBooleanProperty",
                "ReadOnlyBooleanWrapper", "ReadOnlyBooleanProperty",
                "StyleableBooleanProperty", "SimpleStyleableBooleanProperty",
                "javafx.css.StyleConverter.getBooleanConverter()", "Boolean",
                "is", "false", "false", false, false),
        OBJ("fxpobj", "ObjectProperty", "Object", "SimpleObjectProperty",
                "ReadOnlyObjectWrapper", "ReadOnlyObjectProperty",
                "StyleableObjectProperty", "SimpleStyleableObjectProperty",
                "javafx.css.StyleConverter.getStringConverter()", "Object",
                "get", "null", "null", true, false),
        LST("fxplst", "ListProperty", "javafx.collections.ObservableList", "SimpleListProperty",
                "ReadOnlyListWrapper", "ReadOnlyListProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableArrayList()", "null", true, false),
        MAP("fxpmap", "MapProperty", "javafx.collections.ObservableMap", "SimpleMapProperty",
                "ReadOnlyMapWrapper", "ReadOnlyMapProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableHashMap()", "null", false, true),
        SET("fxpset", "SetProperty", "javafx.collections.ObservableSet", "SimpleSetProperty",
                "ReadOnlySetWrapper", "ReadOnlySetProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableSet()", "null", true, false);

        final String abbrev;
        final String propertyClass;
        final String valueType;
        final String simpleClass;
        final String roWrapper;
        final String roProperty;
        final String styleableClass;
        final String simpleStyleableClass;
        final String converterExpr;
        final String cssValueType;
        final String getterPrefix;
        final String lazyDefault;
        final String nullDefault;
        final boolean singleGeneric;
        final boolean dualGeneric;

        PropType(String abbrev, String propertyClass, String valueType, String simpleClass,
                 String roWrapper, String roProperty,
                 String styleableClass, String simpleStyleableClass,
                 String converterExpr, String cssValueType,
                 String getterPrefix, String lazyDefault, String nullDefault,
                 boolean singleGeneric, boolean dualGeneric) {
            this.abbrev = abbrev;
            this.propertyClass = propertyClass;
            this.valueType = valueType;
            this.simpleClass = simpleClass;
            this.roWrapper = roWrapper;
            this.roProperty = roProperty;
            this.styleableClass = styleableClass;
            this.simpleStyleableClass = simpleStyleableClass;
            this.converterExpr = converterExpr;
            this.cssValueType = cssValueType;
            this.getterPrefix = getterPrefix;
            this.lazyDefault = lazyDefault;
            this.nullDefault = nullDefault;
            this.singleGeneric = singleGeneric;
            this.dualGeneric = dualGeneric;
        }

        boolean supportsCss() {
            return styleableClass != null;
        }

    }

    /**
     * Generates template text for the property field + getter/setter/property method.
     * When css=true, references StyleableProperties.$NAME_CONST$ instead of inline CssMetaData.
     */
    @NotNull
    public static String generate(@NotNull PropType type, boolean lazy, boolean css,
                                  boolean readonly, boolean defaultConst,
                                  @NotNull String className) {
        String pkg = "javafx.beans.property.";
        String gen = genericSuffix(type);
        String diamond = type.singleGeneric || type.dualGeneric ? "<>" : "";
        String valType = valueTypeText(type);
        String defVal = lazy ? type.lazyDefault : type.nullDefault;

        StringBuilder sb = new StringBuilder();

        // Default constant
        if (defaultConst) {
            sb.append("private static final ").append(valType)
                    .append(" DEFAULT_$NAME_CONST$ = $DEFAULT$;\n\n");
            defVal = "DEFAULT_$NAME_CONST$";
        }

        // Field — CSS uses standard Property type for declaration, Styleable impl only for new
        String fieldType;
        String implExpr;
        if (css) {
            fieldType = pkg + type.propertyClass + gen;
            implExpr = "javafx.css." + type.simpleStyleableClass + diamond
                    + "(StyleableProperties.$NAME_CONST$, this, \"$NAME$\"";
        } else if (readonly) {
            fieldType = pkg + type.roWrapper + gen;
            implExpr = pkg + type.roWrapper + diamond + "(this, \"$NAME$\"";
        } else {
            fieldType = pkg + type.propertyClass + gen;
            implExpr = pkg + type.simpleClass + diamond + "(this, \"$NAME$\"";
        }

        // Add default to constructor
        String ctorDefault = defaultConst ? ", DEFAULT_$NAME_CONST$" : (lazy ? ", " + type.lazyDefault : "");
        implExpr += ctorDefault + ")";

        sb.append("private ").append(fieldType).append(" $NAME$;\n");

        // Getter
        sb.append("\npublic final ").append(valType).append(" ").append(type.getterPrefix).append("$Name$() {\n");
        sb.append("    return $NAME$ == null ? ").append(defVal).append(" : $NAME$.get();\n");
        sb.append("}\n");

        // Setter
        if (!readonly) {
            sb.append("\npublic final void set$Name$(").append(valType).append(" value) {\n");
            sb.append("    $NAME$Property().set(value);\n");
            sb.append("}\n");
        }

        // Property accessor
        String returnType;
        if (readonly && !css) {
            returnType = pkg + type.roProperty + gen;
        } else {
            returnType = fieldType;
        }
        sb.append("\npublic final ").append(returnType).append(" $NAME$Property() {\n");
        sb.append("    if ($NAME$ == null) {\n");
        sb.append("        $NAME$ = new ").append(implExpr).append(";\n");
        sb.append("    }\n");
        if (readonly && !css) {
            sb.append("    return $NAME$.getReadOnlyProperty();\n");
        } else {
            sb.append("    return $NAME$;\n");
        }
        sb.append("}\n$END$");

        return sb.toString();
    }

    /**
     * Generates the StyleableProperties inner class code for insertion at the end of the class body.
     * Called after the template finishes to get the final property name.
     *
     * @param propertyName the actual property name (e.g. "showLabel")
     * @param className    the containing class name
     * @param type         the property type
     * @param defaultConst whether a default constant was generated
     * @param lazy         whether lazy initialization is used
     * @param superClassName the direct superclass name for getClassCssMetaData() call
     * @param useControlCssMetaData true if the class extends Control (use getControlCssMetaData)
     * @return Java source code for the inner class + two methods
     */
    @NotNull
    public static String generateStyleablePropertiesClass(
            @NotNull String propertyName, @NotNull String className,
            @NotNull PropType type, boolean defaultConst, boolean lazy,
            @NotNull String superClassName, boolean useControlCssMetaData) {

        String constName = toConstantCase(propertyName);
        String cssName = toKebabCase(propertyName);

        String defaultExpr;
        if (defaultConst) {
            defaultExpr = "DEFAULT_" + constName;
        } else if (lazy) {
            defaultExpr = type.lazyDefault;
        } else {
            defaultExpr = null;
        }

        StringBuilder sb = new StringBuilder();

        // StyleableProperties inner class
        sb.append("private static class StyleableProperties {\n\n");

        // CssMetaData field
        sb.append("    private static final javafx.css.CssMetaData<").append(className)
                .append(", ").append(type.cssValueType).append("> ").append(constName).append(" =\n");
        sb.append("            new javafx.css.CssMetaData<>(\"").append(cssName).append("\",\n");
        sb.append("                    ").append(type.converterExpr);
        if (defaultExpr != null) {
            sb.append(", ").append(defaultExpr);
        }
        sb.append(") {\n");
        sb.append("        @Override\n");
        sb.append("        public boolean isSettable(").append(className).append(" node) {\n");
        sb.append("            return node.").append(propertyName)
                .append(" == null || !node.").append(propertyName).append(".isBound();\n");
        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public javafx.css.StyleableProperty<").append(type.cssValueType)
                .append("> getStyleableProperty(").append(className).append(" node) {\n");
        sb.append("            return (javafx.css.StyleableProperty<").append(type.cssValueType)
                .append(">) node.").append(propertyName).append("Property();\n");
        sb.append("        }\n");
        sb.append("    };\n\n");

        // STYLEABLES list
        sb.append("    private static final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> STYLEABLES;\n\n");
        sb.append("    static {\n");
        sb.append("        final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> styleables =\n");
        sb.append("                new java.util.ArrayList<>(").append(superClassName)
                .append(".getClassCssMetaData());\n");
        sb.append("        java.util.Collections.addAll(styleables, ").append(constName).append(");\n");
        sb.append("        STYLEABLES = java.util.Collections.unmodifiableList(styleables);\n");
        sb.append("    }\n");
        sb.append("}\n\n");

        // getClassCssMetaData static method
        sb.append("public static java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getClassCssMetaData() {\n");
        sb.append("    return StyleableProperties.STYLEABLES;\n");
        sb.append("}\n\n");

        // Instance method
        if (useControlCssMetaData) {
            sb.append("@Override\n");
            sb.append("protected java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getControlCssMetaData() {\n");
            sb.append("    return getClassCssMetaData();\n");
            sb.append("}\n");
        } else {
            sb.append("@Override\n");
            sb.append("public java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getCssMetaData() {\n");
            sb.append("    return getClassCssMetaData();\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    /**
     * Generates a standalone CssMetaData field for insertion into an existing StyleableProperties class.
     */
    @NotNull
    public static String generateCssMetaDataField(
            @NotNull String propertyName, @NotNull String className,
            @NotNull PropType type, boolean defaultConst, boolean lazy) {

        String constName = toConstantCase(propertyName);
        String cssName = toKebabCase(propertyName);

        String defaultExpr;
        if (defaultConst) {
            defaultExpr = "DEFAULT_" + constName;
        } else if (lazy) {
            defaultExpr = type.lazyDefault;
        } else {
            defaultExpr = null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("private static final javafx.css.CssMetaData<").append(className)
                .append(", ").append(type.cssValueType).append("> ").append(constName).append(" =\n");
        sb.append("        new javafx.css.CssMetaData<>(\"").append(cssName).append("\",\n");
        sb.append("                ").append(type.converterExpr);
        if (defaultExpr != null) {
            sb.append(", ").append(defaultExpr);
        }
        sb.append(") {\n");
        sb.append("    @Override\n");
        sb.append("    public boolean isSettable(").append(className).append(" node) {\n");
        sb.append("        return node.").append(propertyName)
                .append(" == null || !node.").append(propertyName).append(".isBound();\n");
        sb.append("    }\n");
        sb.append("    @Override\n");
        sb.append("    public javafx.css.StyleableProperty<").append(type.cssValueType)
                .append("> getStyleableProperty(").append(className).append(" node) {\n");
        sb.append("        return (javafx.css.StyleableProperty<").append(type.cssValueType)
                .append(">) node.").append(propertyName).append("Property();\n");
        sb.append("    }\n");
        sb.append("};\n");

        return sb.toString();
    }

    /**
     * Converts camelCase to UPPER_SNAKE_CASE.
     */
    @NotNull
    static String toConstantCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * Converts camelCase to -fx-kebab-case.
     */
    @NotNull
    static String toKebabCase(@NotNull String camelCase) {
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
    private static String genericSuffix(@NotNull PropType type) {
        if (type.singleGeneric) {
            return "<$TYPE$>";
        }
        if (type.dualGeneric) {
            return "<$KEY_TYPE$, $VALUE_TYPE$>";
        }
        return "";
    }

    @NotNull
    private static String valueTypeText(@NotNull PropType type) {
        if (type.singleGeneric) {
            return type.valueType + "<$TYPE$>";
        }
        if (type.dualGeneric) {
            return type.valueType + "<$KEY_TYPE$, $VALUE_TYPE$>";
        }
        return type.valueType;
    }
}
