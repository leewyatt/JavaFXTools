package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.module.Module;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.BuildSystem;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.GradleDsl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of everything the dependency dialog and inserters need to know
 * about the current module's classpath, build system, and module-info state.
 */
public final class DependencyInsertionContext {

    private final @Nullable Module module;
    private final BuildSystem buildSystem;
    private final GradleDsl gradleDsl;
    private final boolean hasVersionCatalog;
    private final boolean hasJavaFxGradlePlugin;
    private final boolean hasControls;
    private final boolean hasFxml;
    private final @Nullable String javafxVersion;
    private final String fxmlKitVersion;
    private final boolean hasModuleInfo;
    private final boolean requiresControlsMissing;
    private final boolean requiresFxmlMissing;
    private final boolean requiresFxmlKitMissing;
    private final @Nullable ParentPomInfo parentPom;

    @SuppressWarnings("ParameterNumber")
    public DependencyInsertionContext(@Nullable Module module,
                                     @NotNull BuildSystem buildSystem,
                                     @NotNull GradleDsl gradleDsl,
                                     boolean hasVersionCatalog,
                                     boolean hasJavaFxGradlePlugin,
                                     boolean hasControls,
                                     boolean hasFxml,
                                     @Nullable String javafxVersion,
                                     @NotNull String fxmlKitVersion,
                                     boolean hasModuleInfo,
                                     boolean requiresControlsMissing,
                                     boolean requiresFxmlMissing,
                                     boolean requiresFxmlKitMissing,
                                     @Nullable ParentPomInfo parentPom) {
        this.module = module;
        this.buildSystem = buildSystem;
        this.gradleDsl = gradleDsl;
        this.hasVersionCatalog = hasVersionCatalog;
        this.hasJavaFxGradlePlugin = hasJavaFxGradlePlugin;
        this.hasControls = hasControls;
        this.hasFxml = hasFxml;
        this.javafxVersion = javafxVersion;
        this.fxmlKitVersion = fxmlKitVersion;
        this.hasModuleInfo = hasModuleInfo;
        this.requiresControlsMissing = requiresControlsMissing;
        this.requiresFxmlMissing = requiresFxmlMissing;
        this.requiresFxmlKitMissing = requiresFxmlKitMissing;
        this.parentPom = parentPom;
    }

    @Nullable
    public Module getModule() {
        return module;
    }

    @NotNull
    public BuildSystem getBuildSystem() {
        return buildSystem;
    }

    @NotNull
    public GradleDsl getGradleDsl() {
        return gradleDsl;
    }

    public boolean hasVersionCatalog() {
        return hasVersionCatalog;
    }

    public boolean hasJavaFxGradlePlugin() {
        return hasJavaFxGradlePlugin;
    }

    public boolean hasControls() {
        return hasControls;
    }

    public boolean hasFxml() {
        return hasFxml;
    }

    @Nullable
    public String getJavafxVersion() {
        return javafxVersion;
    }

    @NotNull
    public String getFxmlKitVersion() {
        return fxmlKitVersion;
    }

    public boolean hasModuleInfo() {
        return hasModuleInfo;
    }

    public boolean isRequiresControlsMissing() {
        return requiresControlsMissing;
    }

    public boolean isRequiresFxmlMissing() {
        return requiresFxmlMissing;
    }

    public boolean isRequiresFxmlKitMissing() {
        return requiresFxmlKitMissing;
    }

    @Nullable
    public ParentPomInfo getParentPom() {
        return parentPom;
    }

    /**
     * Returns true when at least one JavaFX module needs to be added and the version
     * could not be resolved. In this state the Add-to-project button must be disabled
     * because writing an unresolved version breaks the build regardless of build system.
     */
    public boolean isJavafxVersionRequiredButMissing() {
        boolean needsJavafxModule = !hasControls || !hasFxml;
        return needsJavafxModule && javafxVersion == null;
    }

    /**
     * Returns true when the JavaFX Gradle plugin is detected and a JavaFX module
     * needs to be added. In this case the {@code modules} list in the plugin's DSL
     * block would need to be edited, but the inserter cannot do so without Gradle PSI.
     * The Add button should be disabled and the user should use the Copy path.
     */
    public boolean isJavaFxPluginModulesEditRequired() {
        return hasJavaFxGradlePlugin && (!hasControls || !hasFxml);
    }

    /**
     * Returns true when the Add-to-project action should be enabled.
     */
    public boolean isAddToProjectEnabled() {
        if (module == null) {
            return false;
        }
        if (buildSystem == BuildSystem.NONE) {
            return false;
        }
        if (isJavafxVersionRequiredButMissing()) {
            return false;
        }
        if (isJavaFxPluginModulesEditRequired()) {
            return false;
        }
        return true;
    }
}
