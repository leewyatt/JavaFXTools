package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.openapi.module.Module;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared filtering and module extraction helpers for FxmlKit Dev Mode runners.
 */
final class FxmlKitDevRunnerSupport {

    private FxmlKitDevRunnerSupport() {
    }

    /**
     * Returns {@code true} if the profile is a Java-side Run Configuration that goes
     * through {@link com.intellij.execution.configurations.JavaParameters} (and thus
     * through {@link com.intellij.execution.runners.JavaProgramPatcher}), and is not a
     * test configuration. External build-system configurations (Gradle {@code run},
     * Maven {@code exec:java}, etc.) extend independent hierarchies and are excluded.
     */
    static boolean isSupportedProfile(@NotNull RunProfile profile) {
        return profile instanceof JavaRunConfigurationBase
                && !(profile instanceof JavaTestConfigurationBase);
    }

    /**
     * Also requires FxmlKit to be present on the selected module's classpath. Falls
     * back to the project-level check when the profile has no explicit module.
     */
    static boolean hasFxmlKitDependency(@NotNull RunProfile profile) {
        if (profile instanceof ModuleBasedConfiguration<?, ?> mbc) {
            Module module = mbc.getConfigurationModule().getModule();
            if (module != null) {
                return FxmlKitDetector.isFxmlKitModule(module);
            }
            return FxmlKitDetector.isFxmlKitProject(mbc.getProject());
        }
        return false;
    }

    @Nullable
    static Module extractModule(@NotNull RunProfile profile) {
        if (profile instanceof ModuleBasedConfiguration<?, ?> mbc) {
            return mbc.getConfigurationModule().getModule();
        }
        return null;
    }

    /**
     * Adds {@code -Dfxmlkit.devmode=true} to the state's VM parameters if not already
     * set. Idempotent via {@link ParametersList#hasProperty} — safe to call multiple
     * times in the same execution path.
     *
     * <p>This is the canonical injection point for both Run and Debug runners: by
     * mutating the {@link JavaParameters} object before the super runner's
     * {@code doExecute} reads it, the VM args reach the launched JVM regardless of
     * whether the runner internally invokes {@code patch()} or calls
     * {@code runCustomPatchers} directly with its own executor reference.
     */
    static void injectDevModeProperty(@NotNull RunProfileState state)
            throws ExecutionException {
        if (state instanceof JavaCommandLine jcl) {
            JavaParameters params = jcl.getJavaParameters();
            if (params != null) {
                ParametersList vm = params.getVMParametersList();
                if (!vm.hasProperty(FxmlKitDevModeConstants.SYS_PROP_KEY)) {
                    vm.addProperty(FxmlKitDevModeConstants.SYS_PROP_KEY,
                            FxmlKitDevModeConstants.SYS_PROP_VALUE);
                }
            }
        }
    }
}
