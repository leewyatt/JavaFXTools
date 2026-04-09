package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import io.github.leewyatt.fxtools.css.FxCssPropertyTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides -fx-* CSS property name and value completion inside:
 * <ul>
 *   <li>Java: {@code node.setStyle("-fx-...")}</li>
 *   <li>FXML: {@code style="-fx-..."}</li>
 * </ul>
 */
public class FxCssInlineCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        int offset = parameters.getOffset();
        String cssText = null;
        int cssOffset = -1;

        // Use getPosition() (copy file PSI with dummy identifier) for detection —
        // getOriginalPosition() can be null at string boundaries or when PSI is stale
        PsiElement position = parameters.getPosition();

        // Case 1: Java string literal — node.setStyle("-fx-...")
        PsiLiteralExpression literal = findParentLiteral(position);
        if (literal != null && literal.getValue() instanceof String) {
            // Extract CSS text from original file (not copy) to avoid dummy identifier
            String fileText = parameters.getOriginalFile().getText();
            int[] bounds = findStringContentBounds(fileText, offset);
            if (bounds != null) {
                cssText = fileText.substring(bounds[0], bounds[1]);
                cssOffset = offset - bounds[0];
            }
        }

        // Case 2: FXML attribute — style="-fx-..."
        if (cssText == null) {
            PsiElement originalPos = parameters.getOriginalPosition();
            if (originalPos != null) {
                XmlAttributeValue attrValue = findParentXmlAttributeValue(originalPos);
                if (attrValue != null && isStyleAttribute(attrValue)) {
                    String attrText = attrValue.getValue();
                    if (attrText != null) {
                        int attrStart = attrValue.getValueTextRange().getStartOffset();
                        cssText = attrText;
                        cssOffset = offset - attrStart;
                    }
                }
            }
        }

        if (cssText == null || cssOffset < 0 || cssOffset > cssText.length()) {
            return;
        }

        // Extract CSS token prefix at caret position within the CSS text
        int tokenStart = cssOffset;
        while (tokenStart > 0 && FxCssCompletionUtil.isCssTokenChar(cssText.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        String prefix = cssText.substring(tokenStart, cssOffset);

        CompletionResultSet cssResult = result.withPrefixMatcher(
                new PlainPrefixMatcher(prefix));

        // Find the CSS segment containing the caret (segments separated by ';')
        String segment = extractCurrentSegment(cssText, cssOffset);

        if (!segment.contains(":")) {
            FxCssCompletionUtil.addPropertyNameCompletions(cssResult,
                    FxCssCompletionUtil.INLINE_PROPERTY_INSERT_HANDLER,
                    parameters.getOriginalFile().getProject());
        } else {
            String propName = FxCssCompletionUtil.extractPropertyName(segment);
            FxCssPropertyTable.PropertyInfo info = propName != null
                    ? FxCssPropertyTable.getProperty(propName, parameters.getOriginalFile().getProject()) : null;
            FxCssCompletionUtil.addValueCompletions(info, propName, parameters, cssResult,
                    FxCssCompletionUtil.INLINE_VALUE_INSERT_HANDLER);
            cssResult.stopHere();
        }
    }

    /**
     * Extracts the current CSS segment (between semicolons) that contains the caret offset.
     */
    @NotNull
    private static String extractCurrentSegment(@NotNull String cssText, int offset) {
        // Find the last ';' before offset
        int segStart = cssText.lastIndexOf(';', offset - 1);
        segStart = segStart < 0 ? 0 : segStart + 1;

        // Find the next ';' after offset
        int segEnd = cssText.indexOf(';', offset);
        if (segEnd < 0) {
            segEnd = cssText.length();
        }

        return cssText.substring(segStart, Math.min(offset, segEnd));
    }

    /**
     * Finds the content boundaries of the enclosing string literal in the file text.
     *
     * @return [contentStart, contentEnd] (after opening quote, before closing quote), or null
     */
    @Nullable
    private static int[] findStringContentBounds(@NotNull String text, int offset) {
        // Walk backward to find opening quote (handling escaped quotes)
        int start = offset - 1;
        while (start >= 0) {
            if (text.charAt(start) == '"' && !isEscaped(text, start)) {
                break;
            }
            start--;
        }
        if (start < 0) {
            return null;
        }

        // Walk forward to find closing quote
        int end = offset;
        while (end < text.length()) {
            if (text.charAt(end) == '"' && !isEscaped(text, end)) {
                break;
            }
            end++;
        }
        if (end >= text.length()) {
            return null;
        }

        return new int[]{start + 1, end};
    }

    private static boolean isEscaped(@NotNull String text, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 != 0;
    }

    @Nullable
    private static PsiLiteralExpression findParentLiteral(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiLiteralExpression lit) {
                return lit;
            }
            current = current.getParent();
        }
        return null;
    }

    @Nullable
    private static XmlAttributeValue findParentXmlAttributeValue(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlAttributeValue av) {
                return av;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isStyleAttribute(@NotNull XmlAttributeValue attrValue) {
        PsiElement parent = attrValue.getParent();
        if (parent instanceof XmlAttribute attr) {
            return "style".equalsIgnoreCase(attr.getName());
        }
        return false;
    }
}
