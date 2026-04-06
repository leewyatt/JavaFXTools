package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.github.leewyatt.fxtools.css.FxCssModificationTracker;
import io.github.leewyatt.fxtools.css.FxCssPropertyTable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listens for classpath changes and CSS file changes.
 * Clears cached data that depends on project dependencies or CSS file content.
 */
public class FxClasspathListener implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // ==================== Classpath change listener ====================
        project.getMessageBus().connect().subscribe(
                ModuleRootListener.TOPIC,
                new ModuleRootListener() {
                    @Override
                    public void rootsChanged(@NotNull ModuleRootEvent event) {
                        FxDetector.clearCache(project);
                        FxCssPropertyTable.clearCache(project);
                    }
                }
        );

        // ==================== CSS file change listener ====================
        project.getMessageBus().connect().subscribe(
                VirtualFileManager.VFS_CHANGES,
                new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        for (VFileEvent event : events) {
                            VirtualFile file = event.getFile();
                            if (file != null && "css".equals(file.getExtension())) {
                                FxCssModificationTracker.getInstance(project).increment();
                                break;
                            }
                        }
                    }
                }
        );

        return Unit.INSTANCE;
    }
}
