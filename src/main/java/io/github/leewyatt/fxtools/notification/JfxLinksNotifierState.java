package io.github.leewyatt.fxtools.notification;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Persists the GUID of the last notified "Links of the Week" entry,
 * so the same edition is never shown twice across IDE restarts.
 */
@State(
        name = "JfxLinksNotifierState",
        storages = @Storage("jfx-tools-links-notifier.xml")
)
@Service(Service.Level.APP)
public final class JfxLinksNotifierState
        implements PersistentStateComponent<JfxLinksNotifierState.State> {

    static class State {
        public String lastNotifiedGuid = "";
    }

    private State state = new State();

    public static JfxLinksNotifierState getInstance() {
        return ApplicationManager.getApplication().getService(JfxLinksNotifierState.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public boolean isNew(String guid) {
        return !guid.equals(state.lastNotifiedGuid);
    }

    public void markNotified(String guid) {
        state.lastNotifiedGuid = guid;
    }
}
