package io.github.leewyatt.fxtools.generate.styleable;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Integrates generated CssMetaData fields into a JavaFX control class.
 */
public final class StyleablePropertiesIntegrator {

    private StyleablePropertiesIntegrator() {
    }

    /**
     * Creates or updates the containing class's StyleableProperties structure.
     *
     * @param project current IntelliJ project
     * @param psiClass class that owns the generated JavaFX property
     * @param descriptor styleable property metadata to integrate
     * @param editorForHints optional editor used to show generation hints
     */
    public static void integrate(@NotNull Project project,
                                 @NotNull PsiClass psiClass,
                                 @NotNull StyleablePropertyDescriptor descriptor,
                                 @Nullable Editor editorForHints) {
        PsiClass styleableProperties = findStyleablePropertiesClass(psiClass);
        if (styleableProperties == null) {
            createStyleableProperties(project, psiClass, descriptor, editorForHints);
            return;
        }

        appendToStyleableProperties(project, styleableProperties, descriptor, editorForHints);
    }

    private static void createStyleableProperties(@NotNull Project project,
                                                  @NotNull PsiClass psiClass,
                                                  @NotNull StyleablePropertyDescriptor descriptor,
                                                  @Nullable Editor editorForHints) {
        PsiElement rBrace = psiClass.getRBrace();
        if (rBrace == null) {
            return;
        }

        if (hasExistingCssMetaDataMethod(psiClass)) {
            showHint(editorForHints, "Existing getCssMetaData() found but no StyleableProperties class. "
                    + "Generated CssMetaData was added, but you may need to wire "
                    + "StyleableProperties.STYLEABLES into your existing CSS metadata method manually.");
        }

        PsiClass superClass = psiClass.getSuperClass();
        String superClassName = superClass != null && superClass.getName() != null
                ? superClass.getName() : "javafx.scene.Node";
        boolean useControlCssMetaData = isControlClass(project, psiClass);
        String className = psiClass.getName() != null ? psiClass.getName() : "MyControl";

        String code = generateStyleablePropertiesCode(className, descriptor,
                superClassName, useControlCssMetaData);

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

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
        CodeStyleManager.getInstance(project).reformat(psiClass);
    }

    private static void appendToStyleableProperties(@NotNull Project project,
                                                    @NotNull PsiClass styleableProperties,
                                                    @NotNull StyleablePropertyDescriptor descriptor,
                                                    @Nullable Editor editorForHints) {
        for (PsiField field : styleableProperties.getFields()) {
            if (descriptor.constName().equals(field.getName())) {
                showHint(editorForHints,
                        "CssMetaData field '" + descriptor.constName()
                                + "' already exists in StyleableProperties.");
                return;
            }
        }

        PsiClass containingClass = styleableProperties.getContainingClass();
        String className = containingClass != null && containingClass.getName() != null
                ? containingClass.getName() : "MyControl";

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiField newField = createFieldFromText(factory,
                generateCssMetaDataFieldCode(className, descriptor), styleableProperties);
        if (newField == null) {
            return;
        }

        PsiElement inserted = insertCssMetaDataField(styleableProperties, newField);
        if (inserted != null) {
            boolean updated = updateStaticBlock(factory, styleableProperties, descriptor.constName());
            if (!updated) {
                showHint(editorForHints,
                        "CssMetaData field added. Please add '" + descriptor.constName()
                                + "' to the STYLEABLES list manually.");
            }
        }

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(styleableProperties);
        CodeStyleManager.getInstance(project).reformat(styleableProperties);
    }

    @NotNull
    private static String generateStyleablePropertiesCode(@NotNull String className,
                                                          @NotNull StyleablePropertyDescriptor descriptor,
                                                          @NotNull String superClassName,
                                                          boolean useControlCssMetaData) {
        StringBuilder sb = new StringBuilder();
        sb.append("private static class StyleableProperties {\n\n");
        sb.append(generateCssMetaDataFieldCode(className, descriptor));
        sb.append("\n");
        sb.append("    private static final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> STYLEABLES;\n\n");
        sb.append("    static {\n");
        sb.append("        final java.util.List<javafx.css.CssMetaData<? extends javafx.css.Styleable, ?>> styleables =\n");
        sb.append("                new java.util.ArrayList<>(").append(superClassName)
                .append(".getClassCssMetaData());\n");
        sb.append("        java.util.Collections.addAll(styleables, ").append(descriptor.constName()).append(");\n");
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
    private static String generateCssMetaDataFieldCode(@NotNull String className,
                                                       @NotNull StyleablePropertyDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("    private static final javafx.css.CssMetaData<").append(className)
                .append(", ").append(descriptor.cssValueType()).append("> ")
                .append(descriptor.constName()).append(" =\n");
        sb.append("            new javafx.css.CssMetaData<>(\"").append(descriptor.cssName()).append("\",\n");
        sb.append("                    ").append(descriptor.converterExpression());
        if (!descriptor.defaultReference().isEmpty()) {
            sb.append(", ").append(descriptor.defaultReference());
        }
        sb.append(") {\n");
        sb.append("        @Override\n");
        sb.append("        public boolean isSettable(").append(className).append(" node) {\n");
        sb.append("            return node.").append(descriptor.propertyName()).append(" == null || !node.")
                .append(descriptor.propertyName()).append(".isBound();\n");
        sb.append("        }\n");
        sb.append("        @Override\n");
        sb.append("        public javafx.css.StyleableProperty<").append(descriptor.cssValueType())
                .append("> getStyleableProperty(").append(className).append(" node) {\n");
        sb.append("            return (javafx.css.StyleableProperty<").append(descriptor.cssValueType())
                .append(">) node.").append(descriptor.propertyName()).append("Property();\n");
        sb.append("        }\n");
        sb.append("    };\n");
        return sb.toString();
    }

    @Nullable
    private static PsiField createFieldFromText(@NotNull PsiElementFactory factory,
                                                @NotNull String fieldCode,
                                                @NotNull PsiElement context) {
        String wrappedCode = "class _Dummy_ { " + fieldCode + " }";
        PsiClass dummyClass = factory.createClassFromText(wrappedCode, context).getInnerClasses()[0];
        PsiField[] fields = dummyClass.getFields();
        return fields.length > 0 ? (PsiField) fields[0].copy() : null;
    }

    @Nullable
    private static PsiElement insertCssMetaDataField(@NotNull PsiClass styleableProperties,
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

    private static boolean updateStaticBlock(@NotNull PsiElementFactory factory,
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
    private static PsiClassInitializer findStaticInitializer(@NotNull PsiClass psiClass) {
        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
            if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                return initializer;
            }
        }
        return null;
    }

    @Nullable
    private static PsiClass findStyleablePropertiesClass(@NotNull PsiClass parentClass) {
        for (PsiClass innerClass : parentClass.getInnerClasses()) {
            if ("StyleableProperties".equals(innerClass.getName())) {
                return innerClass;
            }
        }
        return null;
    }

    private static boolean isControlClass(@NotNull Project project, @NotNull PsiClass psiClass) {
        PsiClass controlClass = JavaPsiFacade.getInstance(project)
                .findClass("javafx.scene.control.Control", GlobalSearchScope.allScope(project));
        return controlClass != null && psiClass.isInheritor(controlClass, true);
    }

    private static boolean hasExistingCssMetaDataMethod(@NotNull PsiClass psiClass) {
        return psiClass.findMethodsByName("getCssMetaData", false).length > 0
                || psiClass.findMethodsByName("getControlCssMetaData", false).length > 0;
    }

    private static void showHint(@Nullable Editor editor, @NotNull String message) {
        if (editor == null) {
            return;
        }
        // Hints may be requested from inside a write command; defer UI work to the EDT afterward.
        ApplicationManager.getApplication().invokeLater(() ->
                HintManager.getInstance().showInformationHint(editor, message));
    }
}
