package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.List;

/**
 * Verified constants for the FxmlKit library and shared utilities.
 * Constants must be confirmed against a real FxmlKit JAR before shipping
 * — see the pre-merge checklist.
 */
public final class FxmlKitModuleConstants {

    // ==================== Maven coordinates ====================
    public static final String GROUP_ID = "com.dlsc.fxmlkit";
    public static final String ARTIFACT_ID = "fxmlkit";

    // ==================== Java Platform Module System ====================
    // TODO verify against FxmlKit JAR's Automatic-Module-Name or module-info
    public static final String JPMS_MODULE_NAME = "com.dlsc.fxmlkit";

    // ==================== JavaFX dependency coordinates ====================
    public static final String JAVAFX_GROUP_ID = "org.openjfx";
    public static final String JAVAFX_CONTROLS_ARTIFACT = "javafx-controls";
    public static final String JAVAFX_FXML_ARTIFACT = "javafx-fxml";
    public static final String JAVAFX_CONTROLS_MODULE = "javafx.controls";
    public static final String JAVAFX_FXML_MODULE = "javafx.fxml";

    // ==================== Marker classes for classpath detection ====================
    public static final String JAVAFX_CONTROLS_MARKER = "javafx.scene.control.Control";
    public static final String JAVAFX_FXML_MARKER = "javafx.fxml.FXMLLoader";

    private FxmlKitModuleConstants() {
    }

    // ==================== Shared utilities ====================

    /**
     * Finds the main source set's {@code module-info.java} descriptor for the given module.
     * Returns null if no module-info exists or if the module has no Java source roots.
     */
    @Nullable
    public static PsiJavaModule findMainModuleDescriptor(@NotNull Module module) {
        List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaSourceRootType.SOURCE);
        PsiManager psiManager = PsiManager.getInstance(module.getProject());
        for (VirtualFile root : sourceRoots) {
            VirtualFile moduleInfoFile = root.findChild("module-info.java");
            if (moduleInfoFile == null) {
                continue;
            }
            PsiFile psiFile = psiManager.findFile(moduleInfoFile);
            if (psiFile instanceof PsiJavaFile javaFile) {
                PsiJavaModule descriptor = javaFile.getModuleDeclaration();
                if (descriptor != null) {
                    return descriptor;
                }
            }
        }
        return null;
    }
}
