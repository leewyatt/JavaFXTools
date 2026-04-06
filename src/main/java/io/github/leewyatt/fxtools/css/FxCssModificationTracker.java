package io.github.leewyatt.fxtools.css;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks modifications to CSS files within the project.
 * Incremented by {@link io.github.leewyatt.fxtools.util.FxClasspathListener}
 * when any .css file is created, modified, or deleted.
 * Used as a dependency for CachedValue to invalidate CSS variable snapshots.
 */
@Service(Service.Level.PROJECT)
public final class FxCssModificationTracker implements ModificationTracker {

    private final AtomicLong counter = new AtomicLong(0);

    public static FxCssModificationTracker getInstance(@NotNull Project project) {
        return project.getService(FxCssModificationTracker.class);
    }

    @Override
    public long getModificationCount() {
        return counter.get();
    }

    /**
     * Signals that a CSS file has changed. Called from BulkFileListener.
     */
    public void increment() {
        counter.incrementAndGet();
    }
}
