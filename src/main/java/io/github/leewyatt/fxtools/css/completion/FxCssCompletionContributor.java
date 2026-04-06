package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.leewyatt.fxtools.css.FxCssPropertyTable;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;

/**
 * Provides -fx-* property name completion and CSS variable completion in .css files.
 * Uses fillCompletionVariants instead of extend() pattern to work in Community Edition
 * where CSS files are treated as plain text.
 */
public class FxCssCompletionContributor extends CompletionContributor {

    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        PsiFile file = position.getContainingFile();
        if (file == null) {
            return false;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return false;
        }
        return typeChar == '-' || typeChar == ':';
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        VirtualFile vFile = parameters.getOriginalFile().getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return;
        }
        if (!FxDetector.isJavaFxProject(parameters.getOriginalFile().getProject())) {
            return;
        }

        Editor editor = parameters.getEditor();
        Document document = editor.getDocument();
        int offset = parameters.getOffset();
        String text = document.getText();

        // Extract CSS token prefix (hyphens are part of CSS identifiers)
        int tokenStart = offset;
        while (tokenStart > 0 && FxCssCompletionUtil.isCssTokenChar(text.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        String prefix = text.substring(tokenStart, offset);

        CompletionResultSet cssResult = result.withPrefixMatcher(
                new PlainPrefixMatcher(prefix));

        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        String linePrefix = document.getText(TextRange.create(lineStart, offset));

        Project project = parameters.getOriginalFile().getProject();
        if (!FxCssCompletionUtil.isInsideDeclarationBlock(text, offset)) {
            return;
        }
        if (!linePrefix.contains(":")) {
            FxCssCompletionUtil.addPropertyNameCompletions(cssResult,
                    FxCssCompletionUtil.CSS_PROPERTY_INSERT_HANDLER, project);
        } else {
            String propName = FxCssCompletionUtil.extractPropertyName(linePrefix);
            FxCssPropertyTable.PropertyInfo info = propName != null
                    ? FxCssPropertyTable.getProperty(propName, project) : null;
            FxCssCompletionUtil.addValueCompletions(info, propName, parameters, cssResult,
                    FxCssCompletionUtil.CSS_VALUE_INSERT_HANDLER);
        }
    }
}
