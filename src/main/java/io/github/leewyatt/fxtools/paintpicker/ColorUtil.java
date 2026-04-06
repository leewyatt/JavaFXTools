package io.github.leewyatt.fxtools.paintpicker;

import io.github.leewyatt.fxtools.paintpicker.datamodel.ColorFormat;
import io.github.leewyatt.fxtools.paintpicker.datamodel.HSB;
import io.github.leewyatt.fxtools.paintpicker.datamodel.HSL;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Utility class for color conversions and color harmony calculations.
 */
public final class ColorUtil {
    private static final Random RANDOM = new Random();

    private ColorUtil() {
    }

    public static Color randomColor() {
        return new Color(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256));
    }

    /**
     * Converts a Color to its hex string. "#RRGGBB" or "#RRGGBBAA" if alpha &lt; 255.
     */
    public static String colorToWebHex(Color color) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int alpha = color.getAlpha();
        if (alpha == 255) {
            return String.format("#%02x%02x%02x", red, green, blue);
        } else {
            return String.format("#%02x%02x%02x%02x", red, green, blue, alpha);
        }
    }

    /**
     * Converts a hex string to Color.
     */
    public static Color webHexToColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return Color.BLACK;
        }
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        int len = hexColor.length();
        int r, g, b, a = 255;
        if (len == 6) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
        } else if (len == 8) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
            a = Integer.parseInt(hexColor.substring(6, 8), 16);
        } else if (len == 3) {
            r = Integer.parseInt(hexColor.substring(0, 1).repeat(2), 16);
            g = Integer.parseInt(hexColor.substring(1, 2).repeat(2), 16);
            b = Integer.parseInt(hexColor.substring(2, 3).repeat(2), 16);
        } else {
            return Color.BLACK;
        }
        return new Color(r, g, b, a);
    }

    public static int colorToInt(Color color) {
        return color.getRGB();
    }

    public static Color intToColor(int packedInt) {
        return new Color(packedInt, true);
    }

    // ---- Color Harmony ----

    public static List<Color> triadicHarmonies(Color color) {
        float[] hsb = toHsbArray(color);
        Color h1 = fromHsb((hsb[0] * 360 + 120) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        Color h2 = fromHsb((hsb[0] * 360 + 240) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        return List.of(color, h1, h2);
    }

    public static List<Color> complementaryColors(Color color) {
        float[] hsb = toHsbArray(color);
        Color c = fromHsb((hsb[0] * 360 + 180) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        return List.of(color, c);
    }

    public static List<Color> splitComplementaryColors(Color color) {
        float[] hsb = toHsbArray(color);
        Color c1 = fromHsb((hsb[0] * 360 + 150) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        Color c2 = fromHsb((hsb[0] * 360 + 210) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        return List.of(color, c1, c2);
    }

    public static List<Color> tetradicColors(Color color) {
        float[] hsb = toHsbArray(color);
        Color c2 = fromHsb((hsb[0] * 360 + 90) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        Color c3 = fromHsb((hsb[0] * 360 + 180) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        Color c4 = fromHsb((hsb[0] * 360 + 270) % 360 / 360f, hsb[1], hsb[2], color.getAlpha());
        return List.of(color, c2, c3, c4);
    }

    public static List<Color> generateMonochromaticColors(Color baseColor, int count) {
        float[] hsb = toHsbArray(baseColor);
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    double factor = i / (double) (count - 1);
                    float b = (float) (0.5 + factor * 0.5) * hsb[2];
                    b = Math.min(1.0f, Math.max(0.0f, b));
                    return fromHsb(hsb[0], hsb[1], b, baseColor.getAlpha());
                })
                .toList();
    }

    public static List<Color> generateAnalogousColors(Color baseColor, int count) {
        float[] hsb = toHsbArray(baseColor);
        double hueShift = 360.0 / 36;
        int halfCount = count / 2;
        List<Color> left = IntStream.rangeClosed(1, halfCount)
                .mapToObj(i -> {
                    double h = (hsb[0] * 360 - i * hueShift + 360) % 360;
                    return fromHsb((float) (h / 360), hsb[1], hsb[2], baseColor.getAlpha());
                })
                .toList();
        List<Color> right = IntStream.range(1, halfCount + (count % 2 == 0 ? 1 : 2))
                .mapToObj(i -> {
                    double h = (hsb[0] * 360 + i * hueShift) % 360;
                    return fromHsb((float) (h / 360), hsb[1], hsb[2], baseColor.getAlpha());
                })
                .toList();
        List<Color> combined = new ArrayList<>(left);
        combined.add(baseColor);
        combined.addAll(right);
        return combined;
    }

    // ---- HSB conversions ----

    public static HSB colorToHsb(Color color) {
        float[] hsb = toHsbArray(color);
        return new HSB(hsb[0] * 360, hsb[1], hsb[2], color.getAlpha() / 255.0);
    }

    public static Color hsbToColor(HSB hsb) {
        return hsbToColor(hsb.getHue(), hsb.getSaturation(), hsb.getBrightness(), hsb.getOpacity());
    }

    public static Color hsbToColor(double hue, double saturation, double brightness) {
        return hsbToColor(hue, saturation, brightness, 1.0);
    }

    public static Color hsbToColor(double hue, double saturation, double brightness, double opacity) {
        int rgb = Color.HSBtoRGB((float) (hue / 360.0), (float) saturation, (float) brightness);
        int alpha = (int) Math.round(opacity * 255);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    // ---- HSL conversions ----

    public static HSL colorToHsl(Color color) {
        double r = color.getRed() / 255.0;
        double g = color.getGreen() / 255.0;
        double b = color.getBlue() / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double h, s, l = (max + min) / 2;
        if (max == min) {
            h = s = 0;
        } else {
            double delta = max - min;
            s = l > 0.5 ? delta / (2.0 - max - min) : delta / (max + min);
            if (r > g && r > b) {
                h = (g - b) / delta + (g < b ? 6 : 0);
            } else if (g > b) {
                h = (b - r) / delta + 2;
            } else {
                h = (r - g) / delta + 4;
            }
            h /= 6;
        }
        return new HSL(h * 360, s, l, color.getAlpha() / 255.0);
    }

    public static Color hslToColor(HSL hsl) {
        double h = hsl.getHue() / 360.0;
        double s = hsl.getSaturation();
        double l = hsl.getLightness();
        double r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        int alpha = (int) Math.round(hsl.getOpacity() * 255);
        return new Color((int) Math.round(r * 255), (int) Math.round(g * 255),
                (int) Math.round(b * 255), alpha);
    }

    public static Color hslToColor(double hue, double saturation, double lightness) {
        return hslToColor(new HSL(hue, saturation, lightness));
    }

    public static Color hslToColor(double hue, double saturation, double lightness, double opacity) {
        return hslToColor(new HSL(hue, saturation, lightness, opacity));
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) { t += 1; }
        if (t > 1) { t -= 1; }
        if (t < 1.0 / 6.0) { return p + (q - p) * 6 * t; }
        if (t < 1.0 / 2.0) { return q; }
        if (t < 2.0 / 3.0) { return p + (q - p) * (2.0 / 3.0 - t) * 6; }
        return p;
    }

    // ---- Format ----

    public static String formatColorToString(Color color, ColorFormat fmt) {
        if (color == null || fmt == null) { return ""; }
        StringBuilder sb = new StringBuilder();
        switch (fmt) {
            case INTEGER -> sb.append(color.getRed()).append(",")
                    .append(color.getGreen()).append(",").append(color.getBlue());
            case DECIMAL -> sb.append(trimZeros(color.getRed() / 255.0, 3)).append(",")
                    .append(trimZeros(color.getGreen() / 255.0, 3)).append(",")
                    .append(trimZeros(color.getBlue() / 255.0, 3));
            case HEX -> { return colorToWebHex(color); }
        }
        double opacity = color.getAlpha() / 255.0;
        if (opacity < 1.0) {
            sb.append(",").append(trimZeros(opacity, 3));
        }
        return sb.toString();
    }

    public static String formatColorToString(Color color) {
        return formatColorToString(color, ColorFormat.DECIMAL);
    }

    /**
     * Trims trailing zeros from a decimal number string.
     */
    static String trimZeros(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    // ---- Internal ----

    private static float[] toHsbArray(Color color) {
        return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    }

    private static Color fromHsb(float hue, float saturation, float brightness, int alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }
}
