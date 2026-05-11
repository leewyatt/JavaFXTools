package io.github.leewyatt.fxtools.generate;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertiesIntegrator;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertyDescriptor;
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
        PsiClass superClass = psiClass.getSuperClass();
        String superClassName = superClass != null && superClass.getName() != null
                ? superClass.getName() : "javafx.scene.Node";
        FxPropertyGenerateDialog dialog = new FxPropertyGenerateDialog(project, className,
                superClassName, isControlClass(project, psiClass),
                findStyleablePropertiesClass(psiClass) != null);
        if (!dialog.showAndGet()) {
            return;
        }

        String propertyName = dialog.getPropertyName();
        if (hasPropertyMemberConflict(psiClass, dialog.getPropertyType(), propertyName)) {
            HintManager.getInstance().showInformationHint(editor,
                    "JavaFX property '" + propertyName + "' already exists in this class.");
            return;
        }

        String code = dialog.getGeneratedCode();
        if (code.isEmpty()) {
            return;
        }
        StyleablePropertyDescriptor styleableDescriptor =
                dialog.isStyleableGenerated() ? dialog.getStyleableDescriptor() : null;

        WriteCommandAction.runWriteCommandAction(project,
                FxToolsBundle.message("generate.fx.property.title"), null, () -> {
                    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

                    int caretOffset = editor.getCaretModel().getOffset();
                    PsiElement anchor = findInsertionAnchor(psiClass, caretOffset);

                    // Parse the entire generated code as a dummy class body and extract members
                    String dummyClass = "class _Dummy_ {\n" + code + "\n}";
                    PsiJavaFile dummyFile = (PsiJavaFile) PsiFileFactory.getInstance(project)
                            .createFileFromText("_Dummy_.java", JavaLanguage.INSTANCE, dummyClass);
                    PsiClass dummyPsiClass = dummyFile.getClasses()[0];

                    PsiElement constantAnchor = findDefaultConstantAnchor(psiClass);
                    boolean propertyStartsAtClassTop = anchor == psiClass.getLBrace();
                    for (PsiField field : dummyPsiClass.getFields()) {
                        if (!isDefaultConstantField(field)) {
                            continue;
                        }
                        PsiElement added = psiClass.addAfter(field, constantAnchor);
                        constantAnchor = added;
                        if (propertyStartsAtClassTop) {
                            anchor = added;
                        }
                    }

                    for (PsiField field : dummyPsiClass.getFields()) {
                        if (isDefaultConstantField(field)) {
                            continue;
                        }
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

                    if (styleableDescriptor != null) {
                        ensureStyleableProperties(editor, project, psiClass, styleableDescriptor);
                    }

                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
                    CodeStyleManager.getInstance(project).reformat(psiClass);
                    if (styleableDescriptor != null) {
                        // The whole-class reformat above can shift offsets after integrate() focused the T0DO.
                        StyleablePropertiesIntegrator.focusTodoConverter(editor, psiClass, styleableDescriptor);
                    }
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

    private boolean hasPropertyMemberConflict(@NotNull PsiClass psiClass,
                                              @NotNull FxPropertyType type,
                                              @NotNull String propertyName) {
        if (psiClass.findFieldByName(propertyName, false) != null) {
            return true;
        }

        String capitalizedName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        String getterName = type.getGetterPrefix() + capitalizedName;
        return psiClass.findMethodsByName(getterName, false).length > 0
                || psiClass.findMethodsByName("set" + capitalizedName, false).length > 0
                || psiClass.findMethodsByName(propertyName + "Property", false).length > 0;
    }

    private void ensureStyleableProperties(@NotNull Editor editor,
                                           @NotNull Project project,
                                           @NotNull PsiClass psiClass,
                                           @NotNull StyleablePropertyDescriptor descriptor) {
        StyleablePropertiesIntegrator.integrate(project, psiClass, descriptor, editor);
    }

    private PsiElement findInsertionAnchor(@NotNull PsiClass psiClass, int caretOffset) {
        PsiElement anchor = psiClass.getLBrace();
        for (PsiElement child : psiClass.getChildren()) {
            if (!isClassMember(child)) {
                continue;
            }
            if (child.getTextOffset() <= caretOffset) {
                anchor = child;
            }
        }
        return anchor;
    }

    private PsiElement findDefaultConstantAnchor(@NotNull PsiClass psiClass) {
        PsiElement anchor = psiClass.getLBrace();
        for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiField) {
                PsiField field = (PsiField) child;
                if (field.hasModifierProperty(PsiModifier.STATIC)
                        && field.hasModifierProperty(PsiModifier.FINAL)) {
                    anchor = field;
                    continue;
                }
                break;
            }
            if (isClassMember(child)) {
                break;
            }
        }
        return anchor;
    }

    private boolean isDefaultConstantField(@NotNull PsiField field) {
        String name = field.getName();
        return name != null
                && name.startsWith("DEFAULT_")
                && field.hasModifierProperty(PsiModifier.STATIC)
                && field.hasModifierProperty(PsiModifier.FINAL);
    }

    private boolean isClassMember(@NotNull PsiElement element) {
        return element instanceof PsiField
                || element instanceof PsiMethod
                || element instanceof PsiClass
                || element instanceof PsiClassInitializer;
    }

    private boolean isControlClass(@NotNull Project project, @NotNull PsiClass psiClass) {
        PsiClass controlClass = JavaPsiFacade.getInstance(project)
                .findClass("javafx.scene.control.Control", GlobalSearchScope.allScope(project));
        return controlClass != null && psiClass.isInheritor(controlClass, true);
    }

    private PsiClass findStyleablePropertiesClass(@NotNull PsiClass parentClass) {
        for (PsiClass innerClass : parentClass.getInnerClasses()) {
            if ("StyleableProperties".equals(innerClass.getName())) {
                return innerClass;
            }
        }
        return null;
    }
}
