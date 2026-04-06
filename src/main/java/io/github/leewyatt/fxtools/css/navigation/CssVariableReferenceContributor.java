package io.github.leewyatt.fxtools.css.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Injects PsiReferences for CSS variable usages so Ctrl+hover highlights only the variable name.
 */
public class CssVariableReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                new CssVariableReferenceProvider()
        );
    }

    private static class CssVariableReferenceProvider extends PsiReferenceProvider {

        private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        private static final Pattern PROPERTY_LINE_PATTERN =
                Pattern.compile("([\\w-]+)\\s*:\\s*([^;{}]+?)\\s*;");
        private static final Pattern VARIABLE_PATTERN = Pattern.compile("-(?!fx-)[\\w][\\w-]*");

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            // In Ultimate, CSS has a rich PSI tree — skip non-leaf elements to avoid duplicates.
            // In Community, CSS is PsiPlainTextFileImpl (a PsiFile) which has children but must be processed.
            if (!(element instanceof PsiFile) && element.getFirstChild() != null) {
                return PsiReference.EMPTY_ARRAY;
            }
            PsiFile file = element instanceof PsiFile ? (PsiFile) element : element.getContainingFile();
            if (file == null) {
                return PsiReference.EMPTY_ARRAY;
            }
            VirtualFile vFile = file.getVirtualFile();
            if (vFile == null || !"css".equals(vFile.getExtension())) {
                return PsiReference.EMPTY_ARRAY;
            }

            int elementStart = element.getTextRange().getStartOffset();
            String fileText = file.getText();
            String cleaned = COMMENT_PATTERN.matcher(fileText).replaceAll(
                    match -> " ".repeat(match.group().length()));

            List<PsiReference> refs = new ArrayList<>();
            Matcher lineMatcher = PROPERTY_LINE_PATTERN.matcher(cleaned);
            while (lineMatcher.find()) {
                String value = lineMatcher.group(2);
                int valueStart = lineMatcher.start(2);

                Matcher varMatcher = VARIABLE_PATTERN.matcher(value);
                while (varMatcher.find()) {
                    int absStart = valueStart + varMatcher.start();
                    int absEnd = valueStart + varMatcher.end();

                    if (absStart < elementStart || absEnd > element.getTextRange().getEndOffset()) {
                        continue;
                    }

                    String varName = varMatcher.group();
                    TextRange rangeInElement = TextRange.create(absStart - elementStart, absEnd - elementStart);
                    refs.add(new CssVariableReference(element, rangeInElement, varName));
                }
            }

            return refs.toArray(PsiReference.EMPTY_ARRAY);
        }
    }

    private static class CssVariableReference extends PsiPolyVariantReferenceBase<PsiElement> {
        private final String variableName;

        CssVariableReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                             @NotNull String variableName) {
            super(element, rangeInElement, false);
            this.variableName = variableName;
        }

        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            PsiElement[] targets = CssVariableUtil.resolveTargets(myElement.getProject(), variableName);
            if (targets == null) {
                return ResolveResult.EMPTY_ARRAY;
            }
            ResolveResult[] results = new ResolveResult[targets.length];
            for (int i = 0; i < targets.length; i++) {
                results[i] = new PsiElementResolveResult(targets[i]);
            }
            return results;
        }

        @Override
        public @Nullable PsiElement resolve() {
            ResolveResult[] results = multiResolve(false);
            return results.length > 0 ? results[0].getElement() : null;
        }
    }
}
