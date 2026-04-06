package io.github.leewyatt.fxtools.css.preview.effect;

/**
 * Supported JavaFX effect types for the editor.
 */
public enum EffectType {
    DROPSHADOW("dropshadow"),
    INNERSHADOW("innershadow");

    private final String cssName;

    EffectType(String cssName) {
        this.cssName = cssName;
    }

    /**
     * @return the CSS function name (lowercase)
     */
    public String getCssName() {
        return cssName;
    }
}
