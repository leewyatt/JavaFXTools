package io.github.leewyatt.fxtools.generate.styleable;

import org.jetbrains.annotations.NotNull;

/**
 * Describes one CSS styleable JavaFX property that must be wired into StyleableProperties.
 *
 * @param propertyName generated Java property field name
 * @param cssValueType generated CssMetaData value type
 * @param converterExpression Java expression for the CSS converter
 * @param cssName generated CSS property name
 * @param defaultReference optional Java expression for the CSS default value
 * @param constName generated CssMetaData constant name
 */
public record StyleablePropertyDescriptor(
        @NotNull String propertyName,
        @NotNull String cssValueType,
        @NotNull String converterExpression,
        @NotNull String cssName,
        @NotNull String defaultReference,
        @NotNull String constName) {
}
