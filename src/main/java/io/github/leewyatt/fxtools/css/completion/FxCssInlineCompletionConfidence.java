package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * Allows auto-popup completion inside Java string literals that contain CSS.
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
            if (value instanceof String strValue && strValue.trim().startsWith("-")) {
                return ThreeState.NO;
            }
        }
        return ThreeState.UNSURE;
    }
}
