package io.github.leewyatt.fxtools.fxml.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that changes an @FXML field type to match the FXML element type.
 */
public class ChangeFieldTypeQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(ChangeFieldTypeQuickFix.class);
    private final String correctTypeFqn;

    public ChangeFieldTypeQuickFix(@NotNull String correctTypeFqn) {
        this.correctTypeFqn = correctTypeFqn;
    }

    @Override
    public @NotNull String getFamilyName() {
        String simpleName = correctTypeFqn.contains(".")
                ? correctTypeFqn.substring(correctTypeFqn.lastIndexOf('.') + 1)
                : correctTypeFqn;
        return FxToolsBundle.message("quickfix.change.field.type", simpleName);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class, false);
        if (field == null) {
            return;
        }

        PsiTypeElement typeElement = field.getTypeElement();
        if (typeElement == null) {
            return;
        }

        String typeText = FxFileResolver.buildGenericTypeText(correctTypeFqn, project);

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                PsiTypeElement newTypeElement = factory.createTypeElementFromText(typeText, field);
                PsiElement replaced = typeElement.replace(newTypeElement);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
            } catch (Exception ex) {
                LOG.error("Failed to change field type", ex);
            }
        });
    }
}
