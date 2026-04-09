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
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.List;
import java.util.Properties;

/**
 * Quick fix that creates a missing FXML file in the resource directory.
 */
public class CreateFxmlQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(CreateFxmlQuickFix.class);
    private final String fxmlFileName;

    public CreateFxmlQuickFix(@NotNull String fxmlFileName) {
        this.fxmlFileName = fxmlFileName;
    }

    @Override
    public @NotNull String getFamilyName() {
        return FxToolsBundle.message("quickfix.create.fxml", fxmlFileName + ".fxml");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiClass viewClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        if (viewClass == null) {
            return;
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(viewClass);
        if (module == null) {
            return;
        }

        String controllerFqn = extractControllerFqn(viewClass);

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                PsiDirectory resourceDir = findOrCreateResourceDir(project, module, viewClass);
                if (resourceDir == null) {
                    return;
                }

                FileTemplateManager ftm = FileTemplateManager.getInstance(project);
                Properties props = ftm.getDefaultProperties();
                props.setProperty("CONTROLLER", controllerFqn != null ? controllerFqn : "");
                props.setProperty(FileTemplate.ATTRIBUTE_NAME, fxmlFileName);

                PsiElement created = FileTemplateUtil.createFromTemplate(
                        ftm.getInternalTemplate("FxmlKitFxml"), fxmlFileName, props, resourceDir);

                if (created instanceof PsiFile) {
                    FileEditorManager.getInstance(project).openFile(
                            ((PsiFile) created).getVirtualFile(), true);
                }
            } catch (ProcessCanceledException pce) {
                throw pce;
            } catch (Exception ex) {
                LOG.error("Failed to create FXML file", ex);
            }
        });
    }

    private String extractControllerFqn(@NotNull PsiClass viewClass) {
        for (PsiClassType superType : viewClass.getSuperTypes()) {
            PsiClass resolved = superType.resolve();
            if (resolved == null) {
                continue;
            }
            String qname = resolved.getQualifiedName();
            if ("com.dlsc.fxmlkit.fxml.FxmlView".equals(qname)
                    || "com.dlsc.fxmlkit.fxml.FxmlViewProvider".equals(qname)) {
                PsiType[] typeParams = superType.getParameters();
                if (typeParams.length > 0 && typeParams[0] instanceof PsiClassType) {
                    PsiClass ctrlClass = ((PsiClassType) typeParams[0]).resolve();
                    if (ctrlClass != null
                            && !"java.lang.Void".equals(ctrlClass.getQualifiedName())) {
                        return ctrlClass.getQualifiedName();
                    }
                }
                break;
            }
        }
        return null;
    }

    private PsiDirectory findOrCreateResourceDir(
            @NotNull Project project, @NotNull Module module, @NotNull PsiClass viewClass) {
        PsiFile file = viewClass.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return null;
        }

        String packageName = ((PsiJavaFile) file).getPackageName();
        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);

        PsiDirectory baseDir;
        if (!resourceRoots.isEmpty()) {
            baseDir = PsiManager.getInstance(project).findDirectory(resourceRoots.get(0));
        } else {
            VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
            if (contentRoots.length == 0) {
                return null;
            }
            PsiDirectory contentDir = PsiManager.getInstance(project).findDirectory(contentRoots[0]);
            if (contentDir == null) {
                return null;
            }
            baseDir = findOrCreateSubDir(contentDir, "src/main/resources");
        }

        if (baseDir == null || packageName.isEmpty()) {
            return baseDir;
        }
        return findOrCreateSubDir(baseDir, packageName.replace('.', '/'));
    }

    private PsiDirectory findOrCreateSubDir(@NotNull PsiDirectory parent, @NotNull String path) {
        PsiDirectory current = parent;
        for (String part : path.split("/")) {
            PsiDirectory sub = current.findSubdirectory(part);
            if (sub == null) {
                sub = current.createSubdirectory(part);
            }
            current = sub;
        }
        return current;
    }
}
