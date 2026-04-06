package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.css.FxNamedColors;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSS color values into java.awt.Color objects.
 */
public final class FxColorParser {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#([0-9A-Fa-f]{3,8})$");
    private static final Pattern RGB_PATTERN =
            Pattern.compile("^rgb\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RGBA_PATTERN =
            Pattern.compile("^rgba\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([\\d.]+)\\s*\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HSL_PATTERN =
            Pattern.compile("^hsl\\s*\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)%\\s*\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HSLA_PATTERN =
            Pattern.compile("^hsla\\s*\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)%\\s*,\\s*([\\d.]+)\\s*\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DERIVE_PATTERN =
            Pattern.compile("^derive\\s*\\(\\s*(.+?)\\s*,\\s*(-?[\\d.]+)%\\s*\\)$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RESOLVE_DEPTH = 10;

    private FxColorParser() {
    }

    /**
     * Parses a CSS color value to java.awt.Color.
     */
    @Nullable
    public static Color parseColor(@NotNull String colorValue) {
        String v = colorValue.trim();
        if (v.isEmpty()) {
            return null;
        }

        // Hex
        Matcher hexMatcher = HEX_PATTERN.matcher(v);
        if (hexMatcher.matches()) {
            return parseHex(hexMatcher.group(1));
        }

        // rgb()
        Matcher rgbMatcher = RGB_PATTERN.matcher(v);
        if (rgbMatcher.matches()) {
            return new Color(
                    clamp(Integer.parseInt(rgbMatcher.group(1))),
                    clamp(Integer.parseInt(rgbMatcher.group(2))),
                    clamp(Integer.parseInt(rgbMatcher.group(3))));
        }

        // rgba()
        Matcher rgbaMatcher = RGBA_PATTERN.matcher(v);
        if (rgbaMatcher.matches()) {
            return new Color(
                    clamp(Integer.parseInt(rgbaMatcher.group(1))),
                    clamp(Integer.parseInt(rgbaMatcher.group(2))),
                    clamp(Integer.parseInt(rgbaMatcher.group(3))),
                    clamp((int) (Float.parseFloat(rgbaMatcher.group(4)) * 255)));
        }

        // hsl()
        Matcher hslMatcher = HSL_PATTERN.matcher(v);
        if (hslMatcher.matches()) {
            return hslToColor(
                    Float.parseFloat(hslMatcher.group(1)),
                    Float.parseFloat(hslMatcher.group(2)) / 100f,
                    Float.parseFloat(hslMatcher.group(3)) / 100f,
                    1f);
        }

        // hsla()
        Matcher hslaMatcher = HSLA_PATTERN.matcher(v);
        if (hslaMatcher.matches()) {
            return hslToColor(
                    Float.parseFloat(hslaMatcher.group(1)),
                    Float.parseFloat(hslaMatcher.group(2)) / 100f,
                    Float.parseFloat(hslaMatcher.group(3)) / 100f,
                    Float.parseFloat(hslaMatcher.group(4)));
        }

        // Named color
        Color named = FxNamedColors.getColor(v);
        if (named != null) {
            return named;
        }

        return null;
    }

    /**
     * Checks if the value is a direct color (hex, rgb, rgba, hsl, hsla, or named).
     */
    public static boolean isDirectColor(@NotNull String value) {
        return parseColor(value) != null;
    }

    /**
     * Checks if the value is a CSS variable reference (starts with -).
     */
    public static boolean isVariableReference(@NotNull String value) {
        String v = value.trim();
        return v.startsWith("-") && !v.startsWith("-fx-");
    }

    /**
     * Checks if the value is a gradient.
     */
    public static boolean isGradient(@NotNull String value) {
        String lower = value.trim().toLowerCase();
        return lower.startsWith("linear-gradient(") || lower.startsWith("radial-gradient(");
    }

    /**
     * Checks if the value is a derive() function call.
     */
    public static boolean isDerive(@NotNull String value) {
        return DERIVE_PATTERN.matcher(value.trim()).matches();
    }

    /**
     * Parses a derive() expression and returns the derived (computed) color.
     *
     * @return the derived color, or null if parsing fails
     */
    @Nullable
    public static Color parseDeriveColor(@NotNull String value) {
        DeriveInfo info = parseDeriveInfo(value);
        if (info == null) {
            return null;
        }
        return computeDerivedColor(info.baseColor, info.brightnessOffset);
    }

    /**
     * Parses a derive() expression and returns the base color and offset.
     */
    @Nullable
    public static DeriveInfo parseDeriveInfo(@NotNull String value) {
        Matcher m = DERIVE_PATTERN.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }
        String baseColorStr = m.group(1).trim();
        Color baseColor = parseColor(baseColorStr);
        if (baseColor == null) {
            return null;
        }
        double offset = Double.parseDouble(m.group(2));
        return new DeriveInfo(baseColor, baseColorStr, offset);
    }

    /**
     * Computes a derived color by adjusting brightness.
     * Positive offset = brighter, negative = darker.
     * -100% = black, 0% = no change, 100% = white.
     */
    @NotNull
    public static Color computeDerivedColor(@NotNull Color base, double brightnessOffsetPercent) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float b = hsb[2];
        double offset = brightnessOffsetPercent / 100.0;
        if (offset > 0) {
            b = (float) (b + (1.0 - b) * offset);
        } else {
            b = (float) (b + b * offset);
        }
        b = Math.max(0f, Math.min(1f, b));
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], b);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, base.getAlpha());
    }

    /**
     * Holds parsed derive() info: base color and brightness offset.
     */
    public static final class DeriveInfo {
        private final Color baseColor;
        private final String baseColorText;
        private final double brightnessOffset;

        public DeriveInfo(@NotNull Color baseColor, @NotNull String baseColorText, double brightnessOffset) {
            this.baseColor = baseColor;
            this.baseColorText = baseColorText;
            this.brightnessOffset = brightnessOffset;
        }

        public Color getBaseColor() { return baseColor; }
        public String getBaseColorText() { return baseColorText; }
        public double getBrightnessOffset() { return brightnessOffset; }
    }

    /**
     * Resolves a CSS variable to its final color value by following the definition chain.
     */
    @Nullable
    public static Color resolveVariableColor(@NotNull String variableName,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope) {
        return resolveVariableColorInternal(variableName, project, scope, new HashSet<>(), 0);
    }

    /**
     * Resolves a CSS variable and returns all possible colors from different definitions.
     */
    @NotNull
    public static List<ResolvedColor> resolveVariableColors(@NotNull String variableName,
                                                            @NotNull Project project,
                                                            @NotNull GlobalSearchScope scope) {
        List<ResolvedColor> results = new ArrayList<>();
        List<String> values = FxCssPropertyIndex.getPropertyValues(variableName, project, scope);

        for (String rawValue : values) {
            for (String singleValue : rawValue.split("\n")) {
                String trimmed = singleValue.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                Color color = parseColor(trimmed);
                if (color != null) {
                    results.add(new ResolvedColor(color, trimmed, variableName + " \u2192 " + trimmed));
                } else if (trimmed.startsWith("-")) {
                    Color resolved = resolveVariableColorInternal(
                            trimmed, project, scope, new HashSet<>(Set.of(variableName)), 1);
                    if (resolved != null) {
                        results.add(new ResolvedColor(resolved, trimmed,
                                variableName + " \u2192 " + trimmed + " \u2192 ..."));
                    }
                }
            }
        }

        return results;
    }

    /**
     * Resolves a CSS variable to its final color value using the in-memory variable map (no I/O).
     */
    @Nullable
    public static Color resolveVariableColor(@NotNull String variableName,
                                             @NotNull Map<String, List<String>> allVars) {
        return resolveVariableColorFromMap(variableName, allVars, new HashSet<>(), 0);
    }

    /**
     * Resolves a CSS variable and returns all possible colors using the in-memory variable map (no I/O).
     */
    @NotNull
    public static List<ResolvedColor> resolveVariableColors(@NotNull String variableName,
                                                            @NotNull Map<String, List<String>> allVars) {
        List<ResolvedColor> results = new ArrayList<>();
        List<String> values = allVars.getOrDefault(variableName, Collections.emptyList());

        for (String rawValue : values) {
            for (String singleValue : rawValue.split("\n")) {
                String trimmed = singleValue.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                Color color = parseColor(trimmed);
                if (color != null) {
                    results.add(new ResolvedColor(color, trimmed, variableName + " \u2192 " + trimmed));
                } else if (trimmed.startsWith("-")) {
                    Color resolved = resolveVariableColorFromMap(
                            trimmed, allVars, new HashSet<>(Set.of(variableName)), 1);
                    if (resolved != null) {
                        results.add(new ResolvedColor(resolved, trimmed,
                                variableName + " \u2192 " + trimmed + " \u2192 ..."));
                    }
                }
            }
        }

        return results;
    }

    @Nullable
    private static Color resolveVariableColorFromMap(@NotNull String variableName,
                                                      @NotNull Map<String, List<String>> allVars,
                                                      @NotNull Set<String> visited,
                                                      int depth) {
        if (depth >= MAX_RESOLVE_DEPTH || !visited.add(variableName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(variableName, Collections.emptyList());
        for (String rawValue : values) {
            String value = rawValue.contains("\n")
                    ? rawValue.substring(rawValue.lastIndexOf('\n') + 1).trim() : rawValue.trim();
            if (value.isEmpty()) {
                continue;
            }
            Color direct = parseColor(value);
            if (direct != null) {
                return direct;
            }
            if (value.startsWith("-") && !isGradient(value)) {
                Color chained = resolveVariableColorFromMap(value, allVars, visited, depth + 1);
                if (chained != null) {
                    return chained;
                }
            }
        }
        return null;
    }

    /**
     * Represents a resolved color with source information.
     */
    public static final class ResolvedColor {
        private final Color color;
        private final String hexValue;
        private final String resolveChain;

        public ResolvedColor(@NotNull Color color, @NotNull String hexValue,
                             @NotNull String resolveChain) {
            this.color = color;
            this.hexValue = hexValue;
            this.resolveChain = resolveChain;
        }

        public Color getColor() { return color; }
        public String getHexValue() { return hexValue; }
        public String getResolveChain() { return resolveChain; }
    }

    @Nullable
    private static Color resolveVariableColorInternal(@NotNull String variableName,
                                                      @NotNull Project project,
                                                      @NotNull GlobalSearchScope scope,
                                                      @NotNull Set<String> visited,
                                                      int depth) {
        if (depth >= MAX_RESOLVE_DEPTH || visited.contains(variableName)) {
            return null;
        }
        visited.add(variableName);

        List<String> values = FxCssPropertyIndex.getPropertyValues(variableName, project, scope);
        for (String rawValue : values) {
            String value = rawValue.contains("\n") ? rawValue.substring(rawValue.lastIndexOf('\n') + 1).trim() : rawValue.trim();
            if (value.isEmpty()) {
                continue;
            }

            Color direct = parseColor(value);
            if (direct != null) {
                return direct;
            }

            if (value.startsWith("-") && !isGradient(value)) {
                Color chained = resolveVariableColorInternal(value, project, scope, visited, depth + 1);
                if (chained != null) {
                    return chained;
                }
            }
        }

        return null;
    }

    @Nullable
    private static Color parseHex(@NotNull String hex) {
        try {
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3), 16);
                return new Color(r * 17, g * 17, b * 17);
            } else if (hex.length() == 6) {
                return Color.decode("#" + hex);
            } else if (hex.length() == 8) {
                // JavaFX CSS uses #RRGGBBAA format (alpha at end)
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                int alpha = Integer.parseInt(hex.substring(6, 8), 16);
                return new Color(r, g, b, alpha);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }

    @NotNull
    private static Color hslToColor(float h, float s, float l, float alpha) {
        float r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            float hNorm = h / 360f;
            r = hueToRgb(p, q, hNorm + 1f / 3f);
            g = hueToRgb(p, q, hNorm);
            b = hueToRgb(p, q, hNorm - 1f / 3f);
        }
        return new Color(
                clamp(Math.round(r * 255)),
                clamp(Math.round(g * 255)),
                clamp(Math.round(b * 255)),
                clamp(Math.round(alpha * 255)));
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1f / 6f) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5f) {
            return q;
        }
        if (t < 2f / 3f) {
            return p + (q - p) * (2f / 3f - t) * 6;
        }
        return p;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
