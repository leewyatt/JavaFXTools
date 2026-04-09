package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers auto-popup completion when typing '-' or ':' inside
 * Java string literals or FXML style attributes that contain CSS.
 */
public class FxCssInlineTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project,
                                      @NotNull Editor editor, @NotNull PsiFile file) {
        if (c != '-' && c != ':') {
            return Result.CONTINUE;
        }

        String lang = file.getLanguage().getID();
        if (!"JAVA".equals(lang) && !"XML".equals(lang)) {
            return Result.CONTINUE;
        }

        // Commit PSI so findElementAt() sees the just-typed character
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset > 0 ? offset - 1 : offset);
        if (element == null) {
            return Result.CONTINUE;
        }

        // Check if inside a Java string literal
        if (isInsideJavaStringLiteral(element)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            return Result.CONTINUE;
        }

        // Check if inside an FXML style attribute
        if (isInsideFxmlStyleAttribute(element)) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        }

        return Result.CONTINUE;
    }

    private static boolean isInsideJavaStringLiteral(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiLiteralExpression lit) {
                Object value = lit.getValue();
                return value instanceof String;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean isInsideFxmlStyleAttribute(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlAttributeValue attrVal) {
                PsiElement parent = attrVal.getParent();
                if (parent instanceof XmlAttribute attr) {
                    return "style".equalsIgnoreCase(attr.getName());
                }
                return false;
            }
            current = current.getParent();
        }
        return false;
    }
}
