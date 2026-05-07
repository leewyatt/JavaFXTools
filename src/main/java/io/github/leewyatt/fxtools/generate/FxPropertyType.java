package io.github.leewyatt.fxtools.generate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enumerates all JavaFX Property types with their associated class names and metadata.
 */
public enum FxPropertyType {

    STRING("StringProperty", "String", "SimpleStringProperty",
            "ReadOnlyStringWrapper", "ReadOnlyStringProperty",
            "javafx.beans.property", false, false, "get"),
    INTEGER("IntegerProperty", "int", "SimpleIntegerProperty",
            "ReadOnlyIntegerWrapper", "ReadOnlyIntegerProperty",
            "javafx.beans.property", false, false, "get"),
    LONG("LongProperty", "long", "SimpleLongProperty",
            "ReadOnlyLongWrapper", "ReadOnlyLongProperty",
            "javafx.beans.property", false, false, "get"),
    FLOAT("FloatProperty", "float", "SimpleFloatProperty",
            "ReadOnlyFloatWrapper", "ReadOnlyFloatProperty",
            "javafx.beans.property", false, false, "get"),
    DOUBLE("DoubleProperty", "double", "SimpleDoubleProperty",
            "ReadOnlyDoubleWrapper", "ReadOnlyDoubleProperty",
            "javafx.beans.property", false, false, "get"),
    BOOLEAN("BooleanProperty", "boolean", "SimpleBooleanProperty",
            "ReadOnlyBooleanWrapper", "ReadOnlyBooleanProperty",
            "javafx.beans.property", false, false, "is"),
    OBJECT("ObjectProperty", "Object", "SimpleObjectProperty",
            "ReadOnlyObjectWrapper", "ReadOnlyObjectProperty",
            "javafx.beans.property", true, false, "get"),
    LIST("ListProperty", "ObservableList", "SimpleListProperty",
            "ReadOnlyListWrapper", "ReadOnlyListProperty",
            "javafx.beans.property", true, false, "get"),
    MAP("MapProperty", "ObservableMap", "SimpleMapProperty",
            "ReadOnlyMapWrapper", "ReadOnlyMapProperty",
            "javafx.beans.property", false, true, "get"),
    SET("SetProperty", "ObservableSet", "SimpleSetProperty",
            "ReadOnlySetWrapper", "ReadOnlySetProperty",
            "javafx.beans.property", true, false, "get");

    private final String propertyTypeName;
    private final String valueTypeName;
    private final String simpleClassName;
    private final String readOnlyWrapperName;
    private final String readOnlyPropertyName;
    private final String packageName;
    private final boolean needsTypeParam;
    private final boolean needsTwoTypeParams;
    private final String getterPrefix;

    FxPropertyType(String propertyTypeName, String valueTypeName, String simpleClassName,
                   String readOnlyWrapperName, String readOnlyPropertyName, String packageName,
                   boolean needsTypeParam, boolean needsTwoTypeParams, String getterPrefix) {
        this.propertyTypeName = propertyTypeName;
        this.valueTypeName = valueTypeName;
        this.simpleClassName = simpleClassName;
        this.readOnlyWrapperName = readOnlyWrapperName;
        this.readOnlyPropertyName = readOnlyPropertyName;
        this.packageName = packageName;
        this.needsTypeParam = needsTypeParam;
        this.needsTwoTypeParams = needsTwoTypeParams;
        this.getterPrefix = getterPrefix;
    }

    public String getPropertyTypeName() { return propertyTypeName; }
    public String getValueTypeName() { return valueTypeName; }
    public String getSimpleClassName() { return simpleClassName; }
    public String getReadOnlyWrapperName() { return readOnlyWrapperName; }
    public String getReadOnlyPropertyName() { return readOnlyPropertyName; }
    public String getPackageName() { return packageName; }
    public boolean isNeedsTypeParam() { return needsTypeParam; }
    public boolean isNeedsTwoTypeParams() { return needsTwoTypeParams; }
    public String getGetterPrefix() { return getterPrefix; }

    /**
     * Returns whether this type supports CSS styleable generation.
     */
    public boolean isStyleableSupported() {
        return this != OBJECT && this != LIST && this != MAP && this != SET;
    }

    /**
     * Returns the SimpleStyleableXxxProperty class simple name, or null if not supported.
     */
    @Nullable
    public String getSimpleStyleablePropertyName() {
        switch (this) {
            case STRING: return "SimpleStyleableStringProperty";
            case INTEGER: return "SimpleStyleableIntegerProperty";
            case LONG: return "SimpleStyleableLongProperty";
            case FLOAT: return "SimpleStyleableFloatProperty";
            case DOUBLE: return "SimpleStyleableDoubleProperty";
            case BOOLEAN: return "SimpleStyleableBooleanProperty";
            default: return null;
        }
    }

    /**
     * Returns the CSS StyleConverter expression for this type.
     */
    @Nullable
    public String getConverterExpression() {
        switch (this) {
            case STRING: return "javafx.css.StyleConverter.getStringConverter()";
            case INTEGER: case LONG: case FLOAT: case DOUBLE:
                return "javafx.css.StyleConverter.getSizeConverter()";
            case BOOLEAN: return "javafx.css.StyleConverter.getBooleanConverter()";
            default: return null;
        }
    }

    /**
     * Returns the CssMetaData value type for this property type.
     */
    @NotNull
    public String getCssValueType() {
        switch (this) {
            case BOOLEAN: return "Boolean";
            case STRING: return "String";
            case INTEGER: case LONG: case FLOAT: case DOUBLE: return "Number";
            default: return "Object";
        }
    }

    /**
     * Display name for the combo box.
     */
    @NotNull
    public String getDisplayName() {
        if (needsTypeParam) {
            return propertyTypeName + "<T>";
        }
        if (needsTwoTypeParams) {
            return propertyTypeName + "<K,V>";
        }
        return propertyTypeName;
    }

    /**
     * Returns the fully qualified property type name.
     */
    @NotNull
    public String getPropertyFqn() {
        return packageName + "." + propertyTypeName;
    }

    /**
     * Returns the fully qualified simple implementation class name.
     */
    @NotNull
    public String getSimpleFqn() {
        return packageName + "." + simpleClassName;
    }

    /**
     * Returns the fully qualified read-only wrapper class name.
     */
    @NotNull
    public String getReadOnlyWrapperFqn() {
        return packageName + "." + readOnlyWrapperName;
    }

    /**
     * Returns the fully qualified read-only property class name.
     */
    @NotNull
    public String getReadOnlyPropertyFqn() {
        return packageName + "." + readOnlyPropertyName;
    }

    /**
     * Returns the smart default value expression auto-filled when "Provide default value" is enabled.
     */
    @NotNull
    public String getSmartDefault() {
        switch (this) {
            case STRING: return "null";
            case INTEGER: return "0";
            case LONG: return "0L";
            case FLOAT: return "0.0f";
            case DOUBLE: return "0.0";
            case BOOLEAN: return "false";
            case OBJECT: return "null";
            case LIST: return "javafx.collections.FXCollections.observableArrayList()";
            case MAP: return "javafx.collections.FXCollections.observableHashMap()";
            case SET: return "javafx.collections.FXCollections.observableSet()";
            default: return "";
        }
    }

    /**
     * Adds the {@code L} or {@code f} suffix to a numeric literal when missing.
     * Non-literal expressions (variables, method calls) are returned unchanged.
     */
    @NotNull
    public String normalizeDefaultLiteral(@NotNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        boolean isHex = value.startsWith("0x") || value.startsWith("0X");
        char last = value.charAt(value.length() - 1);
        // In hex literals, F/D are hex digits, not type suffixes — only L/l acts as a suffix.
        boolean alreadySuffixed = isHex
                ? (last == 'l' || last == 'L')
                : ("lLfFdD".indexOf(last) >= 0);
        if (alreadySuffixed) {
            return value;
        }
        if (this == LONG) {
            if (isHex) {
                try {
                    Long.parseLong(value.substring(2), 16);
                    return value + "L";
                } catch (NumberFormatException ignored) {
                }
                return value;
            }
            try {
                Long.parseLong(value);
                return value + "L";
            } catch (NumberFormatException ignored) {
            }
            return value;
        }
        if (this == FLOAT) {
            // Hex int literals can't take f suffix in Java — leave alone, the int→float widening
            // is implicit at assignment (e.g., 0x1F → 31.0f).
            if (isHex) {
                return value;
            }
            try {
                Double.parseDouble(value);
                return value + "f";
            } catch (NumberFormatException ignored) {
            }
            return value;
        }
        return value;
    }

    /**
     * Returns the value type for display, considering generic parameters.
     */
    @NotNull
    public String getEffectiveValueType(@NotNull String genericParam, @NotNull String genericParam2) {
        if (needsTwoTypeParams && !genericParam.isEmpty() && !genericParam2.isEmpty()) {
            return valueTypeName + "<" + genericParam + ", " + genericParam2 + ">";
        }
        if (needsTypeParam && !genericParam.isEmpty()) {
            if (this == OBJECT) {
                return genericParam;
            }
            return valueTypeName + "<" + genericParam + ">";
        }
        return valueTypeName;
    }
}
