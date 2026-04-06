package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JavaFX CSS gradient strings into renderable data structures.
 */
public final class FxGradientParser {

    private static final Pattern LINEAR_GRADIENT_PATTERN =
            Pattern.compile("^linear-gradient\\s*\\((.+)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RADIAL_GRADIENT_PATTERN =
            Pattern.compile("^radial-gradient\\s*\\((.+)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("([\\d.]+)%");

    private FxGradientParser() {
    }

    /**
     * Represents a parsed linear gradient.
     */
    public static final class LinearGradientInfo {
        private final float startX, startY, endX, endY;
        private final List<Stop> stops;
        private final boolean proportional;

        public LinearGradientInfo(float startX, float startY, float endX, float endY,
                                  @NotNull List<Stop> stops, boolean proportional) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.stops = stops;
            this.proportional = proportional;
        }

        public float getStartX() { return startX; }
        public float getStartY() { return startY; }
        public float getEndX() { return endX; }
        public float getEndY() { return endY; }
        public List<Stop> getStops() { return stops; }
        public boolean isProportional() { return proportional; }

        /**
         * Converts to java.awt.LinearGradientPaint for rendering.
         */
        public LinearGradientPaint toAwtPaint(float width, float height) {
            float sx = proportional ? startX * width : startX;
            float sy = proportional ? startY * height : startY;
            float ex = proportional ? endX * width : endX;
            float ey = proportional ? endY * height : endY;
            if (sx == ex && sy == ey) {
                ey = sy + 1;
            }
            float[] fractions = new float[stops.size()];
            Color[] colors = new Color[stops.size()];
            for (int i = 0; i < stops.size(); i++) {
                fractions[i] = stops.get(i).getOffset();
                colors[i] = stops.get(i).getColor();
            }
            ensureStrictlyIncreasing(fractions);
            return new LinearGradientPaint(sx, sy, ex, ey, fractions, colors);
        }
    }

    /**
     * Represents a parsed radial gradient.
     */
    public static final class RadialGradientInfo {
        private final float centerX, centerY;
        private final float radius;
        private final float focusAngle, focusDistance;
        private final List<Stop> stops;
        private final boolean proportional;

        public RadialGradientInfo(float centerX, float centerY, float radius,
                                  float focusAngle, float focusDistance,
                                  @NotNull List<Stop> stops, boolean proportional) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.focusAngle = focusAngle;
            this.focusDistance = focusDistance;
            this.stops = stops;
            this.proportional = proportional;
        }

        public float getCenterX() { return centerX; }
        public float getCenterY() { return centerY; }
        public float getRadius() { return radius; }
        public float getFocusAngle() { return focusAngle; }
        public float getFocusDistance() { return focusDistance; }
        public List<Stop> getStops() { return stops; }
        public boolean isProportional() { return proportional; }

        /**
         * Converts to java.awt.RadialGradientPaint for rendering.
         */
        public RadialGradientPaint toAwtPaint(float width, float height) {
            float cx = proportional ? centerX * width : centerX;
            float cy = proportional ? centerY * height : centerY;
            float r = proportional ? radius * Math.min(width, height) : radius;
            if (r <= 0) {
                r = 1;
            }
            float[] fractions = new float[stops.size()];
            Color[] colors = new Color[stops.size()];
            for (int i = 0; i < stops.size(); i++) {
                fractions[i] = stops.get(i).getOffset();
                colors[i] = stops.get(i).getColor();
            }
            ensureStrictlyIncreasing(fractions);
            return new RadialGradientPaint(cx, cy, r, fractions, colors);
        }
    }

    /**
     * Represents a gradient stop.
     */
    public static final class Stop {
        private final float offset;
        private final Color color;

        public Stop(float offset, @NotNull Color color) {
            this.offset = offset;
            this.color = color;
        }

        public float getOffset() { return offset; }
        public Color getColor() { return color; }
    }

    /**
     * Checks if a string is a gradient value.
     */
    public static boolean isGradient(@NotNull String value) {
        String lower = value.trim().toLowerCase();
        return lower.startsWith("linear-gradient(") || lower.startsWith("radial-gradient(");
    }

    /**
     * Parses a linear-gradient() CSS value.
     */
    @Nullable
    public static LinearGradientInfo parseLinearGradient(@NotNull String value,
                                                         @Nullable Project project,
                                                         @Nullable GlobalSearchScope scope) {
        Matcher m = LINEAR_GRADIENT_PATTERN.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }

        String inner = m.group(1).trim();
        List<String> parts = splitTopLevel(inner);
        if (parts.size() < 2) {
            return null;
        }

        float startX = 0, startY = 0, endX = 0, endY = 1;
        boolean proportional = true;
        int colorStartIndex = 0;

        String first = parts.get(0).trim().toLowerCase();
        if (first.startsWith("from ")) {
            String coords = first.substring(5).trim();
            int toIdx = coords.indexOf(" to ");
            if (toIdx > 0) {
                float[] from = parsePoint(coords.substring(0, toIdx).trim());
                float[] to = parsePoint(coords.substring(toIdx + 4).trim());
                if (from != null && to != null) {
                    startX = from[0]; startY = from[1];
                    endX = to[0]; endY = to[1];
                }
            }
            colorStartIndex = 1;
        } else if (first.startsWith("to ")) {
            String direction = first.substring(3).trim();
            float[] dir = parseDirection(direction);
            if (dir != null) {
                startX = dir[0]; startY = dir[1];
                endX = dir[2]; endY = dir[3];
            }
            colorStartIndex = 1;
        }

        // Check for repeat/reflect
        if (colorStartIndex < parts.size()) {
            String next = parts.get(colorStartIndex).trim().toLowerCase();
            if ("repeat".equals(next) || "reflect".equals(next)) {
                colorStartIndex++;
            }
        }

        List<Stop> stops = parseColorStops(parts, colorStartIndex, project, scope);
        if (stops.size() < 2) {
            return null;
        }

        return new LinearGradientInfo(startX, startY, endX, endY, stops, proportional);
    }

    /**
     * Parses a radial-gradient() CSS value.
     */
    @Nullable
    public static RadialGradientInfo parseRadialGradient(@NotNull String value,
                                                          @Nullable Project project,
                                                          @Nullable GlobalSearchScope scope) {
        Matcher m = RADIAL_GRADIENT_PATTERN.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }

        String inner = m.group(1).trim();
        List<String> parts = splitTopLevel(inner);
        if (parts.size() < 2) {
            return null;
        }

        float centerX = 0.5f, centerY = 0.5f, radius = 0.5f;
        float focusAngle = 0, focusDistance = 0;
        boolean proportional = true;
        int colorStartIndex = 0;

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim().toLowerCase();
            if (part.startsWith("focus-angle ")) {
                focusAngle = parseAngle(part.substring(12).trim());
                colorStartIndex = i + 1;
            } else if (part.startsWith("focus-distance ")) {
                focusDistance = parsePercentage(part.substring(15).trim());
                colorStartIndex = i + 1;
            } else if (part.startsWith("center ")) {
                float[] point = parsePoint(part.substring(7).trim());
                if (point != null) {
                    centerX = point[0];
                    centerY = point[1];
                }
                colorStartIndex = i + 1;
            } else if (part.startsWith("radius ")) {
                radius = parsePercentage(part.substring(7).trim());
                colorStartIndex = i + 1;
            } else if ("repeat".equals(part) || "reflect".equals(part)) {
                colorStartIndex = i + 1;
            } else {
                break;
            }
        }

        List<Stop> stops = parseColorStops(parts, colorStartIndex, project, scope);
        if (stops.size() < 2) {
            return null;
        }

        return new RadialGradientInfo(centerX, centerY, radius, focusAngle, focusDistance,
                stops, proportional);
    }

    @NotNull
    private static List<Stop> parseColorStops(@NotNull List<String> parts, int startIndex,
                                              @Nullable Project project,
                                              @Nullable GlobalSearchScope scope) {
        List<Stop> stops = new ArrayList<>();
        List<String> colorParts = parts.subList(startIndex, parts.size());

        for (int i = 0; i < colorParts.size(); i++) {
            String part = colorParts.get(i).trim();
            float offset = -1;
            String colorStr = part;

            Matcher pctMatcher = PERCENTAGE_PATTERN.matcher(part);
            int lastPctStart = -1;
            int lastPctEnd = -1;
            float lastPct = -1;
            while (pctMatcher.find()) {
                lastPct = Float.parseFloat(pctMatcher.group(1)) / 100f;
                lastPctStart = pctMatcher.start();
                lastPctEnd = pctMatcher.end();
            }
            if (lastPctEnd > 0 && lastPctEnd == part.length()) {
                offset = lastPct;
                colorStr = part.substring(0, lastPctStart).trim();
                if (colorStr.isEmpty() && i > 0) {
                    continue;
                }
            }

            Color color = FxColorParser.parseColor(colorStr);
            if (color == null && project != null && scope != null && colorStr.startsWith("-")) {
                color = FxColorParser.resolveVariableColor(colorStr, project, scope);
            }
            if (color == null) {
                color = Color.GRAY;
            }

            stops.add(new Stop(offset, color));
        }

        // Auto-assign offsets
        if (!stops.isEmpty()) {
            if (stops.get(0).getOffset() < 0) {
                stops.set(0, new Stop(0f, stops.get(0).getColor()));
            }
            if (stops.get(stops.size() - 1).getOffset() < 0) {
                stops.set(stops.size() - 1, new Stop(1f, stops.get(stops.size() - 1).getColor()));
            }

            for (int i = 1; i < stops.size() - 1; i++) {
                if (stops.get(i).getOffset() < 0) {
                    int prev = i - 1;
                    int next = i + 1;
                    while (next < stops.size() && stops.get(next).getOffset() < 0) {
                        next++;
                    }
                    if (next < stops.size()) {
                        float start = stops.get(prev).getOffset();
                        float end = stops.get(next).getOffset();
                        float step = (end - start) / (next - prev);
                        for (int j = prev + 1; j < next; j++) {
                            stops.set(j, new Stop(start + step * (j - prev), stops.get(j).getColor()));
                        }
                    }
                }
            }
        }

        return stops;
    }

    @Nullable
    private static float[] parseDirection(@NotNull String direction) {
        switch (direction) {
            case "bottom": return new float[]{0, 0, 0, 1};
            case "top": return new float[]{0, 1, 0, 0};
            case "right": return new float[]{0, 0, 1, 0};
            case "left": return new float[]{1, 0, 0, 0};
            case "bottom right": return new float[]{0, 0, 1, 1};
            case "top left": return new float[]{1, 1, 0, 0};
            case "top right": return new float[]{0, 1, 1, 0};
            case "bottom left": return new float[]{1, 0, 0, 1};
            default: return null;
        }
    }

    @Nullable
    private static float[] parsePoint(@NotNull String point) {
        String[] parts = point.split("\\s+");
        if (parts.length == 2) {
            return new float[]{parsePercentage(parts[0]), parsePercentage(parts[1])};
        }
        return null;
    }

    private static float parsePercentage(@NotNull String value) {
        String v = value.trim();
        if (v.endsWith("%")) {
            try {
                return Float.parseFloat(v.substring(0, v.length() - 1)) / 100f;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (v.endsWith("deg")) {
            try {
                return Float.parseFloat(v.substring(0, v.length() - 3));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float parseAngle(@NotNull String value) {
        String v = value.trim();
        if (v.endsWith("deg")) {
            try {
                return Float.parseFloat(v.substring(0, v.length() - 3));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @NotNull
    private static List<String> splitTopLevel(@NotNull String input) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(input.substring(start, i));
                start = i + 1;
            }
        }
        if (start < input.length()) {
            result.add(input.substring(start));
        }
        return result;
    }

    private static void ensureStrictlyIncreasing(float[] fractions) {
        for (int i = 1; i < fractions.length; i++) {
            if (fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + 0.0001f;
            }
        }
    }
}
