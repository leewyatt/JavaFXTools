package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.*;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * Allows auto-popup completion inside Java string literals for:
 * <ul>
 *   <li>Inline CSS: strings starting with {@code -} (e.g. {@code setStyle("-fx-...")})</li>
 *   <li>Ikonli icon literals: strings inside {@code new FontIcon("...")} or
 *       {@code setIconLiteral("...")}</li>
 * </ul>
 * Without this, {@code JavaCompletionConfidence} suppresses auto-popup in all strings.
 */
public class FxCssInlineCompletionConfidence extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement,
                                                    @NotNull PsiFile psiFile,
                                                    int offset) {
        PsiElement parent = contextElement.getParent();
        if (parent instanceof PsiLiteralExpression literal) {
            Object value = literal.getValue();
            if (value instanceof String strValue) {
                // Inline CSS: strings starting with -
                if (strValue.trim().startsWith("-")) {
                    return ThreeState.NO;
                }
                // Ikonli icon literal: new FontIcon("...") or setIconLiteral("...")
                if (isIconLiteralContext(literal)) {
                    return ThreeState.NO;
                }
            }
        }
        return ThreeState.UNSURE;
    }

    private static boolean isIconLiteralContext(@NotNull PsiLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof PsiExpressionList argList)) {
            return false;
        }
        PsiExpression[] args = argList.getExpressions();
        if (args.length == 0 || args[0] != literal) {
            return false;
        }
        PsiElement grandParent = argList.getParent();
        if (grandParent instanceof PsiNewExpression newExpr) {
            var classRef = newExpr.getClassReference();
            return classRef != null && "FontIcon".equals(classRef.getReferenceName());
        }
        if (grandParent instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            return "setIconLiteral".equals(name);
        }
        return false;
    }
}
