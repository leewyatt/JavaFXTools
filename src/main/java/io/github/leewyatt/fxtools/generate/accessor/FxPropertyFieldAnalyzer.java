package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.leewyatt.fxtools.generate.FxPropertyType;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

final class FxPropertyFieldAnalyzer {

    private FxPropertyFieldAnalyzer() {
    }

    @Nullable
    static FxPropertyAccessorDescriptor analyze(@NotNull Project project, @NotNull PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return null;
        }

        String fieldName = field.getName();
        PsiClass containingClass = field.getContainingClass();
        if (fieldName == null || containingClass == null
                || containingClass.isInterface() || containingClass.isAnnotationType()) {
            return null;
        }
        if (!PsiNameHelper.getInstance(project).isIdentifier(fieldName)) {
            return null;
        }

        Match match = detectType(project, field);
        if (match == null) {
            return null;
        }

        boolean lazy = isLazy(field);
        String initializerText = lazyInitializerText(project, field, match);
        if (lazy && initializerText == null) {
            return null;
        }

        String capitalizedName = FxNamingUtil.capitalize(fieldName);
        String getterName = match.type().getGetterPrefix() + capitalizedName;
        String setterName = "set" + capitalizedName;
        String propertyMethodName = fieldName + "Property";
        String valueTypeText = valueTypeText(match);
        PsiType valueType = JavaPsiFacade.getElementFactory(project).createTypeFromText(valueTypeText, field);
        String propertyReturnTypeText = propertyReturnTypeText(match);

        EnumSet<FxAccessorMethodKind> missing = missingMethods(
                containingClass, match.readOnly(), getterName, setterName,
                propertyMethodName, valueType);

        return new FxPropertyAccessorDescriptor(
                field,
                containingClass,
                match.type(),
                match.readOnly(),
                lazy,
                fieldName,
                capitalizedName,
                getterName,
                setterName,
                propertyMethodName,
                valueTypeText,
                valueType,
                propertyReturnTypeText,
                initializerText == null ? "" : initializerText,
                lazyFallbackText(match.type()),
                missing);
    }

    @Nullable
    private static Match detectType(@NotNull Project project, @NotNull PsiField field) {
        PsiType fieldType = field.getType();
        for (FxPropertyType type : FxPropertyType.values()) {
            if (InheritanceUtil.isInheritor(fieldType, type.getReadOnlyWrapperFqn())) {
                return Match.create(project, field, type, true);
            }
        }
        for (FxPropertyType type : FxPropertyType.values()) {
            if (InheritanceUtil.isInheritor(fieldType, type.getPropertyFqn())) {
                return Match.create(project, field, type, false);
            }
        }
        return null;
    }

    private static boolean isLazy(@NotNull PsiField field) {
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
            return true;
        }
        return initializer instanceof PsiLiteralExpression
                && ((PsiLiteralExpression) initializer).getValue() == null;
    }

    @Nullable
    private static String lazyInitializerText(@NotNull Project project,
                                              @NotNull PsiField field,
                                              @NotNull Match match) {
        if (!isLazy(field)) {
            return null;
        }

        String implTypeForCheck = match.readOnly()
                ? match.type().getReadOnlyWrapperFqn() + genericSuffix(match.typeArguments())
                : match.type().getPackageName() + "." + match.type().getSimpleClassName()
                + genericSuffix(match.typeArguments());
        PsiType implPsiType = JavaPsiFacade.getElementFactory(project).createTypeFromText(implTypeForCheck, field);
        if (!TypeConversionUtil.isAssignable(field.getType(), implPsiType)) {
            return null;
        }
        String implTypeForCode = match.readOnly()
                ? implTypeForCheck
                : match.type().getPackageName() + "." + match.type().getSimpleClassName()
                + genericDiamond(match.typeArguments());
        return "new " + implTypeForCode + "(this, \"" + field.getName() + "\")";
    }

    @NotNull
    private static String valueTypeText(@NotNull Match match) {
        FxPropertyType type = match.type();
        PsiType[] args = match.typeArguments();
        switch (type) {
            case STRING:
                return "java.lang.String";
            case OBJECT:
                return args.length > 0 ? args[0].getCanonicalText() : "java.lang.Object";
            case LIST:
                return collectionValueType("javafx.collections.ObservableList", args);
            case MAP:
                return collectionValueType("javafx.collections.ObservableMap", args);
            case SET:
                return collectionValueType("javafx.collections.ObservableSet", args);
            default:
                return type.getValueTypeName();
        }
    }

    @NotNull
    private static String propertyReturnTypeText(@NotNull Match match) {
        String base = match.readOnly()
                ? match.type().getReadOnlyPropertyFqn()
                : match.type().getPropertyFqn();
        return base + genericSuffix(match.typeArguments());
    }

    @NotNull
    private static String collectionValueType(@NotNull String base, PsiType @NotNull [] args) {
        return args.length == 0 ? base : base + genericSuffix(args);
    }

    @NotNull
    private static String genericSuffix(PsiType @NotNull [] args) {
        if (args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i].getCanonicalText());
        }
        return sb.append(">").toString();
    }

    @NotNull
    private static String genericDiamond(PsiType @NotNull [] args) {
        return args.length == 0 ? "" : "<>";
    }

    @NotNull
    private static String lazyFallbackText(@NotNull FxPropertyType type) {
        switch (type) {
            case INTEGER:
                return "0";
            case LONG:
                return "0L";
            case FLOAT:
                return "0.0F";
            case DOUBLE:
                return "0.0";
            case BOOLEAN:
                return "false";
            default:
                return "null";
        }
    }

    @NotNull
    private static EnumSet<FxAccessorMethodKind> missingMethods(@NotNull PsiClass psiClass,
                                                                boolean readOnly,
                                                                @NotNull String getterName,
                                                                @NotNull String setterName,
                                                                @NotNull String propertyMethodName,
                                                                @NotNull PsiType valueType) {
        EnumSet<FxAccessorMethodKind> missing = EnumSet.noneOf(FxAccessorMethodKind.class);
        if (!hasNoArgMethod(psiClass, getterName)) {
            missing.add(FxAccessorMethodKind.GETTER);
        }
        if (!readOnly && !hasSetter(psiClass, setterName, valueType)) {
            missing.add(FxAccessorMethodKind.SETTER);
        }
        if (!hasNoArgMethod(psiClass, propertyMethodName)) {
            missing.add(FxAccessorMethodKind.PROPERTY);
        }
        return missing;
    }

    private static boolean hasNoArgMethod(@NotNull PsiClass psiClass, @NotNull String methodName) {
        for (PsiMethod method : psiClass.findMethodsByName(methodName, false)) {
            if (method.getParameterList().getParametersCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSetter(@NotNull PsiClass psiClass,
                                     @NotNull String methodName,
                                     @NotNull PsiType valueType) {
        for (PsiMethod method : psiClass.findMethodsByName(methodName, false)) {
            if (method.getParameterList().getParametersCount() == 1
                    && method.getParameterList().getParameter(0).getType().equals(valueType)) {
                return true;
            }
        }
        return false;
    }

    private record Match(@NotNull FxPropertyType type,
                         boolean readOnly,
                         PsiType @NotNull [] typeArguments) {

        @Nullable
        static Match create(@NotNull Project project,
                            @NotNull PsiField field,
                            @NotNull FxPropertyType type,
                            boolean readOnly) {
            String baseFqn = readOnly ? type.getReadOnlyWrapperFqn() : type.getPropertyFqn();
            PsiClass baseClass = JavaPsiFacade.getInstance(project)
                    .findClass(baseFqn, field.getResolveScope());
            if (!(field.getType() instanceof PsiClassType)) {
                return new Match(type, readOnly, PsiType.EMPTY_ARRAY);
            }
            PsiClassType.ClassResolveResult result = ((PsiClassType) field.getType()).resolveGenerics();
            PsiClass fieldClass = result.getElement();
            if (baseClass == null || fieldClass == null) {
                return new Match(type, readOnly, PsiType.EMPTY_ARRAY);
            }

            PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(
                    baseClass, fieldClass, result.getSubstitutor());
            PsiTypeParameter[] parameters = baseClass.getTypeParameters();
            PsiType[] args = new PsiType[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                PsiType substituted = substitutor.substitute(parameters[i]);
                args[i] = substituted == null ? PsiType.getJavaLangObject(field.getManager(), field.getResolveScope())
                        : substituted;
            }
            return new Match(type, readOnly, args);
        }
    }
}
