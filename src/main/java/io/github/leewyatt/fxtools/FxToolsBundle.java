package io.github.leewyatt.fxtools;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Resource bundle for the JavaFX Tools plugin.
 */
public final class FxToolsBundle extends DynamicBundle {

    private static final String BUNDLE = "messages.FxToolsBundle";
    private static final FxToolsBundle INSTANCE = new FxToolsBundle();

    private FxToolsBundle() {
        super(BUNDLE);
    }

    /**
     * Returns a localized message for the given key.
     */
    public static @NotNull String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
