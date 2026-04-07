package io.github.leewyatt.fxtools.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Application-level persistent settings for the JavaFX Tools plugin.
 */
@State(
        name = "FxToolsSettings",
        storages = @Storage("jfx-tools-settings.xml")
)
@Service(Service.Level.APP)
public final class FxToolsSettingsState
        implements PersistentStateComponent<FxToolsSettingsState> {

    public boolean enableLinksNotification = true;
    public boolean enableGutterPreviews = true;
    public boolean enableIkonliCompletion = true;

    public static FxToolsSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(FxToolsSettingsState.class);
    }

    @Override
    public @NotNull FxToolsSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull FxToolsSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
