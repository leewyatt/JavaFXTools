package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generate menu action for batch-creating JavaFX accessors on existing property fields.
 *
 * <p>Lists all JavaFX property fields in the enclosing class that have at least one
 * missing accessor (getter / setter / {@code xxxProperty()}), lets the user multi-select
 * through the platform {@link MemberChooser}, and then generates only the missing
 * methods by delegating to the same analyzer and generator used by the single-field
 * intention.
 */
public class FxPropertyAccessorGenerateAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || !(psiFile instanceof PsiJavaFile)) {
            return;
        }
        PsiClass psiClass = findEnclosingClass(psiFile, editor);
        if (psiClass == null) {
            return;
        }

        List<PsiFieldMember> candidates = collectCandidates(project, psiClass);
        if (candidates.isEmpty()) {
            Messages.showInfoMessage(project,
                    FxToolsBundle.message("generate.fx.property.accessor.dialog.empty"),
                    FxToolsBundle.message("generate.fx.property.accessor.dialog.title"));
            return;
        }

        PsiFieldMember[] candidatesArray = candidates.toArray(new PsiFieldMember[0]);
        MemberChooser<PsiFieldMember> chooser = new MemberChooser<>(
                candidatesArray,
                false,
                true,
                project,
                false,
                createHeaderPanel());
        chooser.setTitle(FxToolsBundle.message("generate.fx.property.accessor.dialog.title"));
        chooser.setCopyJavadocVisible(false);
        chooser.selectElements(candidatesArray);
        if (!chooser.showAndGet()) {
            return;
        }
        List<PsiFieldMember> selected = chooser.getSelectedElements();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        // Sort by source declaration order. The generator snapshots insertion anchors before
        // mutating PSI so batch generation keeps the same placement policy as the single-field
        // intention.
        List<PsiFieldMember> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparingInt(m -> m.getElement().getTextOffset()));

        WriteCommandAction.runWriteCommandAction(project,
                FxToolsBundle.message("action.JavaFX.GeneratePropertyAccessors.text"),
                null,
                () -> {
                    List<FxPropertyAccessorDescriptor> descriptors = new ArrayList<>();
                    for (PsiFieldMember member : ordered) {
                        PsiField field = member.getElement();
                        if (!field.isValid()) {
                            continue;
                        }
                        FxPropertyAccessorDescriptor descriptor =
                                FxPropertyFieldAnalyzer.analyze(project, field);
                        if (descriptor == null || !descriptor.hasMissingMethods()) {
                            continue;
                        }
                        descriptors.add(descriptor);
                    }
                    FxPropertyAccessorGenerator.generateMissingAccessors(project, descriptors);
                });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(isAvailable(e));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private boolean isAvailable(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || !(psiFile instanceof PsiJavaFile)) {
            return false;
        }
        return findEnclosingClass(psiFile, editor) != null;
    }

    @Nullable
    private static PsiClass findEnclosingClass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass == null || psiClass.isInterface() || psiClass.isAnnotationType()) {
            return null;
        }
        return psiClass;
    }

    @NotNull
    private static JComponent createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.emptyBottom(8));
        panel.add(new JBLabel(FxToolsBundle.message("generate.fx.property.accessor.dialog.description")),
                BorderLayout.CENTER);
        return panel;
    }

    @NotNull
    private static List<PsiFieldMember> collectCandidates(@NotNull Project project,
                                                          @NotNull PsiClass psiClass) {
        List<PsiFieldMember> candidates = new ArrayList<>();
        for (PsiField field : psiClass.getFields()) {
            FxPropertyAccessorDescriptor descriptor = FxPropertyFieldAnalyzer.analyze(project, field);
            if (descriptor != null && descriptor.hasMissingMethods()) {
                candidates.add(new PsiFieldMember(field));
            }
        }
        return candidates;
    }
}
