package io.github.leewyatt.fxtools.fxmlkit.devmode;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/**
 * Runner that handles the {@code FxmlKit.DevMode} executor. Inherits all execution
 * behavior from {@link DefaultJavaProgramRunner}; only the id + {@code canRun} filter
 * + pre-execute version check + VM arg injection differ.
 *
 * <p>Note on VM arg injection: the injection happens inside both {@link #doExecute}
 * and {@link #doExecuteAsync} overrides (before delegating to super). Every simpler
 * single-hook approach tried failed on either Run, Debug, or both:
 *
 * <ul>
 *   <li>Registering a {@link com.intellij.execution.runners.JavaProgramPatcher} EP
 *       filtering by executor id: {@code DefaultJavaProgramRunner#patch} calls
 *       {@code runCustomPatchers} with a hardcoded
 *       {@link com.intellij.execution.executors.DefaultRunExecutor}, so the EP
 *       never matches on the Run path. Works for Debug (real {@code env.getExecutor()}
 *       is passed) but breaks Run.</li>
 *   <li>Overriding {@code patch(JavaParameters, RunnerSettings, RunProfile, boolean)}:
 *       {@code DefaultJavaProgramRunner#doExecute} does virtually dispatch to our
 *       {@code patch} for the Run path, but {@code GenericDebuggerRunner} never
 *       invokes its own {@code patch} method anywhere in its flow. Works for Run,
 *       breaks Debug.</li>
 *   <li>Overriding only {@code doExecute(RunProfileState, ExecutionEnvironment)}:
 *       {@code DefaultJavaProgramRunner#execute} checks
 *       {@code state instanceof TargetEnvironmentAwareRunProfileState} and routes
 *       to {@code doExecuteAsync} when true. Since
 *       {@code JavaCommandLineState implements TargetEnvironmentAwareRunProfileState},
 *       all Java Application / Spring Boot / Kotlin main configurations bypass
 *       {@code doExecute}. Breaks both Run and Debug.</li>
 * </ul>
 *
 * The working answer is to mutate the {@link com.intellij.execution.configurations.JavaParameters}
 * object in both {@code doExecute} and {@code doExecuteAsync} — these cover the
 * two execution entry points {@code execute} dispatches to, and idempotent
 * {@code hasProperty} checks keep double-invocation safe.
 *
 * <p>All claims verified via javap on {@code intellij.java.execution.impl.jar} and
 * {@code intellij.java.debugger.impl.jar} (IDEA 2024.2 SDK): see {@code patch()}
 * bytecode line 17 (hardcoded Run executor); {@code execute()} bytecode line 56
 * ({@code instanceof} dispatch to {@code doExecuteAsync}); absence of
 * {@code invokevirtual} targeting {@code this.patch()} in
 * {@code GenericDebuggerRunner}.
 */
public final class FxmlKitDevRunRunner extends DefaultJavaProgramRunner {

    @Override
    public @NotNull String getRunnerId() {
        return FxmlKitDevModeConstants.RUNNER_ID_RUN;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return FxmlKitDevModeConstants.EXECUTOR_ID_RUN.equals(executorId)
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
