package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import io.github.leewyatt.fxtools.generate.FxPropertyType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

record FxPropertyAccessorDescriptor(
        @NotNull PsiField field,
        @NotNull PsiClass containingClass,
        @NotNull FxPropertyType type,
        boolean readOnly,
        boolean lazy,
        @NotNull String fieldName,
        @NotNull String capitalizedName,
        @NotNull String getterName,
        @NotNull String setterName,
        @NotNull String propertyMethodName,
        @NotNull String valueTypeText,
        @NotNull PsiType valueType,
        @NotNull String propertyReturnTypeText,
        @NotNull String lazyInitializerText,
        @NotNull String lazyFallbackText,
        @NotNull EnumSet<FxAccessorMethodKind> missingMethods
) {
    boolean hasMissingMethods() {
        return !missingMethods.isEmpty();
    }
}
