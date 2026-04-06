package io.github.leewyatt.fxtools.ikonli;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.InheritanceUtil;
import io.github.leewyatt.fxtools.css.completion.FxCssCompletionUtil;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides Ikonli icon literal completion inside Java string arguments of:
 * <ul>
 *   <li>{@code new FontIcon("...")}</li>
 *   <li>{@code setIconLiteral("...")}</li>
 * </ul>
 * Reuses {@link FxCssCompletionUtil#addIconCodeCompletions} for the actual candidates.
 */
public class JavaIconLiteralCompletionContributor extends CompletionContributor {

    private static final InsertHandler<LookupElement> NOOP_INSERT_HANDLER = (ctx, item) -> { };
    private static final String FONT_ICON_FQN = "org.kordamp.ikonli.javafx.FontIcon";

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        if (!"java".equalsIgnoreCase(file.getFileType().getDefaultExtension())) {
            return;
        }

        Project project = file.getProject();
        if (!FxDetector.isJavaFxProject(project)) {
            return;
        }

        PsiElement position = parameters.getOriginalPosition();
        if (position == null) {
            position = parameters.getPosition();
        }

        PsiLiteralExpression literal = findStringLiteral(position);
        if (literal == null) {
            return;
        }

        if (!isIconLiteralContext(literal)) {
            return;
        }

        // Extract prefix for matching (text typed so far inside the string)
        String text = literal.getValue() instanceof String s ? s : "";
        int valueStart = literal.getTextRange().getStartOffset() + 1; // skip opening quote
        int caret = parameters.getOffset();
        int prefixLen = Math.max(0, Math.min(caret - valueStart, text.length()));
        String prefix = text.substring(0, prefixLen);

        CompletionResultSet javaResult = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
        FxCssCompletionUtil.addIconCodeCompletions(project, javaResult, NOOP_INSERT_HANDLER);
    }

    @Nullable
    private static PsiLiteralExpression findStringLiteral(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiLiteralExpression lit && lit.getValue() instanceof String) {
                return lit;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Checks if the string literal is inside {@code new FontIcon("...")} or
     * {@code .setIconLiteral("...")}.
     */
    private static boolean isIconLiteralContext(@NotNull PsiLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof PsiExpressionList argList)) {
            return false;
        }
        // Must be the first argument
        PsiExpression[] args = argList.getExpressions();
        if (args.length == 0 || args[0] != literal) {
            return false;
        }

        PsiElement grandParent = argList.getParent();

        // new FontIcon("...")
        if (grandParent instanceof PsiNewExpression newExpr) {
            return isFontIconType(newExpr);
        }

        // .setIconLiteral("...")
        if (grandParent instanceof PsiMethodCallExpression call) {
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return false;
            }
            String methodName = method.getName();
            return "setIconLiteral".equals(methodName);
        }

        return false;
    }

    private static boolean isFontIconType(@NotNull PsiNewExpression newExpr) {
        var classRef = newExpr.getClassReference();
        if (classRef == null) {
            return false;
        }
        PsiElement resolved = classRef.resolve();
        if (resolved instanceof com.intellij.psi.PsiClass psiClass) {
            return InheritanceUtil.isInheritor(psiClass, FONT_ICON_FQN)
                    || FONT_ICON_FQN.equals(psiClass.getQualifiedName());
        }
        // Fallback: name-based check if classpath is incomplete
        String name = classRef.getReferenceName();
        return "FontIcon".equals(name);
    }
}
