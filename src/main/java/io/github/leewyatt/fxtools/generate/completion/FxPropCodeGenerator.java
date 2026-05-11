package io.github.leewyatt.fxtools.generate.completion;

import io.github.leewyatt.fxtools.generate.FxPropertyType;
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
        STRING("fxpstring", "StringProperty", "String", "SimpleStringProperty",
                "ReadOnlyStringWrapper", "ReadOnlyStringProperty",
                "StyleableStringProperty", "SimpleStyleableStringProperty",
                "javafx.css.StyleConverter.getStringConverter()", "String",
                "get", "\"\"", "null", "null", false, false),
        INTEGER("fxpinteger", "IntegerProperty", "int", "SimpleIntegerProperty",
                "ReadOnlyIntegerWrapper", "ReadOnlyIntegerProperty",
                "StyleableIntegerProperty", "SimpleStyleableIntegerProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0", "0", "0", false, false),
        LONG("fxplong", "LongProperty", "long", "SimpleLongProperty",
                "ReadOnlyLongWrapper", "ReadOnlyLongProperty",
                "StyleableLongProperty", "SimpleStyleableLongProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0L", "0L", "0L", false, false),
        FLOAT("fxpfloat", "FloatProperty", "float", "SimpleFloatProperty",
                "ReadOnlyFloatWrapper", "ReadOnlyFloatProperty",
                "StyleableFloatProperty", "SimpleStyleableFloatProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0.0F", "0.0F", "0.0F", false, false),
        DOUBLE("fxpdouble", "DoubleProperty", "double", "SimpleDoubleProperty",
                "ReadOnlyDoubleWrapper", "ReadOnlyDoubleProperty",
                "StyleableDoubleProperty", "SimpleStyleableDoubleProperty",
                "javafx.css.StyleConverter.getSizeConverter()", "Number",
                "get", "0.0", "0.0", "0.0", false, false),
        BOOLEAN("fxpboolean", "BooleanProperty", "boolean", "SimpleBooleanProperty",
                "ReadOnlyBooleanWrapper", "ReadOnlyBooleanProperty",
                "StyleableBooleanProperty", "SimpleStyleableBooleanProperty",
                "javafx.css.StyleConverter.getBooleanConverter()", "Boolean",
                "is", "false", "false", "false", false, false),
        OBJECT("fxpobject", "ObjectProperty", "Object", "SimpleObjectProperty",
                "ReadOnlyObjectWrapper", "ReadOnlyObjectProperty",
                "StyleableObjectProperty", "SimpleStyleableObjectProperty",
                FxPropertyType.TODO_STYLE_CONVERTER_EXPRESSION, "Object",
                "get", "null", "null", "null", true, false),
        LIST("fxplist", "ListProperty", "javafx.collections.ObservableList", "SimpleListProperty",
                "ReadOnlyListWrapper", "ReadOnlyListProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableArrayList()", "null",
                "javafx.collections.FXCollections.observableArrayList()", true, false),
        MAP("fxpmap", "MapProperty", "javafx.collections.ObservableMap", "SimpleMapProperty",
                "ReadOnlyMapWrapper", "ReadOnlyMapProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableHashMap()", "null",
                "javafx.collections.FXCollections.observableHashMap()", false, true),
        SET("fxpset", "SetProperty", "javafx.collections.ObservableSet", "SimpleSetProperty",
                "ReadOnlySetWrapper", "ReadOnlySetProperty",
                null, null, null, null,
                "get", "javafx.collections.FXCollections.observableSet()", "null",
                "javafx.collections.FXCollections.observableSet()", true, false);

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
        final String smartDefault;
        final boolean singleGeneric;
        final boolean dualGeneric;

        PropType(String abbrev, String propertyClass, String valueType, String simpleClass,
                 String roWrapper, String roProperty,
                 String styleableClass, String simpleStyleableClass,
                 String converterExpr, String cssValueType,
                 String getterPrefix, String lazyDefault, String nullDefault, String smartDefault,
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
            this.smartDefault = smartDefault;
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
        String getterDefault = lazy ? type.lazyDefault : type.nullDefault;

        StringBuilder sb = new StringBuilder();

        // Default constant
        if (defaultConst) {
            sb.append("private static final ").append(valType)
                    .append(" DEFAULT_$NAME_CONST$ = $DEFAULT$;\n\n");
            getterDefault = "DEFAULT_$NAME_CONST$";
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

        String ctorDefault = defaultConst ? ", DEFAULT_$NAME_CONST$" : (lazy ? ", " + type.lazyDefault : "");
        implExpr += ctorDefault + ")";

        if (lazy) {
            appendLazyCode(sb, type, fieldType, implExpr, valType, getterDefault, readonly, css);
        } else {
            appendEagerCode(sb, type, fieldType, implExpr, valType, readonly, css);
        }

        return sb.toString();
    }

    private static void appendLazyCode(@NotNull StringBuilder sb,
                                       @NotNull PropType type,
                                       @NotNull String fieldType,
                                       @NotNull String implExpr,
                                       @NotNull String valType,
                                       @NotNull String getterDefault,
                                       boolean readonly,
                                       boolean css) {
        sb.append("private ").append(fieldType).append(" $NAME$;\n");

        sb.append("\npublic final ").append(valType).append(" ").append(type.getterPrefix).append("$Name$() {\n");
        sb.append("    return $NAME$ == null ? ").append(getterDefault).append(" : $NAME$.get();\n");
        sb.append("}\n");

        if (!readonly) {
            sb.append("\npublic final void set$Name$(").append(valType).append(" value) {\n");
            sb.append("    $NAME$Property().set(value);\n");
            sb.append("}\n");
        }

        sb.append("\npublic final ").append(propertyReturnType(type, fieldType, readonly, css))
                .append(" $NAME$Property() {\n");
        sb.append("    if ($NAME$ == null) {\n");
        sb.append("        $NAME$ = new ").append(implExpr).append(";\n");
        sb.append("    }\n");
        if (readonly && !css) {
            sb.append("    return $NAME$.getReadOnlyProperty();\n");
        } else {
            sb.append("    return $NAME$;\n");
        }
        sb.append("}\n$END$");
    }

    private static void appendEagerCode(@NotNull StringBuilder sb,
                                        @NotNull PropType type,
                                        @NotNull String fieldType,
                                        @NotNull String implExpr,
                                        @NotNull String valType,
                                        boolean readonly,
                                        boolean css) {
        sb.append("private final ").append(fieldType).append(" $NAME$ = new ")
                .append(implExpr).append(";\n");

        sb.append("\npublic final ").append(valType).append(" ").append(type.getterPrefix).append("$Name$() {\n");
        sb.append("    return $NAME$.get();\n");
        sb.append("}\n");

        if (!readonly) {
            sb.append("\npublic final void set$Name$(").append(valType).append(" value) {\n");
            sb.append("    this.$NAME$.set(value);\n");
            sb.append("}\n");
        }

        sb.append("\npublic final ").append(propertyReturnType(type, fieldType, readonly, css))
                .append(" $NAME$Property() {\n");
        if (readonly && !css) {
            sb.append("    return $NAME$.getReadOnlyProperty();\n");
        } else {
            sb.append("    return $NAME$;\n");
        }
        sb.append("}\n$END$");
    }

    @NotNull
    private static String propertyReturnType(@NotNull PropType type,
                                             @NotNull String fieldType,
                                             boolean readonly,
                                             boolean css) {
        if (readonly && !css) {
            return "javafx.beans.property." + type.roProperty + genericSuffix(type);
        }
        return fieldType;
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
        if (type == PropType.OBJECT) {
            return "$TYPE$";
        }
        if (type.singleGeneric) {
            return type.valueType + "<$TYPE$>";
        }
        if (type.dualGeneric) {
            return type.valueType + "<$KEY_TYPE$, $VALUE_TYPE$>";
        }
        return type.valueType;
    }
}
