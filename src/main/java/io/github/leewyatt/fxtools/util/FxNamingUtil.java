package io.github.leewyatt.fxtools.util;

import org.jetbrains.annotations.NotNull;

/**
 * Shared naming conversions used by JavaFX property generators and live template macros.
 */
public final class FxNamingUtil {

    private FxNamingUtil() {
    }

    /**
     * Converts a Java property name to upper snake case.
     *
     * @param camelCase property-style name, such as {@code showLabel} or {@code URLPath}
     * @return upper snake case text, such as {@code SHOW_LABEL} or {@code URL_PATH}
     */
    @NotNull
    public static String toUpperSnakeCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && shouldInsertWordBoundary(camelCase, i)) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * Converts a Java property name to a JavaFX CSS property name.
     *
     * @param camelCase property-style name, such as {@code showLabel}
     * @return JavaFX CSS name with the {@code -fx-} prefix
     */
    @NotNull
    public static String toFxKebabCase(@NotNull String camelCase) {
        StringBuilder sb = new StringBuilder("-fx-");
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Capitalizes the first character of the supplied value.
     *
     * @param value source text
     * @return source text with an upper-case first character, or the original empty string
     */
    @NotNull
    public static String capitalize(@NotNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean shouldInsertWordBoundary(@NotNull String value, int index) {
        char previous = value.charAt(index - 1);
        if (!Character.isUpperCase(previous)) {
            return true;
        }
        return index + 1 < value.length() && Character.isLowerCase(value.charAt(index + 1));
    }
}
