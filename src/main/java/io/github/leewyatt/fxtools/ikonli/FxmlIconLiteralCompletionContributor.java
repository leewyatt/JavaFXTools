package io.github.leewyatt.fxtools.ikonli;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import io.github.leewyatt.fxtools.css.completion.FxCssCompletionUtil;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides Ikonli icon literal completion inside FXML
 * {@code <FontIcon iconLiteral="..."/>} attribute values.
 */
public class FxmlIconLiteralCompletionContributor extends CompletionContributor {

    /** No-op handler: XML attribute value completion inserts the literal text as-is. */
    private static final InsertHandler<LookupElement> NOOP_INSERT_HANDLER = (ctx, item) -> { };

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"fxml".equalsIgnoreCase(vFile.getExtension())) {
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

        XmlAttributeValue attrValue = findParentXmlAttributeValue(position);
        if (attrValue == null || !isIconLiteralOnFontIcon(attrValue)) {
            return;
        }

        // Use PlainPrefixMatcher so dashes in literals (e.g. "fa-home") are not
        // treated as word separators by the default matcher.
        int attrStart = attrValue.getValueTextRange().getStartOffset();
        int caret = parameters.getOffset();
        String fullText = attrValue.getValue();
        if (fullText == null) {
            fullText = "";
        }
        int prefixLen = Math.max(0, Math.min(caret - attrStart, fullText.length()));
        String prefix = fullText.substring(0, prefixLen);

        CompletionResultSet xmlResult = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
        FxCssCompletionUtil.addIconCodeCompletions(project, xmlResult, NOOP_INSERT_HANDLER);
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

    private static boolean isIconLiteralOnFontIcon(@NotNull XmlAttributeValue attrValue) {
        PsiElement parent = attrValue.getParent();
        if (!(parent instanceof XmlAttribute attr)) {
            return false;
        }
        if (!"iconLiteral".equals(attr.getName())) {
            return false;
        }
        XmlTag tag = attr.getParent();
        return tag != null && IkonliFxmlUtil.isFontIconTag(tag);
    }
}
