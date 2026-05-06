package io.github.leewyatt.fxtools.generate;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                            .createFileFromText("_Dummy_.java", JavaLanguage.INSTANCE, dummyClass);
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

                    if (dialog.isStyleableGenerated()) {
                        ensureStyleableProperties(editor, project, psiClass, dialog);
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

    private void ensureStyleableProperties(@NotNull Editor editor,
                                           @NotNull Project project,
                                           @NotNull PsiClass psiClass,
                                           @NotNull FxPropertyGenerateDialog dialog) {
        String propertyName = dialog.getPropertyName();
        String constName = toUpperSnakeCase(propertyName);
        PsiClass styleableProperties = findStyleablePropertiesClass(psiClass);

        if (styleableProperties == null) {
            createStyleableProperties(project, psiClass, dialog, constName);
            return;
        }

        appendToStyleableProperties(editor, project, styleableProperties, dialog, constName);
    }

    private void createStyleableProperties(@NotNull Project project,
                                           @NotNull PsiClass psiClass,
                                           @NotNull FxPropertyGenerateDialog dialog,
                                           @NotNull String constName) {
        PsiElement rBrace = psiClass.getRBrace();
        if (rBrace == null) {
            return;
        }

        PsiClass superClass = psiClass.getSuperClass();
        String superClassName = superClass != null && superClass.getName() != null
                ? superClass.getName() : "javafx.scene.Node";
        boolean useControlCssMetaData = isControlClass(project, psiClass);

        String code = generateStyleablePropertiesCode(
                psiClass.getName() != null ? psiClass.getName() : "MyControl",
                dialog.getPropertyName(),
                dialog.getPropertyType(),
                dialog.getCssName(),
                dialog.getCssDefaultReference(),
                constName,
                superClassName,
                useControlCssMetaData);

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiClass dummyClass = factory.createClassFromText(code, psiClass);
        for (PsiClass inner : dummyClass.getInnerClasses()) {
            psiClass.addBefore(inner, rBrace);
        }
        for (PsiMethod method : dummyClass.getMethods()) {
            if (psiClass.findMethodsByName(method.getName(), false).length == 0) {
                psiClass.addBefore(method, rBrace);
            }
        }
    }

    private void appendToStyleableProperties(@NotNull Editor editor,
                                             @NotNull Project project,
                                             @NotNull PsiClass styleableProperties,
                                             @NotNull FxPropertyGenerateDialog dialog,
                                             @NotNull String constName) {
        for (PsiField field : styleableProperties.getFields()) {
            if (constName.equals(field.getName())) {
                ApplicationManager.getApplication().invokeLater(() ->
                        HintManager.getInstance().showInformationHint(editor,
                                "CssMetaData field '" + constName
                                        + "' already exists in StyleableProperties."));
                return;
            }
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiField newField = createFieldFromText(factory, generateCssMetaDataFieldCode(
                styleableProperties.getContainingClass() != null
                        && styleableProperties.getContainingClass().getName() != null
                        ? styleableProperties.getContainingClass().getName() : "MyControl",
                dialog.getPropertyName(),
                dialog.getPropertyType(),
                dialog.getCssName(),
                dialog.getCssDefaultReference(),
                constName), styleableProperties);
        if (newField == null) {
            return;
        }

        PsiElement inserted = insertCssMetaDataField(styleableProperties, newField);
        if (inserted != null) {
            boolean updated = updateStaticBlock(factory, styleableProperties, constName);
            if (!updated) {
                ApplicationManager.getApplication().invokeLater(() ->
                        HintManager.getInstance().showInformationHint(editor,
                                "CssMetaData field added. Please add '" + constName
                                        + "' to the STYLEABLES list manually."));
            }
        }
    }

    @NotNull
    private String generateStyleablePropertiesCode(@NotNull String className,
                                                   @NotNull String propertyName,
                                                   @NotNull FxPropertyType type,
                                                   @NotNull String cssName,
                                                   @NotNull String defaultReference,
                                                   @NotNull String constName,
                                                   @NotNull String superClassName,
                                                   boolean useControlCssMetaData) {
        StringBuilder sb = new StringBuilder();
        sb.append("private static class StyleableProperties {\n\n");
        sb.append(generateCssMetaDataFieldCode(className, propertyName, type, cssName, defaultReference, constName));
        sb.append("\n");
        sb.append("    private static final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> STYLEABLES;\n\n");
        sb.append("    static {\n");
        sb.append("        final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> styleables =\n");
        sb.append("                new java.util.ArrayList<>(").append(superClassName)
                .append(".getClassCssMetaData());\n");
        sb.append("        java.util.Collections.addAll(styleables, ").append(constName).append(");\n");
        sb.append("        STYLEABLES = java.util.Collections.unmodifiableList(styleables);\n");
        sb.append("    }\n");
        sb.append("}\n\n");
        sb.append("public static java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getClassCssMetaData() {\n");
        sb.append("    return StyleableProperties.STYLEABLES;\n");
        sb.append("}\n\n");
        if (useControlCssMetaData) {
            sb.append("@Override\n");
            sb.append("protected java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getControlCssMetaData() {\n");
        } else {
            sb.append("@Override\n");
            sb.append("public java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> getCssMetaData() {\n");
        }
        sb.append("    return getClassCssMetaData();\n");
        sb.append("}\n");
        return sb.toString();
    }

    @NotNull
    private String generateCssMetaDataFieldCode(@NotNull String className,
                                                @NotNull String propertyName,
                                                @NotNull FxPropertyType type,
                                                @NotNull String cssName,
                                                @NotNull String defaultReference,
                                                @NotNull String constName) {
        String converterExpression = type.getConverterExpression();
        if (converterExpression == null) {
            converterExpression = "/* TODO: provide StyleConverter */";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    private static final javafx.css.CssMetaData<").append(className)
                .append(", ").append(type.getCssValueType()).append("> ").append(constName).append(" =\n");
        sb.append("            new javafx.css.CssMetaData<>(\"").append(cssName).append("\",\n");
        sb.append("                    ").append(converterExpression);
        if (!defaultReference.isEmpty()) {
            sb.append(", ").append(defaultReference);
        }
        sb.append(") {\n");
        sb.append("        @Override\n");
        sb.append("        public boolean isSettable(").append(className).append(" node) {\n");
        sb.append("            return node.").append(propertyName).append(" == null || !node.")
                .append(propertyName).append(".isBound();\n");
        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public javafx.css.StyleableProperty<").append(type.getCssValueType())
                .append("> getStyleableProperty(").append(className).append(" node) {\n");
        sb.append("            return (javafx.css.StyleableProperty<").append(type.getCssValueType())
                .append(">) node.").append(propertyName).append("Property();\n");
        sb.append("        }\n");
        sb.append("    };\n");
        return sb.toString();
    }

    @Nullable
    private PsiField createFieldFromText(@NotNull PsiElementFactory factory,
                                         @NotNull String fieldCode,
                                         @NotNull PsiElement context) {
        String wrappedCode = "class _Dummy_ { " + fieldCode + " }";
        PsiClass dummyClass = factory.createClassFromText(wrappedCode, context).getInnerClasses()[0];
        PsiField[] fields = dummyClass.getFields();
        return fields.length > 0 ? (PsiField) fields[0].copy() : null;
    }

    @Nullable
    private PsiElement insertCssMetaDataField(@NotNull PsiClass styleableProperties,
                                              @NotNull PsiField newField) {
        PsiField styleablesListField = null;
        PsiField lastCssMetaDataField = null;
        for (PsiField field : styleableProperties.getFields()) {
            String typeText = field.getType().getCanonicalText();
            if (typeText.contains("List") && typeText.contains("CssMetaData")) {
                styleablesListField = field;
            } else if (typeText.contains("CssMetaData")) {
                lastCssMetaDataField = field;
            }
        }

        if (styleablesListField != null) {
            return styleableProperties.addBefore(newField, styleablesListField);
        }
        if (lastCssMetaDataField != null) {
            return styleableProperties.addAfter(newField, lastCssMetaDataField);
        }
        PsiElement lBrace = styleableProperties.getLBrace();
        if (lBrace != null) {
            return styleableProperties.addAfter(newField, lBrace);
        }
        return null;
    }

    private boolean updateStaticBlock(@NotNull PsiElementFactory factory,
                                      @NotNull PsiClass styleableProperties,
                                      @NotNull String constName) {
        PsiClassInitializer staticInit = findStaticInitializer(styleableProperties);
        if (staticInit == null) {
            return false;
        }
        PsiCodeBlock body = staticInit.getBody();

        for (PsiStatement statement : body.getStatements()) {
            if (!(statement instanceof PsiExpressionStatement)) {
                continue;
            }
            PsiExpression expression = ((PsiExpressionStatement) statement).getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                continue;
            }
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            String methodName = call.getMethodExpression().getReferenceName();
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if ("addAll".equals(methodName) && qualifier != null
                    && qualifier.getText().endsWith("Collections")) {
                PsiExpressionList argList = call.getArgumentList();
                argList.add(factory.createExpressionFromText(constName, null));
                return true;
            }
        }

        PsiStatement lastAddStatement = null;
        String listVariableName = null;
        for (PsiStatement statement : body.getStatements()) {
            if (!(statement instanceof PsiExpressionStatement)) {
                continue;
            }
            PsiExpression expression = ((PsiExpressionStatement) statement).getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                continue;
            }
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            if ("add".equals(call.getMethodExpression().getReferenceName())) {
                lastAddStatement = statement;
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                if (qualifier != null) {
                    listVariableName = qualifier.getText();
                }
            }
        }

        if (lastAddStatement != null && listVariableName != null) {
            PsiStatement newStatement = factory.createStatementFromText(
                    listVariableName + ".add(" + constName + ");", null);
            body.addAfter(newStatement, lastAddStatement);
            return true;
        }
        return false;
    }

    @Nullable
    private PsiClassInitializer findStaticInitializer(@NotNull PsiClass psiClass) {
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                return initializer;
            }
        }
        return null;
    }

    @Nullable
    private PsiClass findStyleablePropertiesClass(@NotNull PsiClass parentClass) {
        for (PsiClass innerClass : parentClass.getInnerClasses()) {
            if ("StyleableProperties".equals(innerClass.getName())) {
                return innerClass;
            }
        }
        return null;
    }

    private boolean isControlClass(@NotNull Project project, @NotNull PsiClass psiClass) {
        PsiClass controlClass = JavaPsiFacade.getInstance(project)
                .findClass("javafx.scene.control.Control", GlobalSearchScope.allScope(project));
        return controlClass != null && psiClass.isInheritor(controlClass, true);
    }

    @NotNull
    private String toUpperSnakeCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(camelCase.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private PsiElement findInsertionAnchor(@NotNull PsiClass psiClass) {
        PsiField[] fields = psiClass.getFields();
        if (fields.length > 0) {
            return fields[fields.length - 1];
        }
        return psiClass.getLBrace();
    }
}
