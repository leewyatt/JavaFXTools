package io.github.leewyatt.fxtools.css.preview;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.css.preview.effect.EffectConfig;
import io.github.leewyatt.fxtools.css.preview.effect.FxEffectParser;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.util.FxGradientParser;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility for finding previewable CSS values in inline style text
 * and creating the appropriate gutter icons.
 */
public final class InlineCssGutterUtil {

    /**
     * Upper bound on gutter icons emitted per inline CSS snippet (single string /
     * single text block line / single FXML style attribute). IntelliJ's gutter can
     * stack multiple icons on the same line; this cap keeps extreme cases bounded.
     */
    public static final int MAX_INLINE_MARKERS = 4;

    public enum MatchType { COLOR, GRADIENT, SVG_PATH, EFFECT, ICON_CODE }

    /**
     * Information about a previewable CSS value found in inline style text.
     */
    public static final class PreviewMatch {
        private final MatchType type;
        private final String value;
        private final int valueStart;
        private final int valueEnd;
        private final String propertyName;
        private final boolean variableRef;

        PreviewMatch(@NotNull MatchType type, @NotNull String value,
                     int valueStart, int valueEnd, @NotNull String propertyName,
                     boolean variableRef) {
            this.type = type;
            this.value = value;
            this.valueStart = valueStart;
            this.valueEnd = valueEnd;
            this.propertyName = propertyName;
            this.variableRef = variableRef;
        }

        public @NotNull MatchType getType() { return type; }

        /** Parsed value: unquoted path data for SVG, raw CSS text for colors/gradients/variables. */
        public @NotNull String getValue() { return value; }

        /** Start offset of the raw value text within the CSS string. */
        public int getValueStart() { return valueStart; }

        /** End offset of the raw value text within the CSS string. */
        public int getValueEnd() { return valueEnd; }

        public @NotNull String getPropertyName() { return propertyName; }

        public boolean isVariableRef() { return variableRef; }
    }

    private InlineCssGutterUtil() {
    }

    /**
     * Returns {@code true} if the given match type is enabled in the current settings.
     */
    public static boolean isMatchTypeEnabled(@NotNull MatchType type) {
        return FxToolsSettingsState.getInstance().enableGutterPreviews;
    }

    /**
     * Scans inline CSS text and returns all previewable values, up to
     * {@link #MAX_INLINE_MARKERS}. Declarations are walked left-to-right.
     * Paint-list properties (see {@link CssPaintListScanner#PAINT_LIST_PROPERTIES},
     * e.g. {@code -fx-background-color}) emit one match per comma segment; other
     * declarations emit at most one match from their first value.
     *
     * @param cssText the CSS text content (without surrounding quotes)
     * @param allVars cached CSS variable map for variable-reference resolution
     * @return list of matches (possibly empty, never null)
     */
    @NotNull
    public static List<PreviewMatch> findAllPreviewables(@NotNull String cssText,
                                                          @NotNull Map<String, List<String>> allVars) {
        List<PreviewMatch> results = new ArrayList<>();
        int pos = 0;
        int len = cssText.length();

        outer:
        while (pos < len && results.size() < MAX_INLINE_MARKERS) {
            while (pos < len && Character.isWhitespace(cssText.charAt(pos))) {
                pos++;
            }
            if (pos >= len) {
                break;
            }

            int colonIdx = cssText.indexOf(':', pos);
            if (colonIdx < 0) {
                break;
            }

            String propName = cssText.substring(pos, colonIdx).trim();

            int valueStart = colonIdx + 1;
            while (valueStart < len && Character.isWhitespace(cssText.charAt(valueStart))) {
                valueStart++;
            }

            int valueEnd = findValueEnd(cssText, valueStart);

            int trimmedEnd = valueEnd;
            while (trimmedEnd > valueStart && Character.isWhitespace(cssText.charAt(trimmedEnd - 1))) {
                trimmedEnd--;
            }

            if (trimmedEnd > valueStart) {
                String fullValue = cssText.substring(valueStart, trimmedEnd);

                if (CssPaintListScanner.isPaintListProperty(propName)) {
                    // Multi-value paint: comma segments for background-color,
                    // comma+space (nested) segments for border-color.
                    List<CssPaintListScanner.Segment> segments =
                            CssPaintListScanner.scanForProperty(propName, fullValue);
                    for (CssPaintListScanner.Segment seg : segments) {
                        if (results.size() >= MAX_INLINE_MARKERS) {
                            break outer;
                        }
                        int segStart = valueStart + seg.getStartInValue();
                        int segEnd = valueStart + seg.getEndInValue();
                        PreviewMatch m = checkValue(
                                seg.getText(), segStart, segEnd, propName, allVars);
                        if (m != null) {
                            results.add(m);
                        }
                    }
                } else {
                    // Single-value declaration: only preview the first comma-segment.
                    String firstValue = extractFirstValue(fullValue);
                    int fvStart = valueStart + fullValue.indexOf(firstValue);
                    int fvEnd = fvStart + firstValue.length();

                    PreviewMatch match = checkValue(firstValue, fvStart, fvEnd, propName, allVars);
                    if (match != null) {
                        results.add(match);
                    }
                }
            }

            pos = valueEnd < len && cssText.charAt(valueEnd) == ';' ? valueEnd + 1 : len;
        }

        return results;
    }

    /**
     * Checks if a CSS value is previewable (color, gradient, variable reference, or SVG path).
     */
    @Nullable
    private static PreviewMatch checkValue(@NotNull String value, int start, int end,
                                            @NotNull String propName,
                                            @NotNull Map<String, List<String>> allVars) {
        // Effect (dropshadow / innershadow)
        if (FxEffectParser.isEffect(value)) {
            return new PreviewMatch(MatchType.EFFECT, value, start, end, propName, false);
        }

        // Gradient (direct)
        if (FxColorParser.isGradient(value)) {
            return new PreviewMatch(MatchType.GRADIENT, value, start, end, propName, false);
        }

        // Derive or direct color
        if (FxColorParser.isDerive(value) || FxColorParser.isDirectColor(value)) {
            return new PreviewMatch(MatchType.COLOR, value, start, end, propName, false);
        }

        // SVG path (direct, only -fx-shape)
        if ("-fx-shape".equals(propName)) {
            String pathData = unquote(value);
            if (pathData != null && FxSvgRenderer.isSvgPath(pathData)) {
                return new PreviewMatch(MatchType.SVG_PATH, pathData, start, end, propName, false);
            }
        }

        // Icon code (Ikonli) — resolve literal to SVG path
        if ("-fx-icon-code".equals(propName)) {
            String literal = unquote(value);
            if (literal == null) {
                literal = value;
            }
            literal = literal.trim();
            if (!literal.isEmpty()) {
                return new PreviewMatch(MatchType.ICON_CODE, literal, start, end, propName, false);
            }
        }

        // Variable reference — resolve to color, gradient, or SVG path
        if (FxColorParser.isVariableReference(value)) {
            return resolveVariable(value, start, end, propName, allVars);
        }

        return null;
    }

    /**
     * Resolves a CSS variable reference to a previewable value.
     * Checks effect first, then color, then gradient, then SVG path.
     */
    @Nullable
    private static PreviewMatch resolveVariable(@NotNull String varName, int start, int end,
                                                 @NotNull String propName,
                                                 @NotNull Map<String, List<String>> allVars) {
        // Variable → color
        Color resolved = FxColorParser.resolveVariableColor(varName, allVars);
        if (resolved != null) {
            return new PreviewMatch(MatchType.COLOR, varName, start, end, propName, true);
        }

        // Variable → gradient, effect, or SVG path
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String rawValue : values) {
            String trimmed = rawValue.trim();

            // Variable → effect
            if (FxEffectParser.isEffect(trimmed)) {
                return new PreviewMatch(MatchType.EFFECT, trimmed, start, end, propName, true);
            }

            // Variable → gradient
            if (FxColorParser.isGradient(trimmed)) {
                return new PreviewMatch(MatchType.GRADIENT, trimmed, start, end, propName, true);
            }

            // Variable → SVG path (quoted or unquoted)
            String pathData = unquote(trimmed);
            if (pathData != null && FxSvgRenderer.isSvgPath(pathData)) {
                return new PreviewMatch(MatchType.SVG_PATH, pathData, start, end, propName, true);
            }
            if (FxSvgRenderer.isSvgPath(trimmed)) {
                return new PreviewMatch(MatchType.SVG_PATH, trimmed, start, end, propName, true);
            }
        }

        return null;
    }

    /**
     * Creates a gutter preview icon for the given match.
     */
    @Nullable
    public static Icon createIcon(@NotNull PreviewMatch match, @NotNull Project project,
                                   @NotNull Map<String, List<String>> allVars) {
        switch (match.type) {
            case COLOR -> {
                if (match.variableRef) {
                    List<FxColorParser.ResolvedColor> resolved =
                            FxColorParser.resolveVariableColors(match.value, allVars);
                    if (resolved.size() >= 1) {
                        return CssPreviewIconRenderer.createSquareIcon(resolved.get(0).getColor());
                    }
                    return null;
                }
                Color color;
                if (FxColorParser.isDerive(match.value)) {
                    color = FxColorParser.parseDeriveColor(match.value);
                } else {
                    color = FxColorParser.parseColor(match.value);
                }
                return color != null ? CssPreviewIconRenderer.createSquareIcon(color) : null;
            }
            case GRADIENT -> {
                Paint paint = null;
                String v = match.value.toLowerCase();
                if (v.startsWith("linear-gradient")) {
                    FxGradientParser.LinearGradientInfo info =
                            FxGradientParser.parseLinearGradient(match.value, allVars);
                    if (info != null && info.getStops().size() >= 2) {
                        paint = info.toAwtPaint(16, 16);
                    }
                } else if (v.startsWith("radial-gradient")) {
                    FxGradientParser.RadialGradientInfo info =
                            FxGradientParser.parseRadialGradient(match.value, allVars);
                    if (info != null && info.getStops().size() >= 2) {
                        paint = info.toAwtPaint(16, 16);
                    }
                }
                return paint != null ? CssPreviewIconRenderer.createGradientIcon(paint) : null;
            }
            case SVG_PATH -> {
                return CssPreviewIconRenderer.createSvgIcon(match.value, JBColor.foreground());
            }
            case EFFECT -> {
                EffectConfig cfg = FxEffectParser.parseEffect(match.value, project);
                if (cfg != null) {
                    return CssPreviewIconRenderer.createEffectIcon(cfg);
                }
                return null;
            }
            case ICON_CODE -> {
                return resolveIconCodeIcon(match.value, project);
            }
        }
        return null;
    }

    private static int findValueEnd(@NotNull String text, int start) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
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
            } else if (c == ';' && depth == 0) {
                return i;
            }
        }
        return text.length();
    }

    @NotNull
    private static String extractFirstValue(@NotNull String value) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
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
                return value.substring(0, i).trim();
            }
        }
        return value.trim();
    }

    private static final Color ICON_CODE_COLOR =
            new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY);

    /**
     * Resolves an Ikonli icon literal to an SVG preview icon. Returns a placeholder
     * icon when the literal refers to an icon with {@code np: true}; returns null
     * when the literal is unknown or its pack is not on the classpath.
     */
    @Nullable
    private static Icon resolveIconCodeIcon(@NotNull String literal, @NotNull Project project) {
        IconDataService service = IconDataService.getInstance();
        if (!service.isLoaded()) {
            return null;
        }
        Set<String> availablePacks = IconDataService.getAvailablePacks(project);
        IconDataService.IconEntry icon = service.getLiteralMap().get(literal);
        if (icon == null || !availablePacks.contains(icon.getPackId())) {
            return null;
        }
        if (!icon.isRenderable()) {
            return io.github.leewyatt.fxtools.toolwindow.iconbrowser
                    .IconPlaceholder.createIcon(CssPreviewIconRenderer.getGutterIconSize());
        }
        if (!service.isPackLoaded(icon.getPackId())) {
            service.ensurePackLoaded(icon.getPack());
        }
        String pathData = service.getPath(icon);
        if (pathData == null) {
            return null;
        }
        return CssPreviewIconRenderer.createSvgIcon(pathData, ICON_CODE_COLOR);
    }

    @Nullable
    private static String unquote(@NotNull String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return null;
    }
}
