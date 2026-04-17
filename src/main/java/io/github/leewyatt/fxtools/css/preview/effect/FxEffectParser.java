package io.github.leewyatt.fxtools.css.preview.effect;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.util.FxColorParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSS dropshadow() and innershadow() function calls into EffectConfig.
 */
public final class FxEffectParser {

    /**
     * Pattern to match dropshadow(...) or innershadow(...) expressions.
     * Content group uses manual parenthesis-aware extraction to handle rgba() etc.
     */
    private static final Pattern EFFECT_START_PATTERN = Pattern.compile(
            "(dropshadow|innershadow)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BLUR_TYPE_PATTERN = Pattern.compile(
            "^(gaussian|one-pass-box|two-pass-box|three-pass-box)$",
            Pattern.CASE_INSENSITIVE
    );

    private FxEffectParser() {
    }

    /**
     * Checks if the given CSS value is a dropshadow() or innershadow() expression.
     */
    public static boolean isEffect(@NotNull String value) {
        String lower = value.trim().toLowerCase();
        return lower.startsWith("dropshadow(") || lower.startsWith("innershadow(");
    }

    /**
     * Parses a CSS effect expression into an EffectConfig.
     *
     * @param effectExpr the CSS effect expression (e.g., "dropshadow(three-pass-box, #000000, 10, 0.0, 0, 0)")
     * @param project    the project (for resolving color variables)
     * @return the parsed config, or null if parsing fails
     */
    @Nullable
    public static EffectConfig parseEffect(@NotNull String effectExpr, @Nullable Project project) {
        String trimmed = effectExpr.trim();
        Matcher matcher = EFFECT_START_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            return null;
        }

        String funcName = matcher.group(1).toLowerCase();
        int argsStart = matcher.end();
        String argsStr = extractParenContent(trimmed, argsStart);
        if (argsStr == null) {
            return null;
        }

        EffectConfig config = new EffectConfig();
        config.setEffectType("dropshadow".equals(funcName) ? EffectType.DROPSHADOW : EffectType.INNERSHADOW);

        // Split arguments by comma, respecting parentheses (for rgba(...), etc.)
        String[] args = splitArgs(argsStr);
        if (args.length < 6) {
            return null;
        }

        // Arg 0: blur type
        String blurType = args[0].trim().toLowerCase();
        if (!BLUR_TYPE_PATTERN.matcher(blurType).matches()) {
            return null;
        }
        config.setBlurType(blurType);

        // Arg 1: color (may be multi-token for rgba/hsla/etc.)
        String colorStr = args[1].trim();
        Color color = FxColorParser.parseColor(colorStr);
        if (color == null && project != null && FxColorParser.isVariableReference(colorStr)) {
            color = FxColorParser.resolveVariableColor(colorStr, project,
                    GlobalSearchScope.allScope(project));
        }
        if (color == null) {
            color = Color.BLACK;
        }
        config.setColor(color);

        // Arg 2: radius
        try {
            config.setRadius(Double.parseDouble(args[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }

        // Arg 3: spread/choke
        try {
            config.setSpreadOrChoke(Double.parseDouble(args[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }

        // Arg 4: offsetX
        try {
            config.setOffsetX(Double.parseDouble(args[4].trim()));
        } catch (NumberFormatException e) {
            return null;
        }

        // Arg 5: offsetY
        try {
            config.setOffsetY(Double.parseDouble(args[5].trim()));
        } catch (NumberFormatException e) {
            return null;
        }

        // Compute width/height from radius
        double r = config.getRadius();
        config.setWidth(r * 2 + 1);
        config.setHeight(r * 2 + 1);

        return config;
    }

    /**
     * Extracts content between the outermost parentheses, handling nesting.
     *
     * @param text  the full text
     * @param start position right after the opening parenthesis
     * @return the content string, or null if no matching close paren found
     */
    @Nullable
    private static String extractParenContent(@NotNull String text, int start) {
        int depth = 1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i);
                }
            }
        }
        return null;
    }

    /**
     * Splits argument string by commas, respecting parentheses nesting.
     * This handles cases like "rgba(0,0,0,0.3)" as a single argument.
     */
    private static String[] splitArgs(@NotNull String argsStr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(argsStr.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(argsStr.substring(start));
        return parts.toArray(new String[0]);
    }
}
