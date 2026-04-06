package io.github.leewyatt.fxtools.ikonli;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * FXML-side helpers for Ikonli features.
 */
final class IkonliFxmlUtil {

    private static final String FONT_ICON_SHORT = "FontIcon";
    private static final String FONT_ICON_FQN = "org.kordamp.ikonli.javafx.FontIcon";

    private IkonliFxmlUtil() {
    }

    /**
     * Returns true if the given XmlTag refers to Ikonli's {@code FontIcon}.
     * Matches both the short form (typical: {@code <FontIcon .../>}) and a fully
     * qualified write-out ({@code <org.kordamp.ikonli.javafx.FontIcon .../>}).
     */
    static boolean isFontIconTag(@NotNull XmlTag tag) {
        String name = tag.getName();
        return FONT_ICON_SHORT.equals(name) || FONT_ICON_FQN.equals(name);
    }
}
