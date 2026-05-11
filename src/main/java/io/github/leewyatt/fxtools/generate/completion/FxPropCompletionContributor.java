package io.github.leewyatt.fxtools.generate.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ProcessingContext;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.generate.StyleableConverterResolver;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertiesIntegrator;
import io.github.leewyatt.fxtools.generate.styleable.StyleablePropertyDescriptor;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Completion contributor for JavaFX Property code generation.
 * Type fxpstring/fxpinteger/fxpboolean/... then select options in a popup.
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
            JRadioButton readonlyRadio = new JRadioButton(
                    "[R] " + FxToolsBundle.message("generate.fx.property.access.readonly"), false);
            JRadioButton standardRadio = new JRadioButton(
                    "[S] " + FxToolsBundle.message("generate.fx.property.access.standard"), true);
            JRadioButton styleableRadio = new JRadioButton(
                    "[C] " + FxToolsBundle.message("generate.fx.property.access.styleable"), false);
            ButtonGroup accessGroup = new ButtonGroup();
            accessGroup.add(readonlyRadio);
            accessGroup.add(standardRadio);
            accessGroup.add(styleableRadio);

            JCheckBox lazyBox = new JCheckBox("[L] " + FxToolsBundle.message("generate.fx.property.lazy"));
            JCheckBox defaultBox = new JCheckBox("[D] " + FxToolsBundle.message("generate.fx.property.constant"));

            JPanel checkPanel = new JPanel(new MigLayout("wrap 1, insets 0, gap 2"));
            checkPanel.add(readonlyRadio);
            checkPanel.add(standardRadio);
            if (type.supportsCss()) {
                checkPanel.add(styleableRadio);
            }
            checkPanel.add(new JSeparator(), "growx, gaptop 4, gapbottom 4");
            checkPanel.add(lazyBox);
            checkPanel.add(defaultBox);

            JBLabel hintLabel = new JBLabel(FxToolsBundle.message("generate.fx.property.completion.options.hint"));
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
                        styleableRadio.isSelected() && type.supportsCss(),
                        readonlyRadio.isSelected(),
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
                        case KeyEvent.VK_R:
                            readonlyRadio.setSelected(true);
                            e.consume();
                            break;
                        case KeyEvent.VK_S:
                            standardRadio.setSelected(true);
                            e.consume();
                            break;
                        case KeyEvent.VK_C:
                            if (type.supportsCss()) {
                                styleableRadio.setSelected(true);
                            }
                            e.consume();
                            break;
                        case KeyEvent.VK_UP:
                            selectAdjacentAccessMode(readonlyRadio, standardRadio, styleableRadio, -1);
                            e.consume();
                            break;
                        case KeyEvent.VK_DOWN:
                            selectAdjacentAccessMode(readonlyRadio, standardRadio, styleableRadio, 1);
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
            readonlyRadio.addKeyListener(keyHandler);
            standardRadio.addKeyListener(keyHandler);
            styleableRadio.addKeyListener(keyHandler);
            defaultBox.addKeyListener(keyHandler);
            okButton.addKeyListener(keyHandler);
            cancelButton.addKeyListener(keyHandler);

            popup.showInBestPositionFor(editor);
        }

        private void selectAdjacentAccessMode(@NotNull JRadioButton readonlyRadio,
                                              @NotNull JRadioButton standardRadio,
                                              @NotNull JRadioButton styleableRadio,
                                              int direction) {
            JRadioButton[] radios = type.supportsCss()
                    ? new JRadioButton[]{readonlyRadio, standardRadio, styleableRadio}
                    : new JRadioButton[]{readonlyRadio, standardRadio};
            int selectedIndex = 0;
            for (int i = 0; i < radios.length; i++) {
                if (radios[i].isSelected()) {
                    selectedIndex = i;
                    break;
                }
            }
            int nextIndex = Math.floorMod(selectedIndex + direction, radios.length);
            radios[nextIndex].setSelected(true);
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
            template.addVariable("NAME_CONST", "fxConstantName(NAME)", "", false);

            if (css) {
                template.addVariable("CSS_NAME", "fxCssName(NAME)", "", false);
            }

            if (defaultConst) {
                template.addVariable("DEFAULT", new ConstantNode(type.smartDefault), true);
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

            String constName = FxNamingUtil.toUpperSnakeCase(propertyName);
            String defaultReference = "";
            if (defaultConst) {
                defaultReference = "DEFAULT_" + constName;
            } else if (lazy) {
                defaultReference = type.lazyDefault;
            }

            StyleableConverterResolver.Result conversion =
                    resolveStyleableConversion(currentClass, propertyName);
            StyleablePropertyDescriptor descriptor = new StyleablePropertyDescriptor(
                    propertyName,
                    conversion.cssValueType(),
                    conversion.converterExpression(),
                    FxNamingUtil.toFxKebabCase(propertyName),
                    defaultReference,
                    constName);

            WriteCommandAction.runWriteCommandAction(project, "Integrate StyleableProperties", null,
                    () -> StyleablePropertiesIntegrator.integrate(project, currentClass, descriptor, editor));
        }

        /**
         * Resolves the CssMetaData type and converter. For ObjectProperty<T>, the
         * just-inserted PsiField gives us the concrete generic type.
         */
        @NotNull
        private StyleableConverterResolver.Result resolveStyleableConversion(@NotNull PsiClass psiClass,
                                                                             @NotNull String propertyName) {
            if (type != FxPropCodeGenerator.PropType.OBJECT) {
                return new StyleableConverterResolver.Result(type.cssValueType, type.converterExpr);
            }
            PsiField field = psiClass.findFieldByName(propertyName, false);
            if (field == null) {
                return StyleableConverterResolver.resolveObjectProperty((PsiType) null);
            }
            PsiType fieldType = field.getType();
            if (!(fieldType instanceof PsiClassType)) {
                return StyleableConverterResolver.resolveObjectProperty((PsiType) null);
            }
            PsiType[] params = ((PsiClassType) fieldType).getParameters();
            return params.length == 1
                    ? StyleableConverterResolver.resolveObjectProperty(params[0])
                    : StyleableConverterResolver.resolveObjectProperty((PsiType) null);
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
