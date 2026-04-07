package io.github.leewyatt.fxtools.notification;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Triggers the RSS notifier once across all projects.
 * Uses {@link AtomicBoolean} to ensure the APP-level service starts only once,
 * regardless of how many projects are opened.
 */
public class JfxLinksStartupActivity implements ProjectActivity {

    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (started.compareAndSet(false, true)) {
            JfxLinksNotifierService.getInstance().start();
        }
        return Unit.INSTANCE;
    }
}
