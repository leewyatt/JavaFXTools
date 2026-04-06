package io.github.leewyatt.fxtools.css.preview.effect;

import java.awt.Color;

/**
 * Data model for DropShadow / InnerShadow effect parameters.
 */
public class EffectConfig {

    // ==================== Constants ====================

    public static final String BLUR_GAUSSIAN = "gaussian";
    public static final String BLUR_ONE_PASS_BOX = "one-pass-box";
    public static final String BLUR_TWO_PASS_BOX = "two-pass-box";
    public static final String BLUR_THREE_PASS_BOX = "three-pass-box";

    public static final String[] BLUR_TYPES = {
            BLUR_GAUSSIAN, BLUR_ONE_PASS_BOX, BLUR_TWO_PASS_BOX, BLUR_THREE_PASS_BOX
    };

    private static final double DEFAULT_RADIUS = 10.0;
    private static final double DEFAULT_WIDTH = 21.0;
    private static final double DEFAULT_HEIGHT = 21.0;
    private static final double DEFAULT_SPREAD = 0.0;
    private static final double DEFAULT_OFFSET = 0.0;

    // ==================== Fields ====================

    private EffectType effectType;
    private String blurType;
    private Color color;
    private double width;
    private double height;
    private double radius;
    private double spreadOrChoke;
    private double offsetX;
    private double offsetY;

    // ==================== Constructor ====================

    /**
     * Creates an EffectConfig with default values.
     */
    public EffectConfig() {
        this.effectType = EffectType.DROPSHADOW;
        this.blurType = BLUR_THREE_PASS_BOX;
        this.color = Color.BLACK;
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        this.radius = DEFAULT_RADIUS;
        this.spreadOrChoke = DEFAULT_SPREAD;
        this.offsetX = DEFAULT_OFFSET;
        this.offsetY = DEFAULT_OFFSET;
    }

    /**
     * Creates a copy of the given config.
     */
    public EffectConfig(EffectConfig other) {
        this.effectType = other.effectType;
        this.blurType = other.blurType;
        this.color = other.color;
        this.width = other.width;
        this.height = other.height;
        this.radius = other.radius;
        this.spreadOrChoke = other.spreadOrChoke;
        this.offsetX = other.offsetX;
        this.offsetY = other.offsetY;
    }

    // ==================== Getters / Setters ====================

    public EffectType getEffectType() {
        return effectType;
    }

    public void setEffectType(EffectType effectType) {
        this.effectType = effectType;
    }

    public String getBlurType() {
        return blurType;
    }

    public void setBlurType(String blurType) {
        this.blurType = blurType;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getSpreadOrChoke() {
        return spreadOrChoke;
    }

    public void setSpreadOrChoke(double spreadOrChoke) {
        this.spreadOrChoke = spreadOrChoke;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    // ==================== CSS / Java Code Generation ====================

    /**
     * Generates the CSS function call text.
     */
    public String toCssText() {
        String colorStr = colorToCssString(color);
        String spreadLabel = formatNumber(spreadOrChoke);
        String radiusStr = formatNumber(radius);
        String oxStr = formatNumber(offsetX);
        String oyStr = formatNumber(offsetY);
        return effectType.getCssName() + "(" + blurType + ", " + colorStr + ", "
                + radiusStr + ", " + spreadLabel + ", " + oxStr + ", " + oyStr + ")";
    }

    /**
     * Generates multi-line Java code for constructing the effect.
     */
    public String toJavaCode() {
        String className = effectType == EffectType.DROPSHADOW ? "DropShadow" : "InnerShadow";
        String spreadName = effectType == EffectType.DROPSHADOW ? "Spread" : "Choke";
        String blurEnum = blurTypeToJavaEnum(blurType);
        String colorJava = colorToJavaString(color);
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(" effect = new ").append(className).append("();\n");
        sb.append("effect.setBlurType(BlurType.").append(blurEnum).append(");\n");
        sb.append("effect.setColor(").append(colorJava).append(");\n");
        sb.append("effect.setWidth(").append(formatNumber(width)).append(");\n");
        sb.append("effect.setHeight(").append(formatNumber(height)).append(");\n");
        sb.append("effect.setRadius(").append(formatNumber(radius)).append(");\n");
        sb.append("effect.set").append(spreadName).append("(").append(formatNumber(spreadOrChoke)).append(");\n");
        sb.append("effect.setOffsetX(").append(formatNumber(offsetX)).append(");\n");
        sb.append("effect.setOffsetY(").append(formatNumber(offsetY)).append(");");
        return sb.toString();
    }

    // ==================== Helpers ====================

    private static String colorToCssString(Color c) {
        if (c.getAlpha() == 255) {
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }
        double alpha = Math.round(c.getAlpha() / 255.0 * 100.0) / 100.0;
        return String.format("rgba(%d,%d,%d,%s)", c.getRed(), c.getGreen(), c.getBlue(), formatNumber(alpha));
    }

    private static String colorToJavaString(Color c) {
        if (c.getAlpha() == 255) {
            return String.format("Color.color(%.4f, %.4f, %.4f)",
                    c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0);
        }
        return String.format("Color.color(%.4f, %.4f, %.4f, %.4f)",
                c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0, c.getAlpha() / 255.0);
    }

    private static String blurTypeToJavaEnum(String blurType) {
        return switch (blurType) {
            case BLUR_GAUSSIAN -> "GAUSSIAN";
            case BLUR_ONE_PASS_BOX -> "ONE_PASS_BOX";
            case BLUR_TWO_PASS_BOX -> "TWO_PASS_BOX";
            default -> "THREE_PASS_BOX";
        };
    }

    /**
     * Formats a number, removing unnecessary trailing zeros.
     */
    static String formatNumber(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        String s = Double.toString(value);
        // Remove trailing zeros but keep at least one decimal
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s + "0";
            }
        }
        return s;
    }
}
