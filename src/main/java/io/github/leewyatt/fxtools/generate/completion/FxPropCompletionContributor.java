package io.github.leewyatt.fxtools.generate.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ProcessingContext;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Completion contributor for JavaFX Property code generation.
 * Type fxpstr/fxpint/fxpbool/... then select options in a popup.
 */
public class FxPropCompletionContributor extends CompletionContributor {

    public FxPropCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new FxPropProvider());
    }

    private static class FxPropProvider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet resultSet) {
            PsiFile file = parameters.getOriginalFile();
            if (!(file instanceof PsiJavaFile)) {
                return;
            }

            PsiElement pos = parameters.getPosition();
            PsiClass psiClass = PsiTreeUtil.getParentOfType(pos, PsiClass.class);
            if (psiClass == null) {
                return;
            }
            if (PsiTreeUtil.getParentOfType(pos, PsiMethod.class) != null) {
                return;
            }

            String prefix = resultSet.getPrefixMatcher().getPrefix();
            if (prefix.length() < 2) {
                return;
            }
            if (!"fxp".startsWith(prefix) && !prefix.startsWith("fxp")) {
                return;
            }

            String className = psiClass.getName() != null ? psiClass.getName() : "YourControl";

            for (FxPropCodeGenerator.PropType type : FxPropCodeGenerator.PropType.values()) {
                String displayType = type.propertyClass;
                if (type.singleGeneric) {
                    displayType += "<T>";
                } else if (type.dualGeneric) {
                    displayType += "<K,V>";
                }

                LookupElementBuilder builder = LookupElementBuilder.create(type.abbrev)
                        .withIcon(AllIcons.Nodes.Property)
                        .withTypeText(displayType, true)
                        .withInsertHandler(new FxPropInsertHandler(type, className, psiClass))
                        .bold();

                resultSet.addElement(PrioritizedLookupElement.withPriority(builder, 300));
            }
        }
    }

    private static class FxPropInsertHandler implements InsertHandler<LookupElement> {

        private final FxPropCodeGenerator.PropType type;
        private final String className;
        private final PsiClass psiClass;

        FxPropInsertHandler(@NotNull FxPropCodeGenerator.PropType type,
                            @NotNull String className,
                            @NotNull PsiClass psiClass) {
            this.type = type;
            this.className = className;
            this.psiClass = psiClass;
        }

        @Override
        public void handleInsert(@NotNull InsertionContext ctx, @NotNull LookupElement item) {
            Editor editor = ctx.getEditor();
            Project project = ctx.getProject();

            int startOffset = ctx.getStartOffset();
            int endOffset = ctx.getTailOffset();
            ctx.getDocument().deleteString(startOffset, endOffset);
            editor.getCaretModel().moveToOffset(startOffset);
            ctx.commitDocument();

            showOptionsPopup(editor, project, startOffset);
        }

        private void showOptionsPopup(@NotNull Editor editor, @NotNull Project project, int offset) {
            JCheckBox lazyBox = new JCheckBox("[L] Lazy initialization");
            JCheckBox cssBox = new JCheckBox("[C] CSS Styleable");
            JCheckBox readonlyBox = new JCheckBox("[R] Read-only (no setter)");
            JCheckBox defaultBox = new JCheckBox("[D] Default as constant");

            JPanel checkPanel = new JPanel(new MigLayout("wrap 1, insets 0, gap 2"));
            checkPanel.add(lazyBox);
            if (type.supportsCss()) {
                checkPanel.add(cssBox);
            }
            checkPanel.add(readonlyBox);
            checkPanel.add(defaultBox);

            JBLabel hintLabel = new JBLabel("Press letter to toggle, Enter to confirm");
            hintLabel.setForeground(JBColor.namedColor(
                    "Link.activeForeground", new JBColor(0x2470B3, 0x589DF6)));
            hintLabel.setFont(hintLabel.getFont().deriveFont(java.awt.Font.BOLD));

            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");

            JPanel buttonPanel = new JPanel(new MigLayout("insets 0", "push[]4[]"));
            buttonPanel.add(cancelButton);
            buttonPanel.add(okButton);

            JPanel panel = new JPanel(new MigLayout("wrap 1, insets 8 8 4 8, gap 2", "[grow,fill]"));
            panel.add(hintLabel);
            panel.add(new JSeparator(), "growx, gaptop 2, gapbottom 4");
            panel.add(checkPanel);
            panel.add(new JSeparator(), "growx, gaptop 4, gapbottom 2");
            panel.add(buttonPanel, "growx");

            JBPopup popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(panel, panel)
                    .setTitle(type.propertyClass + " Options")
                    .setFocusable(true)
                    .setRequestFocus(true)
                    .setMovable(false)
                    .setResizable(false)
                    .setCancelOnClickOutside(true)
                    .setCancelOnOtherWindowOpen(true)
                    .createPopup();

            Runnable confirmAction = () -> {
                popup.cancel();
                generateCode(editor, project, offset,
                        lazyBox.isSelected(),
                        cssBox.isSelected() && type.supportsCss(),
                        readonlyBox.isSelected(),
                        defaultBox.isSelected());
            };

            okButton.addActionListener(e -> confirmAction.run());
            cancelButton.addActionListener(e -> popup.cancel());

            KeyAdapter keyHandler = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_L:
                            lazyBox.setSelected(!lazyBox.isSelected());
                            e.consume();
                            break;
                        case KeyEvent.VK_C:
                            if (type.supportsCss()) {
                                cssBox.setSelected(!cssBox.isSelected());
                            }
                            e.consume();
                            break;
                        case KeyEvent.VK_R:
                            readonlyBox.setSelected(!readonlyBox.isSelected());
                            e.consume();
                            break;
                        case KeyEvent.VK_D:
                            defaultBox.setSelected(!defaultBox.isSelected());
                            e.consume();
                            break;
                        case KeyEvent.VK_ENTER:
                            e.consume();
                            confirmAction.run();
                            break;
                    }
                }
            };
            panel.addKeyListener(keyHandler);
            lazyBox.addKeyListener(keyHandler);
            cssBox.addKeyListener(keyHandler);
            readonlyBox.addKeyListener(keyHandler);
            defaultBox.addKeyListener(keyHandler);
            okButton.addKeyListener(keyHandler);
            cancelButton.addKeyListener(keyHandler);

            popup.showInBestPositionFor(editor);
        }

        private void generateCode(@NotNull Editor editor, @NotNull Project project,
                                  int offset, boolean lazy, boolean css,
                                  boolean readonly, boolean defaultConst) {
            String templateText = FxPropCodeGenerator.generate(
                    type, lazy, css, readonly, defaultConst, className);

            TemplateManager manager = TemplateManager.getInstance(project);
            Template template = manager.createTemplate("fxprop", "JavaFX", templateText);
            template.setToReformat(true);
            template.setToShortenLongNames(true);

            if (type.singleGeneric) {
                template.addVariable("TYPE", new ConstantNode("Object"), true);
            }
            if (type.dualGeneric) {
                template.addVariable("KEY_TYPE", new ConstantNode("Object"), true);
                template.addVariable("VALUE_TYPE", new ConstantNode("Object"), true);
            }

            template.addVariable("NAME", new ConstantNode("name"), true);
            template.addVariable("Name", "capitalize(NAME)", "", false);
            template.addVariable("NAME_CONST", "capitalizeAndUnderscore(NAME)", "", false);

            if (css) {
                template.addVariable("CSS_NAME", "fxCssName(NAME)", "", false);
            }

            if (defaultConst) {
                template.addVariable("DEFAULT", new ConstantNode(type.lazyDefault), true);
            }

            editor.getCaretModel().moveToOffset(offset);

            if (css) {
                manager.startTemplate(editor, template, new TemplateEditingAdapter() {
                    @Override
                    public void templateFinished(@NotNull Template tmpl, boolean brokenOff) {
                        if (brokenOff) {
                            return;
                        }
                        insertStyleablePropertiesClass(editor, project, lazy, defaultConst);
                    }
                });
            } else {
                manager.startTemplate(editor, template);
            }
        }

        /**
         * After the live template finishes, creates or appends to the StyleableProperties inner class.
         */
        private void insertStyleablePropertiesClass(@NotNull Editor editor,
                                                     @NotNull Project project,
                                                     boolean lazy, boolean defaultConst) {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (psiFile == null) {
                return;
            }

            int caretOffset = editor.getCaretModel().getOffset();
            PsiElement elementAtCaret = psiFile.findElementAt(caretOffset > 0 ? caretOffset - 1 : 0);
            PsiClass currentClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass.class);
            if (currentClass == null) {
                return;
            }

            String propertyName = findPropertyNameNearCaret(currentClass, caretOffset);
            if (propertyName == null) {
                return;
            }

            String constName = FxPropCodeGenerator.toConstantCase(propertyName);

            // Find existing StyleableProperties
            PsiClass styleableProps = findStyleablePropertiesClass(currentClass);

            if (styleableProps != null) {
                // Phase 2: append to existing inner class
                appendToStyleableProperties(editor, project, currentClass, styleableProps,
                        propertyName, constName, defaultConst, lazy);
            } else {
                // Phase 1: create new inner class + methods
                createStyleableProperties(project, currentClass, propertyName,
                        constName, defaultConst, lazy);
            }
        }

        /**
         * Phase 1: Creates StyleableProperties inner class + getClassCssMetaData + instance method.
         */
        private void createStyleableProperties(@NotNull Project project,
                                                @NotNull PsiClass currentClass,
                                                @NotNull String propertyName,
                                                @NotNull String constName,
                                                boolean defaultConst, boolean lazy) {
            PsiClass superClass = currentClass.getSuperClass();
            String superClassName = superClass != null && superClass.getName() != null
                    ? superClass.getName() : "javafx.scene.control.Control";

            boolean useControlCssMetaData = false;
            PsiClass controlClass = JavaPsiFacade.getInstance(project)
                    .findClass("javafx.scene.control.Control", GlobalSearchScope.allScope(project));
            if (controlClass != null && currentClass.isInheritor(controlClass, true)) {
                useControlCssMetaData = true;
            }

            String code = FxPropCodeGenerator.generateStyleablePropertiesClass(
                    propertyName, className, type, defaultConst, lazy,
                    superClassName, useControlCssMetaData);

            WriteCommandAction.runWriteCommandAction(project, "Generate StyleableProperties", null, () -> {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                PsiElement rBrace = currentClass.getRBrace();
                if (rBrace == null) {
                    return;
                }

                PsiClass dummyClass = factory.createClassFromText(code, currentClass);
                for (PsiClass inner : dummyClass.getInnerClasses()) {
                    currentClass.addBefore(inner, rBrace);
                }
                for (PsiMethod method : dummyClass.getMethods()) {
                    if (currentClass.findMethodsByName(method.getName(), false).length == 0) {
                        currentClass.addBefore(method, rBrace);
                    }
                }

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(currentClass);
            });
        }

        /**
         * Phase 2: Appends a CssMetaData field to existing StyleableProperties
         * and updates the STYLEABLES collection in the static block.
         */
        private void appendToStyleableProperties(@NotNull Editor editor,
                                                  @NotNull Project project,
                                                  @NotNull PsiClass currentClass,
                                                  @NotNull PsiClass styleableProps,
                                                  @NotNull String propertyName,
                                                  @NotNull String constName,
                                                  boolean defaultConst, boolean lazy) {
            // Check for name conflict
            for (PsiField f : styleableProps.getFields()) {
                if (constName.equals(f.getName())) {
                    HintManager.getInstance().showInformationHint(editor,
                            "CssMetaData field '" + constName + "' already exists in StyleableProperties.");
                    return;
                }
            }

            String fieldCode = FxPropCodeGenerator.generateCssMetaDataField(
                    propertyName, className, type, defaultConst, lazy);

            WriteCommandAction.runWriteCommandAction(project, "Append to StyleableProperties", null, () -> {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

                // Step 1: Insert the CssMetaData field into StyleableProperties
                PsiField newField = createFieldFromText(factory, fieldCode, styleableProps);
                if (newField == null) {
                    return;
                }

                PsiElement insertedField = insertCssMetaDataField(styleableProps, newField);
                if (insertedField == null) {
                    return;
                }

                // Step 2: Update the static block's collection
                boolean updated = updateStaticBlock(factory, styleableProps, constName);
                if (!updated) {
                    // Show hint if we couldn't auto-update the static block
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() ->
                            HintManager.getInstance().showInformationHint(editor,
                                    "CssMetaData field added. Please add '" + constName
                                            + "' to the STYLEABLES list manually."));
                }

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(styleableProps);
                com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(styleableProps);
            });
        }

        /**
         * Creates a PsiField from code text using a dummy class wrapper.
         */
        @Nullable
        private static PsiField createFieldFromText(@NotNull PsiElementFactory factory,
                                                     @NotNull String fieldCode,
                                                     @NotNull PsiElement context) {
            String wrappedCode = "class _Dummy_ { " + fieldCode + " }";
            PsiClass dummyClass = factory.createClassFromText(wrappedCode, context).getInnerClasses()[0];
            PsiField[] fields = dummyClass.getFields();
            return fields.length > 0 ? (PsiField) fields[0].copy() : null;
        }

        /**
         * Inserts a CssMetaData field into StyleableProperties before the STYLEABLES list field,
         * or after the last CssMetaData field, or at the beginning.
         */
        @Nullable
        private static PsiElement insertCssMetaDataField(@NotNull PsiClass styleableProps,
                                                          @NotNull PsiField newField) {
            PsiField stylesablesListField = null;
            PsiField lastCssMetaDataField = null;

            for (PsiField field : styleableProps.getFields()) {
                String typeText = field.getType().getCanonicalText();
                if (typeText.contains("List") && typeText.contains("CssMetaData")) {
                    stylesablesListField = field;
                } else if (typeText.contains("CssMetaData")) {
                    lastCssMetaDataField = field;
                }
            }

            if (stylesablesListField != null) {
                return styleableProps.addBefore(newField, stylesablesListField);
            } else if (lastCssMetaDataField != null) {
                return styleableProps.addAfter(newField, lastCssMetaDataField);
            } else {
                PsiElement lBrace = styleableProps.getLBrace();
                if (lBrace != null) {
                    return styleableProps.addAfter(newField, lBrace);
                }
            }
            return null;
        }

        /**
         * Updates the static block to include the new constant in the STYLEABLES collection.
         * Supports Collections.addAll(...) and list.add(...) patterns.
         *
         * @return true if the static block was successfully updated
         */
        private static boolean updateStaticBlock(@NotNull PsiElementFactory factory,
                                                  @NotNull PsiClass styleableProps,
                                                  @NotNull String constName) {
            PsiClassInitializer staticInit = findStaticInitializer(styleableProps);
            if (staticInit == null) {
                return false;
            }
            PsiCodeBlock body = staticInit.getBody();

            // Pattern A: Collections.addAll(styleables, FIELD_A, FIELD_B)
            for (PsiStatement stmt : body.getStatements()) {
                if (!(stmt instanceof PsiExpressionStatement)) {
                    continue;
                }
                PsiExpression expr = ((PsiExpressionStatement) stmt).getExpression();
                if (!(expr instanceof PsiMethodCallExpression)) {
                    continue;
                }
                PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
                String methodName = call.getMethodExpression().getReferenceName();
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();

                if ("addAll".equals(methodName) && qualifier != null
                        && qualifier.getText().endsWith("Collections")) {
                    PsiExpressionList argList = call.getArgumentList();
                    PsiExpression newArg = factory.createExpressionFromText(constName, null);
                    argList.add(newArg);
                    return true;
                }
            }

            // Pattern B: list.add(FIELD_A)
            PsiStatement lastAddStmt = null;
            String listVarName = null;
            for (PsiStatement stmt : body.getStatements()) {
                if (!(stmt instanceof PsiExpressionStatement)) {
                    continue;
                }
                PsiExpression expr = ((PsiExpressionStatement) stmt).getExpression();
                if (!(expr instanceof PsiMethodCallExpression)) {
                    continue;
                }
                PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
                String methodName = call.getMethodExpression().getReferenceName();
                if ("add".equals(methodName)) {
                    lastAddStmt = stmt;
                    PsiExpression q = call.getMethodExpression().getQualifierExpression();
                    if (q != null) {
                        listVarName = q.getText();
                    }
                }
            }

            if (lastAddStmt != null && listVarName != null) {
                PsiStatement newStmt = factory.createStatementFromText(
                        listVarName + ".add(" + constName + ");", null);
                body.addAfter(newStmt, lastAddStmt);
                return true;
            }

            return false;
        }

        /**
         * Finds the static initializer block in a class.
         */
        @Nullable
        private static PsiClassInitializer findStaticInitializer(@NotNull PsiClass psiClass) {
            for (PsiClassInitializer init : psiClass.getInitializers()) {
                if (init.hasModifierProperty(PsiModifier.STATIC)) {
                    return init;
                }
            }
            return null;
        }

        @Nullable
        private static PsiClass findStyleablePropertiesClass(@NotNull PsiClass parentClass) {
            for (PsiClass inner : parentClass.getInnerClasses()) {
                if ("StyleableProperties".equals(inner.getName())) {
                    return inner;
                }
            }
            return null;
        }

        /**
         * Finds the Property field closest to (but before) the caret position.
         * The caret is at $END$ after the just-generated code, so this finds the correct field.
         */
        @Nullable
        private static String findPropertyNameNearCaret(@NotNull PsiClass psiClass, int caretOffset) {
            PsiField closest = null;
            for (PsiField field : psiClass.getFields()) {
                String typeName = field.getType().getCanonicalText();
                if (typeName.contains("Property") && field.getTextOffset() <= caretOffset) {
                    closest = field;
                }
            }
            return closest != null ? closest.getName() : null;
        }
    }
}
