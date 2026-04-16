package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitVersionResolver;
import io.github.leewyatt.fxtools.util.BuildSystemDetector;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.BuildSystem;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.GradleDsl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link DependencyInsertionContext} by running the full detection pipeline:
 * module resolution, JavaFX module presence, version resolution, build-system analysis,
 * parent-POM inspection, and module-info scanning.
 */
public final class DependencyContextBuilder {

    private static final Logger LOG = Logger.getInstance(DependencyContextBuilder.class);

    private static final Pattern JAVAFX_PLUGIN_ID =
            Pattern.compile("id\\s*[('\"]org\\.openjfx\\.javafxplugin['\")]");
    private static final Pattern JAVAFX_PLUGIN_VERSION =
            Pattern.compile("javafx\\s*\\{[^}]*version\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.DOTALL);

    private DependencyContextBuilder() {
    }

    /**
     * Builds the full context for the given module. If the module is null the context
     * degrades to whole-project mode (snippet-only, Add disabled).
     */
    @NotNull
    public static DependencyInsertionContext build(@NotNull Project project,
                                                    @Nullable Module module) {
        BuildSystem buildSystem = BuildSystemDetector.detect(project);
        GradleDsl gradleDsl = BuildSystemDetector.detectGradleDsl(project, module);
        boolean hasVersionCatalog = BuildSystemDetector.hasVersionCatalog(project);

        // ==================== JavaFX Gradle plugin ====================
        boolean hasJavaFxGradlePlugin = false;
        String javafxPluginVersion = null;
        if (buildSystem == BuildSystem.GRADLE) {
            VirtualFile buildScript = findGradleBuildScript(project, module);
            if (buildScript != null) {
                String content = readFileContent(buildScript);
                if (content != null) {
                    hasJavaFxGradlePlugin = JAVAFX_PLUGIN_ID.matcher(content).find();
                    if (hasJavaFxGradlePlugin) {
                        javafxPluginVersion = extractJavafxPluginVersion(content);
                    }
                }
            }
        }

        // ==================== JavaFX module presence + version ====================
        boolean hasControls = false;
        boolean hasFxml = false;
        String javafxVersion = null;
        if (module != null) {
            JavaFxModulePresenceDetector detector =
                    JavaFxModulePresenceDetector.detect(module, javafxPluginVersion);
            hasControls = detector.hasControls();
            hasFxml = detector.hasFxml();
            javafxVersion = detector.getJavafxVersion();
        }

        // ==================== FxmlKit version ====================
        String fxmlKitVersion = FxmlKitVersionResolver.resolveVersion(project);

        // ==================== module-info.java ====================
        boolean hasModuleInfo = false;
        boolean requiresControlsMissing = false;
        boolean requiresFxmlMissing = false;
        boolean requiresFxmlKitMissing = false;
        if (module != null) {
            PsiJavaModule javaModule = FxmlKitModuleConstants.findMainModuleDescriptor(module);
            if (javaModule != null) {
                hasModuleInfo = true;
                requiresControlsMissing = !hasRequires(javaModule,
                        FxmlKitModuleConstants.JAVAFX_CONTROLS_MODULE);
                requiresFxmlMissing = !hasRequires(javaModule,
                        FxmlKitModuleConstants.JAVAFX_FXML_MODULE);
                requiresFxmlKitMissing = !hasRequires(javaModule,
                        FxmlKitModuleConstants.JPMS_MODULE_NAME);
            }
        }

        // ==================== Parent POM (Maven only) ====================
        ParentPomInfo parentPom = null;
        if (buildSystem == BuildSystem.MAVEN && module != null) {
            parentPom = buildParentPomInfo(project, module);
        }

        return new DependencyInsertionContext(
                module, buildSystem, gradleDsl,
                hasVersionCatalog, hasJavaFxGradlePlugin,
                hasControls, hasFxml, javafxVersion, fxmlKitVersion,
                hasModuleInfo, requiresControlsMissing, requiresFxmlMissing,
                requiresFxmlKitMissing, parentPom);
    }

    /**
     * Convenience overload that resolves the module from a virtual file.
     */
    @NotNull
    public static DependencyInsertionContext build(@NotNull Project project,
                                                    @Nullable VirtualFile file) {
        Module module = file != null
                ? ModuleUtilCore.findModuleForFile(file, project) : null;
        return build(project, module);
    }

    // ==================== Gradle helpers ====================

    @Nullable
    private static VirtualFile findGradleBuildScript(@NotNull Project project,
                                                      @Nullable Module module) {
        if (module != null) {
            VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
            if (roots.length > 0) {
                VirtualFile script = findBuildScript(roots[0]);
                if (script != null) {
                    return script;
                }
            }
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }
        VirtualFile baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath);
        return baseDir != null ? findBuildScript(baseDir) : null;
    }

    @Nullable
    private static VirtualFile findBuildScript(@NotNull VirtualFile dir) {
        VirtualFile kts = dir.findChild("build.gradle.kts");
        if (kts != null) {
            return kts;
        }
        return dir.findChild("build.gradle");
    }

    @Nullable
    private static String extractJavafxPluginVersion(@NotNull String content) {
        Matcher m = JAVAFX_PLUGIN_VERSION.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    // ==================== module-info helpers ====================

    private static boolean hasRequires(@NotNull PsiJavaModule javaModule,
                                       @NotNull String moduleName) {
        for (PsiRequiresStatement req : javaModule.getRequires()) {
            if (moduleName.equals(req.getModuleName())) {
                return true;
            }
        }
        return false;
    }

    // ==================== Parent POM helpers ====================

    @Nullable
    private static ParentPomInfo buildParentPomInfo(@NotNull Project project,
                                                     @NotNull Module module) {
        VirtualFile modulePom = findModulePom(module);
        if (modulePom == null) {
            return null;
        }

        XmlFile pomXml = parseXmlFile(project, modulePom);
        if (pomXml == null) {
            return null;
        }

        XmlTag root = pomXml.getRootTag();
        if (root == null) {
            return null;
        }

        XmlTag parentTag = root.findFirstSubTag("parent");
        if (parentTag == null) {
            return null;
        }

        String parentGid = getTagText(parentTag, "groupId");
        String parentAid = getTagText(parentTag, "artifactId");

        VirtualFile parentPomFile = resolveParentPomFile(modulePom, parentTag, project);
        boolean isExternal = (parentPomFile == null);

        // External parent — we cannot inspect its dependencyManagement, so all
        // manages* flags default to false. The checkbox will be disabled anyway.
        if (isExternal) {
            return new ParentPomInfo(parentGid, parentAid,
                    false, false, false, false, true, null);
        }

        XmlFile parentXml = parseXmlFile(project, parentPomFile);
        if (parentXml == null) {
            return new ParentPomInfo(parentGid, parentAid,
                    false, false, false, false, true, null);
        }

        XmlTag parentRoot = parentXml.getRootTag();
        if (parentRoot == null) {
            return new ParentPomInfo(parentGid, parentAid,
                    false, false, false, false, false, parentPomFile);
        }

        XmlTag depMgmt = parentRoot.findFirstSubTag("dependencyManagement");
        if (depMgmt == null) {
            return new ParentPomInfo(parentGid, parentAid,
                    false, false, false, false, false, parentPomFile);
        }

        XmlTag depMgmtDeps = depMgmt.findFirstSubTag("dependencies");
        if (depMgmtDeps == null) {
            return new ParentPomInfo(parentGid, parentAid,
                    true, false, false, false, false, parentPomFile);
        }

        boolean managesFxmlKit = false;
        boolean managesControls = false;
        boolean managesFxml = false;
        for (XmlTag dep : depMgmtDeps.findSubTags("dependency")) {
            String groupId = getTagText(dep, "groupId");
            String artifactId = getTagText(dep, "artifactId");
            if (FxmlKitModuleConstants.GROUP_ID.equals(groupId)
                    && FxmlKitModuleConstants.ARTIFACT_ID.equals(artifactId)) {
                managesFxmlKit = true;
            }
            if (FxmlKitModuleConstants.JAVAFX_GROUP_ID.equals(groupId)) {
                if (FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT.equals(artifactId)) {
                    managesControls = true;
                } else if (FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT.equals(artifactId)) {
                    managesFxml = true;
                }
            }
        }

        return new ParentPomInfo(parentGid, parentAid,
                true, managesFxmlKit, managesControls, managesFxml,
                false, parentPomFile);
    }

    @Nullable
    private static VirtualFile findModulePom(@NotNull Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        for (VirtualFile root : roots) {
            VirtualFile pom = root.findChild("pom.xml");
            if (pom != null) {
                return pom;
            }
        }
        return null;
    }

    @Nullable
    private static VirtualFile resolveParentPomFile(@NotNull VirtualFile modulePom,
                                                     @NotNull XmlTag parentTag,
                                                     @NotNull Project project) {
        String relativePath = getTagText(parentTag, "relativePath");
        if (relativePath == null || relativePath.isEmpty()) {
            relativePath = "../pom.xml";
        }

        VirtualFile moduleDir = modulePom.getParent();
        if (moduleDir == null) {
            return null;
        }

        VirtualFile resolved = moduleDir.findFileByRelativePath(relativePath);
        if (resolved != null && !resolved.isDirectory()) {
            return isInsideProject(resolved, project) ? resolved : null;
        }

        if (resolved != null && resolved.isDirectory()) {
            VirtualFile pom = resolved.findChild("pom.xml");
            if (pom != null) {
                return isInsideProject(pom, project) ? pom : null;
            }
        }

        return null;
    }

    private static boolean isInsideProject(@NotNull VirtualFile file,
                                           @NotNull Project project) {
        String basePath = project.getBasePath();
        return basePath != null && file.getPath().startsWith(basePath);
    }

    @Nullable
    private static XmlFile parseXmlFile(@NotNull Project project,
                                        @NotNull VirtualFile file) {
        var psiFile = PsiManager.getInstance(project).findFile(file);
        return psiFile instanceof XmlFile xmlFile ? xmlFile : null;
    }

    @Nullable
    private static String getTagText(@NotNull XmlTag parent, @NotNull String childName) {
        XmlTag child = parent.findFirstSubTag(childName);
        if (child == null) {
            return null;
        }
        String text = child.getValue().getTrimmedText();
        return text.isEmpty() ? null : text;
    }

    @Nullable
    private static String readFileContent(@NotNull VirtualFile file) {
        try {
            return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.info("Failed to read file: " + file.getPath(), e);
            return null;
        }
    }
}
