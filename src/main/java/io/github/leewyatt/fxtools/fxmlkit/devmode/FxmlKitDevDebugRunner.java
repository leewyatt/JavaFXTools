package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/**
 * Debug variant of the FxmlKit Dev Mode runner. Inherits debugger attach behavior
 * from {@link GenericDebuggerRunner} — framework-specific enhancements (Spring Boot,
 * Kotlin coroutines, etc.) layered on top of the generic debugger are automatically
 * inherited.
 *
 * <p>VM arg injection happens in {@link #doExecute} (before super's execution),
 * matching {@link FxmlKitDevRunRunner}. See that class's Javadoc for why this is
 * preferred over overriding {@code patch} or registering a
 * {@link com.intellij.execution.runners.JavaProgramPatcher} EP.
 */
public final class FxmlKitDevDebugRunner extends GenericDebuggerRunner {

    @Override
    public @NotNull String getRunnerId() {
        return FxmlKitDevModeConstants.RUNNER_ID_DEBUG;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return FxmlKitDevModeConstants.EXECUTOR_ID_DEBUG.equals(executorId)
                && FxmlKitDevRunnerSupport.isSupportedProfile(profile)
                && FxmlKitDevRunnerSupport.hasFxmlKitDependency(profile);
    }

    @Override
    public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
        Module module = FxmlKitDevRunnerSupport.extractModule(environment.getRunProfile());
        if (module != null && !FxmlKitDevVersionChecker.check(environment.getProject(), module)) {
            return;
        }
        super.execute(environment);
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                                       @NotNull ExecutionEnvironment env)
            throws ExecutionException {
        FxmlKitDevRunnerSupport.injectDevModeProperty(state);
        return super.doExecute(state, env);
    }

    @Override
    protected @NotNull Promise<RunContentDescriptor> doExecuteAsync(
            @NotNull TargetEnvironmentAwareRunProfileState state,
            @NotNull ExecutionEnvironment env) throws ExecutionException {
        FxmlKitDevRunnerSupport.injectDevModeProperty(state);
        return super.doExecuteAsync(state, env);
    }
}
