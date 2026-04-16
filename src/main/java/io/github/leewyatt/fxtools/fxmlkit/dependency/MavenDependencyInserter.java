package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inserts Maven dependency entries into the current module's {@code pom.xml} and
 * optionally into the parent POM's {@code <dependencyManagement>} section.
 * <p>
 * Must be called inside a {@code WriteCommandAction}.
 */
public final class MavenDependencyInserter {

    private MavenDependencyInserter() {
    }

    /**
     * Inserts dependencies according to the context. Edits the current module's POM
     * and, when requested, the parent POM.
     *
     * @param ctx              the insertion context
     * @param updateParentPom  true when the user opted to also update the parent POM
     *                         (Case B checkbox)
     */
    public static void insert(@NotNull DependencyInsertionContext ctx,
                              boolean updateParentPom) {
        Module module = ctx.getModule();
        if (module == null) {
            return;
        }

        XmlFile modulePom = findModulePom(module);
        if (modulePom == null) {
            throw new IllegalStateException("Cannot find pom.xml for module: " + module.getName());
        }

        // ==================== Optional parent POM update (Case B) ====================
        if (updateParentPom && ctx.getParentPom() != null
                && !ctx.getParentPom().isExternal()
                && ctx.getParentPom().getParentPomFile() != null) {
            XmlFile parentPom = parseXmlFile(module, ctx.getParentPom().getParentPomFile());
            if (parentPom != null) {
                insertIntoParentDepMgmt(parentPom, ctx);
            }
        }

        // ==================== Current module POM ====================
        insertIntoModulePom(modulePom, ctx, updateParentPom);
    }

    // ==================== Module POM ====================

    private static void insertIntoModulePom(@NotNull XmlFile pomXml,
                                             @NotNull DependencyInsertionContext ctx,
                                             boolean parentUpdated) {
        XmlTag root = pomXml.getRootTag();
        if (root == null) {
            return;
        }

        XmlTag deps = root.findFirstSubTag("dependencies");
        if (deps == null) {
            deps = root.createChildTag("dependencies", root.getNamespace(), null, false);
            deps = root.addSubTag(deps, false);
        }

        ParentPomInfo parent = ctx.getParentPom();
        String fxVersion = ctx.getJavafxVersion();

        if (!ctx.hasControls()) {
            boolean managed = parent != null && parent.managesJavafxControls();
            addDependencyTag(deps, FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT,
                    managed ? null : fxVersion);
        }

        if (!ctx.hasFxml()) {
            boolean managed = parent != null && parent.managesJavafxFxml();
            addDependencyTag(deps, FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT,
                    managed ? null : fxVersion);
        }

        boolean fxmlKitManaged = (parent != null && parent.managesFxmlKit()) || parentUpdated;
        addDependencyTag(deps, FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID,
                fxmlKitManaged ? null : ctx.getFxmlKitVersion());
    }

    // ==================== Parent POM dependencyManagement ====================

    private static void insertIntoParentDepMgmt(@NotNull XmlFile parentPom,
                                                 @NotNull DependencyInsertionContext ctx) {
        XmlTag root = parentPom.getRootTag();
        if (root == null) {
            return;
        }

        XmlTag depMgmt = root.findFirstSubTag("dependencyManagement");
        if (depMgmt == null) {
            depMgmt = root.createChildTag("dependencyManagement", root.getNamespace(),
                    null, false);
            depMgmt = root.addSubTag(depMgmt, false);
        }

        XmlTag deps = depMgmt.findFirstSubTag("dependencies");
        if (deps == null) {
            deps = depMgmt.createChildTag("dependencies", depMgmt.getNamespace(),
                    null, false);
            deps = depMgmt.addSubTag(deps, false);
        }

        addDependencyTag(deps, FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID, ctx.getFxmlKitVersion());
    }

    // ==================== Helpers ====================

    private static void addDependencyTag(@NotNull XmlTag dependencies,
                                         @NotNull String groupId,
                                         @NotNull String artifactId,
                                         @Nullable String version) {
        XmlTag dep = dependencies.createChildTag("dependency",
                dependencies.getNamespace(), null, false);
        dep = dependencies.addSubTag(dep, false);

        addChildTag(dep, "groupId", groupId);
        addChildTag(dep, "artifactId", artifactId);
        if (version != null && !version.isBlank()) {
            addChildTag(dep, "version", version);
        }
    }

    private static void addChildTag(@NotNull XmlTag parent,
                                    @NotNull String name,
                                    @NotNull String value) {
        XmlTag child = parent.createChildTag(name, parent.getNamespace(), value, false);
        parent.addSubTag(child, false);
    }

    @Nullable
    private static XmlFile findModulePom(@NotNull Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        PsiManager psiManager = PsiManager.getInstance(module.getProject());
        for (VirtualFile root : roots) {
            VirtualFile pom = root.findChild("pom.xml");
            if (pom != null) {
                PsiFile psiFile = psiManager.findFile(pom);
                if (psiFile instanceof XmlFile xmlFile) {
                    return xmlFile;
                }
            }
        }
        return null;
    }

    @Nullable
    private static XmlFile parseXmlFile(@NotNull Module module,
                                        @NotNull VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
        return psiFile instanceof XmlFile xmlFile ? xmlFile : null;
    }
}
