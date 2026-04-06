package io.github.leewyatt.fxtools.css.preview;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits a CSS property value into comma-separated segments, tracking parenthesis
 * and quote depth so that commas inside function calls (e.g. {@code rgba(255,0,0,0.5)},
 * {@code linear-gradient(red, blue)}) or quoted strings (e.g. {@code 'M1,2L3,4'}) are
 * not treated as top-level separators.
 *
 * <p>Used by the .css color and gradient gutter providers to support multi-value
 * paint properties such as {@code -fx-background-color}.</p>
 */
public final class CssPaintListScanner {

    /** Upper bound on segments previewed per declaration. */
    public static final int MAX_SEGMENTS = 4;

    /**
     * Properties whose value is a flat comma-separated list of paints —
     * one paint per layer. Example: {@code -fx-background-color: red, #000, linear-gradient(...);}.
     */
    public static final Set<String> PAINT_LIST_PROPERTIES = Set.of(
            "-fx-background-color"
    );

    /**
     * Properties whose value is a comma-separated list where each layer can itself
     * be 1–4 space-separated paints ({@code top right bottom left}). Example:
     * {@code -fx-border-color: red blue green yellow, black;}.
     * Scanning yields a flat list of every paint encountered, up to {@link #MAX_SEGMENTS}.
     */
    public static final Set<String> PAINT_GRID_PROPERTIES = Set.of(
            "-fx-border-color"
    );

    /**
     * Returns {@code true} if the given property accepts any form of multi-paint
     * value (either simple list or nested grid). Callers use this to decide whether
     * to emit per-segment gutter markers vs. the single-value historical behavior.
     */
    public static boolean isPaintListProperty(@NotNull String propertyName) {
        return PAINT_LIST_PROPERTIES.contains(propertyName)
                || PAINT_GRID_PROPERTIES.contains(propertyName);
    }

    /** A single comma-separated piece of a CSS value. */
    public static final class Segment {
        private final String text;
        private final int startInValue;
        private final int endInValue;

        Segment(@NotNull String text, int startInValue, int endInValue) {
            this.text = text;
            this.startInValue = startInValue;
            this.endInValue = endInValue;
        }

        /** Trimmed segment text. */
        public @NotNull String getText() { return text; }

        /** Start offset of the trimmed segment, relative to the start of the full value. */
        public int getStartInValue() { return startInValue; }

        /** End offset of the trimmed segment, relative to the start of the full value. */
        public int getEndInValue() { return endInValue; }
    }

    private CssPaintListScanner() {
    }

    /**
     * Picks the correct scanning strategy for a property: nested (comma + space)
     * for paint-grid properties, flat (comma only) for paint-list properties.
     * Callers that have already classified the property via
     * {@link #isPaintListProperty(String)} should use this method to get segments.
     */
    @NotNull
    public static List<Segment> scanForProperty(@NotNull String propertyName,
                                                 @NotNull String value) {
        if (PAINT_GRID_PROPERTIES.contains(propertyName)) {
            return scanNested(value);
        }
        return scan(value);
    }

    /**
     * Splits {@code value} on top-level commas, ignoring commas inside parentheses
     * or quotes. Returns at most {@link #MAX_SEGMENTS} trimmed segments.
     */
    @NotNull
    public static List<Segment> scan(@NotNull String value) {
        List<Segment> result = new ArrayList<>();
        int len = value.length();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int segStart = 0;

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
            } else if (c == '\'') {
                inSingleQuote = true;
            } else if (c == '"') {
                inDoubleQuote = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                addSegment(result, value, segStart, i);
                if (result.size() >= MAX_SEGMENTS) {
                    return result;
                }
                segStart = i + 1;
            }
        }
        addSegment(result, value, segStart, len);
        return result;
    }

    private static void addSegment(@NotNull List<Segment> out, @NotNull String value,
                                   int rawStart, int rawEnd) {
        int s = rawStart;
        int e = rawEnd;
        while (s < e && Character.isWhitespace(value.charAt(s))) {
            s++;
        }
        while (e > s && Character.isWhitespace(value.charAt(e - 1))) {
            e--;
        }
        if (e > s) {
            out.add(new Segment(value.substring(s, e), s, e));
        }
    }

    // ==================== Nested Scan (comma + space) ====================

    /**
     * Two-level scan for paint-grid properties such as {@code -fx-border-color}.
     * <ol>
     *   <li>Splits on top-level commas to get per-layer segments.</li>
     *   <li>Within each layer, splits on top-level whitespace to extract 1–4
     *       individual paints (ignoring spaces inside parentheses or quotes,
     *       e.g. {@code linear-gradient(from 0% 0% to 100% 100%, red, blue)}).</li>
     * </ol>
     * Returns a flat list of every paint piece across all layers, capped at
     * {@link #MAX_SEGMENTS}. Offsets are relative to the start of {@code value}.
     */
    @NotNull
    static List<Segment> scanNested(@NotNull String value) {
        List<Segment> layers = scan(value);
        List<Segment> result = new ArrayList<>();
        for (Segment layer : layers) {
            splitBySpace(value, layer.getStartInValue(), layer.getEndInValue(), result);
            if (result.size() >= MAX_SEGMENTS) {
                return result.subList(0, Math.min(result.size(), MAX_SEGMENTS));
            }
        }
        return result;
    }

    /**
     * Splits a slice of {@code value} on top-level whitespace, respecting
     * parentheses and quotes. Appends trimmed segments to {@code out}.
     */
    private static void splitBySpace(@NotNull String value, int start, int end,
                                     @NotNull List<Segment> out) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int i = start;

        while (i < end) {
            // Skip leading whitespace
            while (i < end && Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            if (i >= end) {
                break;
            }

            int segStart = i;
            while (i < end) {
                char c = value.charAt(i);
                if (inSingleQuote) {
                    if (c == '\'') {
                        inSingleQuote = false;
                    }
                    i++;
                    continue;
                }
                if (inDoubleQuote) {
                    if (c == '"') {
                        inDoubleQuote = false;
                    }
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inSingleQuote = true;
                    i++;
                    continue;
                }
                if (c == '"') {
                    inDoubleQuote = true;
                    i++;
                    continue;
                }
                if (c == '(') {
                    depth++;
                    i++;
                    continue;
                }
                if (c == ')') {
                    depth--;
                    i++;
                    continue;
                }
                if (depth == 0 && Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }

            if (i > segStart) {
                out.add(new Segment(value.substring(segStart, i), segStart, i));
                if (out.size() >= MAX_SEGMENTS) {
                    return;
                }
            }
        }
    }
}
