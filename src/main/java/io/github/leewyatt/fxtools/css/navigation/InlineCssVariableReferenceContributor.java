package io.github.leewyatt.fxtools.css.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides Ctrl+Click navigation for CSS variable references inside
 * Java {@code setStyle("...")} strings and FXML {@code style="..."} attributes.
 */
public class InlineCssVariableReferenceContributor extends PsiReferenceContributor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("-(?!fx-)[\\w][\\w-]*");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Java string literals
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new JavaStyleReferenceProvider()
        );
        // FXML attribute values
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new FxmlStyleReferenceProvider()
        );
    }

    /**
     * Provides references for CSS variables inside Java setStyle("...") string literals.
     */
    private static class JavaStyleReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof PsiLiteralExpression literal)) {
                return PsiReference.EMPTY_ARRAY;
            }
            Object value = literal.getValue();
            if (!(value instanceof String cssText)) {
                return PsiReference.EMPTY_ARRAY;
            }
            if (!isInsideSetStyleCall(literal)) {
                return PsiReference.EMPTY_ARRAY;
            }

            // Offset from element start to CSS content start
            int contentOffset;
            if (literal.isTextBlock()) {
                String rawText = literal.getText();
                int nlIdx = rawText.indexOf('\n');
                contentOffset = nlIdx >= 0 ? nlIdx + 1 : 4;
                // For text blocks, scan the raw content (preserving line offsets)
                int contentEndIdx = rawText.lastIndexOf("\"\"\"");
                if (contentEndIdx <= contentOffset) {
                    return PsiReference.EMPTY_ARRAY;
                }
                String rawContent = rawText.substring(contentOffset, contentEndIdx);
                return findVariableRefs(element, rawContent, contentOffset);
            } else {
                contentOffset = 1; // skip opening quote
                return findVariableRefs(element, cssText, contentOffset);
            }
        }
    }

    /**
     * Provides references for CSS variables inside FXML style="..." attributes.
     */
    private static class FxmlStyleReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof XmlAttributeValue attrValue)) {
                return PsiReference.EMPTY_ARRAY;
            }
            PsiElement parent = attrValue.getParent();
            if (!(parent instanceof XmlAttribute attr) || !"style".equalsIgnoreCase(attr.getName())) {
                return PsiReference.EMPTY_ARRAY;
            }

            String cssText = attrValue.getValue();
            if (cssText == null || cssText.isEmpty()) {
                return PsiReference.EMPTY_ARRAY;
            }

            // Offset from element start to value content start
            // XmlAttributeValue text is: "value", so content starts at 1
            int contentOffset = attrValue.getText().indexOf(cssText);
            if (contentOffset < 0) {
                contentOffset = 1;
            }

            return findVariableRefs(element, cssText, contentOffset);
        }
    }

    /**
     * Scans CSS text for variable references and creates PsiReferences.
     *
     * @param host          the host PsiElement (string literal or attribute value)
     * @param cssText       the CSS text to scan
     * @param contentOffset offset from host element start to cssText start
     */
    @NotNull
    private static PsiReference[] findVariableRefs(@NotNull PsiElement host,
                                                    @NotNull String cssText,
                                                    int contentOffset) {
        List<PsiReference> refs = new ArrayList<>();
        Matcher m = VARIABLE_PATTERN.matcher(cssText);

        while (m.find()) {
            String varName = m.group();
            int varStart = m.start();

            // Skip definition sites (variable followed by ':')
            if (isDefinitionSite(cssText, varStart, varName)) {
                continue;
            }

            int rangeStart = contentOffset + varStart;
            int rangeEnd = contentOffset + m.end();
            TextRange range = new TextRange(rangeStart, rangeEnd);

            refs.add(new InlineCssVariableReference(host, range, varName));
        }

        return refs.toArray(PsiReference.EMPTY_ARRAY);
    }

    private static boolean isInsideSetStyleCall(@NotNull PsiLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof PsiExpressionList)) {
            return false;
        }
        PsiElement grandparent = parent.getParent();
        if (!(grandparent instanceof PsiMethodCallExpression call)) {
            return false;
        }
        PsiReferenceExpression methodExpr = call.getMethodExpression();
        return "setStyle".equals(methodExpr.getReferenceName());
    }

    private static boolean isDefinitionSite(@NotNull String text, int varStart, @NotNull String varName) {
        int i = varStart + varName.length();
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i < text.length() && text.charAt(i) == ':';
    }

    private static class InlineCssVariableReference extends PsiPolyVariantReferenceBase<PsiElement> {
        private final String variableName;

        InlineCssVariableReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
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
