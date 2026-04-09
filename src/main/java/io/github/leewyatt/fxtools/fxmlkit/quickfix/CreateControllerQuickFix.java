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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;
import java.util.Properties;

/**
 * Quick fix that creates a missing Controller class referenced by fx:controller.
 */
public class CreateControllerQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(CreateControllerQuickFix.class);
    private final String controllerFqn;

    public CreateControllerQuickFix(@NotNull String controllerFqn) {
        this.controllerFqn = controllerFqn;
    }

    @Override
    public @NotNull String getFamilyName() {
        return FxToolsBundle.message("quickfix.create.controller", controllerFqn);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiFile fxmlFile = descriptor.getPsiElement().getContainingFile();
        Module module = ModuleUtilCore.findModuleForFile(fxmlFile.getVirtualFile(), project);
        if (module == null) {
            return;
        }

        int lastDot = controllerFqn.lastIndexOf('.');
        String packageName = lastDot > 0 ? controllerFqn.substring(0, lastDot) : "";
        String className = lastDot > 0 ? controllerFqn.substring(lastDot + 1) : controllerFqn;

        List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaSourceRootType.SOURCE);
        if (sourceRoots.isEmpty()) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                PsiDirectory baseDir = PsiManager.getInstance(project).findDirectory(sourceRoots.get(0));
                if (baseDir == null) {
                    return;
                }

                PsiDirectory targetDir = baseDir;
                if (!packageName.isEmpty()) {
                    for (String part : packageName.split("\\.")) {
                        PsiDirectory sub = targetDir.findSubdirectory(part);
                        if (sub == null) {
                            sub = targetDir.createSubdirectory(part);
                        }
                        targetDir = sub;
                    }
                }

                FileTemplateManager ftm = FileTemplateManager.getInstance(project);
                Properties props = ftm.getDefaultProperties();
                props.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);
                props.setProperty(FileTemplate.ATTRIBUTE_NAME, className);

                PsiElement created = FileTemplateUtil.createFromTemplate(
                        ftm.getInternalTemplate("FxmlKitController"), className, props, targetDir);

                if (created instanceof PsiFile) {
                    FileEditorManager.getInstance(project).openFile(
                            ((PsiFile) created).getVirtualFile(), true);
                }
            } catch (ProcessCanceledException pce) {
                throw pce;
            } catch (Exception ex) {
                LOG.error("Failed to create controller class", ex);
            }
        });
    }
}
