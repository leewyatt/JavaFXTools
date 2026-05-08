package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Intention for generating JavaFX-style accessors from an existing property field.
 */
public class FxPropertyAccessorIntention implements IntentionAction, PriorityAction,
        CustomizableIntentionAction, Iconable {

    private volatile String text = FxToolsBundle.message("generate.fx.property.accessor.intention.text");

    @Override
    public @Nls @NotNull String getText() {
        return text;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
        return FxToolsBundle.message("generate.fx.property.accessor.family");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) {
            return false;
        }
        FxPropertyAccessorDescriptor descriptor = descriptorAtCaret(project, editor, file);
        if (descriptor == null || !descriptor.hasMissingMethods()) {
            return false;
        }
        text = FxToolsBundle.message("generate.fx.property.accessor.intention.text.for",
                descriptor.fieldName());
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) {
            return;
        }
        FxPropertyAccessorDescriptor descriptor = descriptorAtCaret(project, editor, file);
        if (descriptor == null || !descriptor.hasMissingMethods()) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project,
                FxToolsBundle.message("generate.fx.property.accessor.intention.text.for",
                        descriptor.fieldName()),
                null,
                () -> FxPropertyAccessorGenerator.generateMissingAccessors(project, descriptor));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public Priority getPriority() {
        return Priority.TOP;
    }

    @Override
    public boolean isShowIcon() {
        return true;
    }

    @Override
    public Icon getIcon(int flags) {
        return AllIcons.Actions.IntentionBulb;
    }

    private FxPropertyAccessorDescriptor descriptorAtCaret(@NotNull Project project,
                                                          @NotNull Editor editor,
                                                          @NotNull PsiFile file) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
        if (field == null) {
            return null;
        }
        return FxPropertyFieldAnalyzer.analyze(project, field);
    }
}
