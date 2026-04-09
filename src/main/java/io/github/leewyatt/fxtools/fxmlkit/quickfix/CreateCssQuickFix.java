package io.github.leewyatt.fxtools.fxmlkit.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

/**
 * Quick fix that creates a missing CSS file in the same directory as the FXML file.
 */
public class CreateCssQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(CreateCssQuickFix.class);
    private final String cssFileName;

    public CreateCssQuickFix(@NotNull String cssFileName) {
        this.cssFileName = cssFileName;
    }

    @Override
    public @NotNull String getFamilyName() {
        return FxToolsBundle.message("quickfix.create.css", cssFileName + ".css");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiClass viewClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        if (viewClass == null) {
            return;
        }

        VirtualFile fxmlFile = FxFileResolver.findFxmlFile(viewClass);
        if (fxmlFile == null) {
            return;
        }

        VirtualFile fxmlDir = fxmlFile.getParent();
        if (fxmlDir == null) {
            return;
        }

        PsiDirectory targetDir = PsiManager.getInstance(project).findDirectory(fxmlDir);
        if (targetDir == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                FileTemplateManager ftm = FileTemplateManager.getInstance(project);
                Properties props = ftm.getDefaultProperties();
                props.setProperty(FileTemplate.ATTRIBUTE_NAME, cssFileName);

                PsiElement created = FileTemplateUtil.createFromTemplate(
                        ftm.getInternalTemplate("FxmlKitCss"), cssFileName, props, targetDir);

                if (created instanceof PsiFile) {
                    FileEditorManager.getInstance(project).openFile(
                            ((PsiFile) created).getVirtualFile(), true);
                }
            } catch (ProcessCanceledException pce) {
                throw pce;
            } catch (Exception ex) {
                LOG.error("Failed to create CSS file", ex);
            }
        });
    }
}
