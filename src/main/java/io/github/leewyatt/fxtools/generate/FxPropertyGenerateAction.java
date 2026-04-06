package io.github.leewyatt.fxtools.generate;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Generate menu action for creating JavaFX Property fields with getter/setter/property methods.
 */
public class FxPropertyGenerateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || psiFile == null) {
            return;
        }

        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass == null) {
            return;
        }

        String className = psiClass.getName() != null ? psiClass.getName() : "MyControl";
        FxPropertyGenerateDialog dialog = new FxPropertyGenerateDialog(project, className);
        if (!dialog.showAndGet()) {
            return;
        }

        String code = dialog.getGeneratedCode();
        if (code.isEmpty()) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project,
                FxToolsBundle.message("generate.fx.property.title"), null, () -> {
                    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

                    PsiElement anchor = findInsertionAnchor(psiClass);

                    // Parse the entire generated code as a dummy class body and extract members
                    String dummyClass = "class _Dummy_ {\n" + code + "\n}";
                    PsiJavaFile dummyFile = (PsiJavaFile) PsiFileFactory.getInstance(project)
                            .createFileFromText("_Dummy_.java", com.intellij.lang.java.JavaLanguage.INSTANCE, dummyClass);
                    PsiClass dummyPsiClass = dummyFile.getClasses()[0];

                    for (PsiField field : dummyPsiClass.getFields()) {
                        PsiElement added;
                        if (anchor != null) {
                            added = psiClass.addAfter(field, anchor);
                        } else {
                            added = psiClass.add(field);
                        }
                        anchor = added;
                    }

                    for (PsiMethod method : dummyPsiClass.getMethods()) {
                        PsiElement added;
                        if (anchor != null) {
                            added = psiClass.addAfter(method, anchor);
                        } else {
                            added = psiClass.add(method);
                        }
                        anchor = added;
                    }

                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
                    CodeStyleManager.getInstance(project).reformat(psiClass);
                });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        boolean visible = false;
        if (project != null && editor != null && psiFile instanceof PsiJavaFile) {
            PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
            PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (psiClass != null && !psiClass.isInterface() && !psiClass.isAnnotationType()) {
                visible = true;
            }
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiElement findInsertionAnchor(@NotNull PsiClass psiClass) {
        PsiField[] fields = psiClass.getFields();
        if (fields.length > 0) {
            return fields[fields.length - 1];
        }
        return psiClass.getLBrace();
    }
}
