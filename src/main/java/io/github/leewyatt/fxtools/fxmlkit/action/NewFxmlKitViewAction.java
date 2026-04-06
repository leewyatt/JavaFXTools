package io.github.leewyatt.fxtools.fxmlkit.action;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.fxmlkit.dialog.I18nConfig;
import io.github.leewyatt.fxtools.fxmlkit.dialog.NewFxmlKitViewDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.List;
import java.util.Properties;

/**
 * Action for creating a new FxmlKit View with associated files.
 * Visible in New menu when the project has FxmlKit on its classpath.
 */
public class NewFxmlKitViewAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(NewFxmlKitViewAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
        if (project == null || ideView == null) {
            return;
        }

        PsiDirectory directory = ideView.getOrChooseDirectory();
        if (directory == null) {
            return;
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(directory);
        if (module == null) {
            return;
        }

        // If user right-clicked on a resource root, redirect Java files to Java source root
        PsiDirectory javaDir = resolveJavaDir(project, module, directory);

        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(javaDir);
        String packageName = psiPackage != null ? psiPackage.getQualifiedName() : "";

        String projectBasePath = project.getBasePath();
        String javaRelPath = relativePath(projectBasePath, javaDir.getVirtualFile().getPath());
        String resourceRelPath = computeResourceRelativePath(module, packageName, projectBasePath);

        PsiDirectory resourceDir = findResourceDir(project, module, packageName);
        NewFxmlKitViewDialog dialog = new NewFxmlKitViewDialog(
                project, packageName, javaRelPath, resourceRelPath,
                javaDir, resourceDir);
        if (!dialog.showAndGet()) {
            return;
        }

        String viewClassName = dialog.getViewClassName();
        String controllerName = dialog.getControllerClassName();
        boolean createController = dialog.isCreateController();
        boolean createFxml = dialog.isCreateFxml();
        boolean createCss = dialog.isCreateCss();
        boolean isProvider = dialog.isProviderMode();
        I18nConfig i18nConfig = dialog.getI18nConfig();

        WriteCommandAction.runWriteCommandAction(project,
                FxToolsBundle.message("action.new.fxmlkit.view"), null, () -> {
                    try {
                        FileTemplateManager ftm = FileTemplateManager.getInstance(project);
                        Properties defaultProps = ftm.getDefaultProperties();

                        // View or ViewProvider class
                        Properties viewProps = new Properties(defaultProps);
                        viewProps.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);
                        viewProps.setProperty(FileTemplate.ATTRIBUTE_NAME, viewClassName);
                        viewProps.setProperty("CONTROLLER", createController ? controllerName : "Void");
                        String resourceBundle = "";
                        if (i18nConfig != null) {
                            resourceBundle = computeBundleBaseName(
                                    i18nConfig.getBundleName(),
                                    i18nConfig.getBundlePath(), module);
                        }
                        viewProps.setProperty("RESOURCE_BUNDLE", resourceBundle);
                        String viewTemplate = isProvider ? "FxmlKitViewProvider" : "FxmlKitView";
                        PsiElement viewFile = FileTemplateUtil.createFromTemplate(
                                ftm.getInternalTemplate(viewTemplate), viewClassName, viewProps, javaDir);

                        // Controller class
                        if (createController) {
                            Properties ctrlProps = new Properties(defaultProps);
                            ctrlProps.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);
                            ctrlProps.setProperty(FileTemplate.ATTRIBUTE_NAME, controllerName);
                            FileTemplateUtil.createFromTemplate(
                                    ftm.getInternalTemplate("FxmlKitController"),
                                    controllerName, ctrlProps, javaDir);
                        }

                        // FXML and CSS in resources directory
                        if (createFxml || createCss) {
                            PsiDirectory resDir = findOrCreateResourceDir(
                                    project, module, packageName);
                            if (resDir != null) {
                                if (createFxml) {
                                    Properties fxmlProps = new Properties(defaultProps);
                                    String fqController = createController
                                            ? (packageName.isEmpty()
                                            ? controllerName
                                            : packageName + "." + controllerName)
                                            : "";
                                    fxmlProps.setProperty("CONTROLLER", fqController);
                                    fxmlProps.setProperty(FileTemplate.ATTRIBUTE_NAME, viewClassName);
                                    FileTemplateUtil.createFromTemplate(
                                            ftm.getInternalTemplate("FxmlKitFxml"),
                                            viewClassName, fxmlProps, resDir);
                                }
                                if (createCss) {
                                    Properties cssProps = new Properties(defaultProps);
                                    cssProps.setProperty(FileTemplate.ATTRIBUTE_NAME, viewClassName);
                                    FileTemplateUtil.createFromTemplate(
                                            ftm.getInternalTemplate("FxmlKitCss"),
                                            viewClassName, cssProps, resDir);
                                }
                            }
                        }

                        // i18n properties files
                        if (i18nConfig != null
                                && i18nConfig.getMode() == I18nConfig.Mode.CREATE_NEW) {
                            PsiDirectory resRoot = findOrCreateResourceDir(
                                    project, module, "");
                            if (resRoot != null) {
                                String subDir = resolveSubDir(
                                        i18nConfig.getBundlePath(), module);
                                PsiDirectory propDir = subDir.isEmpty()
                                        ? resRoot
                                        : findOrCreateSubDir(resRoot, subDir);
                                String bundleName = i18nConfig.getBundleName();
                                createPropertiesFile(propDir,
                                        bundleName + ".properties");
                                for (String locale : i18nConfig.getSelectedLocales()) {
                                    createPropertiesFile(propDir,
                                            bundleName + "_" + locale + ".properties");
                                }
                            }
                        }

                        // Open the View/Provider file in the editor
                        PsiFile psiFile = viewFile instanceof PsiFile
                                ? (PsiFile) viewFile
                                : viewFile.getContainingFile();
                        if (psiFile != null) {
                            FileEditorManager.getInstance(project).openFile(
                                    psiFile.getVirtualFile(), true);
                        }
                    } catch (Exception ex) {
                        LOG.error("Failed to create FxmlKit view files", ex);
                    }
                });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || DumbService.isDumb(project)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
        if (ideView == null || ideView.getDirectories().length == 0) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        PsiDirectory dir = ideView.getDirectories()[0];
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(dir);
        if (pkg == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        e.getPresentation().setEnabledAndVisible(FxmlKitDetector.isFxmlKitProject(project));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * If the directory is under a resource root, redirects to the corresponding
     * Java source root with the same package path. Otherwise returns directory as-is.
     */
    private PsiDirectory resolveJavaDir(
            @NotNull Project project, @NotNull Module module, @NotNull PsiDirectory directory) {
        VirtualFile dirVFile = directory.getVirtualFile();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(dirVFile);
        if (sourceRoot == null) {
            return directory;
        }

        // Check if the directory is under a resource root
        boolean isResourceRoot = false;
        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);
        for (VirtualFile resRoot : resourceRoots) {
            if (dirVFile.getPath().startsWith(resRoot.getPath())) {
                isResourceRoot = true;
                break;
            }
        }
        if (!isResourceRoot) {
            return directory;
        }

        // Find the package path relative to the resource root
        String packagePath = "";
        String dirPath = dirVFile.getPath();
        String rootPath = sourceRoot.getPath();
        if (dirPath.length() > rootPath.length()) {
            packagePath = dirPath.substring(rootPath.length() + 1);
        }

        // Find the first Java source root
        List<VirtualFile> javaRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaSourceRootType.SOURCE);
        if (javaRoots.isEmpty()) {
            return directory;
        }

        VirtualFile javaRoot = javaRoots.get(0);
        PsiDirectory javaBaseDir = PsiManager.getInstance(project).findDirectory(javaRoot);
        if (javaBaseDir == null) {
            return directory;
        }

        if (packagePath.isEmpty()) {
            return javaBaseDir;
        }
        // Find or create the package path under Java source root
        return findOrCreateSubDir(javaBaseDir, packagePath);
    }

    private PsiDirectory findResourceDir(
            Project project, Module module, String packageName) {
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
            baseDir = findSubDir(contentDir, "src/main/resources");
        }

        if (baseDir == null || packageName.isEmpty()) {
            return baseDir;
        }
        return findSubDir(baseDir, packageName.replace('.', '/'));
    }

    private PsiDirectory findOrCreateResourceDir(
            Project project, Module module, String packageName) {
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

    private PsiDirectory findSubDir(PsiDirectory parent, String path) {
        PsiDirectory current = parent;
        for (String part : path.split("/")) {
            PsiDirectory sub = current.findSubdirectory(part);
            if (sub == null) {
                return null;
            }
            current = sub;
        }
        return current;
    }

    private PsiDirectory findOrCreateSubDir(PsiDirectory parent, String path) {
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

    private String computeBundleBaseName(String bundleName, String bundlePath,
                                         Module module) {
        if (bundlePath == null || bundlePath.isEmpty()) {
            return bundleName;
        }
        String subDir = resolveSubDir(bundlePath, module);
        if (subDir.isEmpty()) {
            return bundleName;
        }
        return subDir.replace("/", ".") + "." + bundleName;
    }

    private String resolveSubDir(String bundlePath, Module module) {
        if (bundlePath == null || bundlePath.isEmpty()) {
            return "";
        }
        String path = bundlePath.replaceAll("/+$", "");
        if (path.isEmpty()) {
            return "";
        }
        String projectBasePath = module.getProject().getBasePath();
        List<VirtualFile> resRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);
        if (projectBasePath != null) {
            for (VirtualFile root : resRoots) {
                String rootRel = relativePath(projectBasePath, root.getPath());
                if (path.equals(rootRel)) {
                    return "";
                }
                if (path.startsWith(rootRel + "/")) {
                    return path.substring(rootRel.length() + 1);
                }
            }
        }
        return path;
    }

    private void createPropertiesFile(PsiDirectory dir, String fileName) {
        if (dir.findFile(fileName) == null) {
            dir.createFile(fileName);
        }
    }

    private String relativePath(String basePath, String fullPath) {
        if (basePath != null && fullPath.startsWith(basePath)) {
            String rel = fullPath.substring(basePath.length());
            if (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            return rel;
        }
        return fullPath;
    }

    private String computeResourceRelativePath(
            Module module, String packageName, String projectBasePath) {
        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);

        String basePath;
        if (!resourceRoots.isEmpty()) {
            basePath = relativePath(projectBasePath, resourceRoots.get(0).getPath());
        } else {
            basePath = "src/main/resources";
        }

        if (!packageName.isEmpty()) {
            return basePath + "/" + packageName.replace('.', '/');
        }
        return basePath;
    }
}
