package com.itcodebox.fxtools.utils;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class JavaFXToolsBundle extends AbstractBundle {
    /**
     * 注意并没有后缀.properties
     */
    private static final String JAVAFX_TOOLS_BUNDLE = "messages.JavaFXToolsBundle";
    private static final JavaFXToolsBundle INSTANCE = new JavaFXToolsBundle();
    private JavaFXToolsBundle() {
        super(JAVAFX_TOOLS_BUNDLE);
    }

    @NotNull
    @Contract(pure = true)
    public static String message(@PropertyKey(resourceBundle= JAVAFX_TOOLS_BUNDLE) String key, Object... objs) {
        return INSTANCE.getMessage(key,objs);
    }

}
