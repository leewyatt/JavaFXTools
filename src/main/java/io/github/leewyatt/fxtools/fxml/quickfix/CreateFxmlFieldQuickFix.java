package io.github.leewyatt.fxtools.fxml.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick fix that generates an @FXML field in the Controller class for a given fx:id.
 */
public class CreateFxmlFieldQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(CreateFxmlFieldQuickFix.class);

    private final String controllerFqn;
    private final String fieldName;
    private final String elementTagName;
    private final VirtualFile fxmlFile;

    public CreateFxmlFieldQuickFix(@NotNull String controllerFqn, @NotNull String fieldName,
                                   @NotNull String elementTagName, @NotNull VirtualFile fxmlFile) {
        this.controllerFqn = controllerFqn;
        this.fieldName = fieldName;
        this.elementTagName = elementTagName;
        this.fxmlFile = fxmlFile;
    }

    @Override
    public @NotNull String getFamilyName() {
        String simpleCtrl = controllerFqn.contains(".")
                ? controllerFqn.substring(controllerFqn.lastIndexOf('.') + 1)
                : controllerFqn;
        return FxToolsBundle.message("quickfix.create.fxml.field", fieldName, simpleCtrl);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass controllerClass = JavaPsiFacade.getInstance(project)
                .findClass(controllerFqn, GlobalSearchScope.projectScope(project));
        if (controllerClass == null) {
            return;
        }

        String typeFqn = FxFileResolver.resolveTagNameToFqn(elementTagName, fxmlFile, project);
        if (typeFqn == null) {
            typeFqn = "javafx.scene.Node";
        }
        String typeText = FxFileResolver.buildGenericTypeText(typeFqn, project);

        String finalTypeText = typeText;
        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                PsiField field = factory.createFieldFromText(
                        "@javafx.fxml.FXML\nprivate " + finalTypeText + " " + fieldName + ";",
                        controllerClass);

                PsiElement anchor = findFieldInsertionAnchor(controllerClass);
                PsiElement added;
                if (anchor != null) {
                    added = controllerClass.addAfter(field, anchor);
                } else {
                    added = controllerClass.add(field);
                }

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);

                PsiFile containingFile = controllerClass.getContainingFile();
                if (containingFile != null) {
                    VirtualFile vf = containingFile.getVirtualFile();
                    if (vf != null) {
                        FileEditorManager.getInstance(project).openTextEditor(
                                new OpenFileDescriptor(project, vf, added.getTextOffset()), true);
                    }
                }
            } catch (Exception ex) {
                LOG.error("Failed to create @FXML field", ex);
            }
        });
    }

    @Nullable
    private PsiElement findFieldInsertionAnchor(@NotNull PsiClass psiClass) {
        PsiField[] fields = psiClass.getFields();
        if (fields.length > 0) {
            return fields[fields.length - 1];
        }
        return psiClass.getLBrace();
    }
}
