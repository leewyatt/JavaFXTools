package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import io.github.leewyatt.fxtools.css.FxCssPropertyTable;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import io.github.leewyatt.fxtools.css.preview.effect.EffectConfig;
import io.github.leewyatt.fxtools.css.preview.effect.FxEffectParser;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconPlaceholder;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.util.FxGradientParser;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared CSS completion logic used by both .css file completion and Java/FXML inline completion.
 */
public final class FxCssCompletionUtil {

    private static final Pattern QUOTED_STRING = Pattern.compile("^[\"']([^\"']+)[\"']$");
    private static final Pattern EFFECT_PATTERN =
            Pattern.compile("(?i)^(dropshadow|innershadow)\\s*\\(");

    private FxCssCompletionUtil() {
    }

    /**
     * Builds a display string for property sources.
     * Built-in: "FlowPane, GridPane"
     * Single library: "SearchField (GemsFX)"
     * Multiple libraries: "GridView (ControlsFX), Spacer (GemsFX)"
     */
    @NotNull
    static String buildSourceText(@NotNull FxCssPropertyTable.PropertyInfo info) {
        List<FxCssPropertyTable.SourceEntry> sources = info.getSources();
        if (sources.size() == 1) {
            FxCssPropertyTable.SourceEntry src = sources.get(0);
            if (src.getLibrary() == null) {
                return src.getAppliesTo();
            }
            return src.getAppliesTo() + " (" + src.getLibrary() + ")";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            FxCssPropertyTable.SourceEntry src = sources.get(i);
            sb.append(src.getAppliesTo());
            if (src.getLibrary() != null) {
                sb.append(" (").append(src.getLibrary()).append(")");
            }
        }
        return sb.toString();
    }

    // ---- Property name completion ----

    /**
     * Adds built-in and library CSS properties plus project custom properties to the completion list.
     */
    public static void addPropertyNameCompletions(@NotNull CompletionResultSet result,
                                                   @NotNull InsertHandler<LookupElement> insertHandler,
                                                   @NotNull Project project) {
        // Built-in + library properties (filtered by project classpath)
        Set<String> builtInNames = new HashSet<>();
        for (FxCssPropertyTable.PropertyInfo info : FxCssPropertyTable.getAllProperties(project)) {
            builtInNames.add(info.getName());
            LookupElementBuilder builder = LookupElementBuilder.create(info.getName())
                    .withIcon(AllIcons.FileTypes.Css)
                    .withTailText("  " + buildSourceText(info), true)
                    .withTypeText(info.getValueType(), true)
                    .withInsertHandler(insertHandler);
            result.addElement(PrioritizedLookupElement.withPriority(builder, 100));
        }

        // Project custom properties (from cached snapshot)
        Map<String, List<String>> allVars = FxCssPropertyIndex.getAllVariables(project);
        for (String name : allVars.keySet()) {
            if (builtInNames.contains(name)) {
                continue;
            }
            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTailText("  (project)", true)
                    .withInsertHandler(insertHandler);
            result.addElement(PrioritizedLookupElement.withPriority(builder, 50));
        }
    }

    // ---- Property value completion (type-aware) ----

    public static void addValueCompletions(@Nullable FxCssPropertyTable.PropertyInfo info,
                                            @NotNull CompletionParameters parameters,
                                            @NotNull CompletionResultSet result,
                                            @NotNull InsertHandler<LookupElement> valueInsertHandler) {
        addValueCompletions(info, null, parameters, result, valueInsertHandler);
    }

    /**
     * Adds value completions with type inference for custom properties.
     *
     * @param info     built-in property info, or null for custom properties
     * @param propName the property name (used for type inference when info is null)
     */
    public static void addValueCompletions(@Nullable FxCssPropertyTable.PropertyInfo info,
                                            @Nullable String propName,
                                            @NotNull CompletionParameters parameters,
                                            @NotNull CompletionResultSet result,
                                            @NotNull InsertHandler<LookupElement> valueInsertHandler) {
        // Collect all value types and predefined values from all sources
        Set<String> valueTypes = new LinkedHashSet<>();
        Set<String> addedValues = new HashSet<>();

        if (info != null) {
            for (FxCssPropertyTable.SourceEntry src : info.getSources()) {
                if (src.getValueType() != null) {
                    valueTypes.add(src.getValueType());
                }
                // Predefined values (enum/boolean) from each source
                if (src.getValues() != null && !src.getValues().isEmpty()) {
                    String srcTail = src.getLibrary() != null
                            ? "  " + src.getAppliesTo() + " (" + src.getLibrary() + ")"
                            : "  " + src.getAppliesTo();
                    for (String value : src.getValues()) {
                        if (addedValues.add(value)) {
                            LookupElementBuilder builder = LookupElementBuilder.create(value)
                                    .withIcon(AllIcons.Nodes.Property)
                                    .withTailText(srcTail, true)
                                    .withInsertHandler(valueInsertHandler);
                            result.addElement(PrioritizedLookupElement.withPriority(builder, 200));
                        }
                    }
                }
            }
        }

        Project project = parameters.getOriginalFile().getProject();

        // Infer from project index if no info
        if (valueTypes.isEmpty() && propName != null) {
            String inferred = inferValueType(propName, project);
            if (inferred != null) {
                valueTypes.add(inferred);
            }
        }

        // Function templates and predefined values by valueType
        Set<String> addedTemplates = new HashSet<>();
        for (String vt : valueTypes) {
            if (addedTemplates.add(vt)) {
                switch (vt) {
                    case "effect" -> addEffectTemplates(result, valueInsertHandler);
                    case "paint", "color" -> addGradientTemplates(result, valueInsertHandler);
                    case "boolean" -> addBooleanValues(result, valueInsertHandler);
                    case "duration" -> addDurationValues(result, valueInsertHandler);
                    case "icon-code" -> addIconCodeCompletions(project, result, ICON_CODE_INSERT_HANDLER);
                    default -> { }
                }
            }
        }

        // Property-specific completions
        if (propName != null) {
            switch (propName) {
                case "transition-property" ->
                        addTransitionPropertyValues(project, result, valueInsertHandler);
                case "transition-timing-function" ->
                        addCubicBezierTemplate(result, valueInsertHandler);
            }
        }

        // Variable completion based on valueTypes
        if (valueTypes.isEmpty()) {
            String currentPrefix = result.getPrefixMatcher().getPrefix();
            if (currentPrefix.startsWith("-")) {
                addFilteredVariables(propName, parameters, result, null, valueInsertHandler);
            }
        } else {
            Set<VariableCategory> addedCategories = new HashSet<>();
            for (String vt : valueTypes) {
                VariableCategory cat = switch (vt) {
                    case "paint", "color" -> VariableCategory.COLOR;
                    case "shape" -> VariableCategory.SHAPE;
                    case "effect" -> VariableCategory.EFFECT;
                    default -> null;
                };
                if (cat != null && addedCategories.add(cat)) {
                    addFilteredVariables(propName, parameters, result, cat, valueInsertHandler);
                }
            }
        }
    }

    // ---- Function templates ----

    private static final String DROP_SHADOW_TEMPLATE = "dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 5)";
    private static final String INNER_SHADOW_TEMPLATE = "innershadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0)";
    private static final String LINEAR_GRADIENT_TEMPLATE = "linear-gradient(to bottom, #ffffff 0%, #000000 100%)";
    private static final String RADIAL_GRADIENT_TEMPLATE = "radial-gradient(center 50% 50%, radius 50%, #ffffff 0%, #000000 100%)";

    private static void addEffectTemplates(@NotNull CompletionResultSet result,
                                            @NotNull InsertHandler<LookupElement> insertHandler) {
        LookupElementBuilder drop = LookupElementBuilder.create(DROP_SHADOW_TEMPLATE)
                .withPresentableText("dropshadow(...)")
                .withIcon(AllIcons.Nodes.Property)
                .withTailText("  DropShadow effect", true)
                .withInsertHandler(insertHandler);
        result.addElement(PrioritizedLookupElement.withPriority(drop, 300));

        LookupElementBuilder inner = LookupElementBuilder.create(INNER_SHADOW_TEMPLATE)
                .withPresentableText("innershadow(...)")
                .withIcon(AllIcons.Nodes.Property)
                .withTailText("  InnerShadow effect", true)
                .withInsertHandler(insertHandler);
        result.addElement(PrioritizedLookupElement.withPriority(inner, 299));
    }

    private static void addGradientTemplates(@NotNull CompletionResultSet result,
                                              @NotNull InsertHandler<LookupElement> insertHandler) {
        LookupElementBuilder linear = LookupElementBuilder.create(LINEAR_GRADIENT_TEMPLATE)
                .withPresentableText("linear-gradient(...)")
                .withIcon(AllIcons.Nodes.Property)
                .withTailText("  Linear gradient", true)
                .withInsertHandler(insertHandler);
        result.addElement(PrioritizedLookupElement.withPriority(linear, 300));

        LookupElementBuilder radial = LookupElementBuilder.create(RADIAL_GRADIENT_TEMPLATE)
                .withPresentableText("radial-gradient(...)")
                .withIcon(AllIcons.Nodes.Property)
                .withTailText("  Radial gradient", true)
                .withInsertHandler(insertHandler);
        result.addElement(PrioritizedLookupElement.withPriority(radial, 299));
    }

    private static void addBooleanValues(@NotNull CompletionResultSet result,
                                            @NotNull InsertHandler<LookupElement> insertHandler) {
        for (String val : new String[]{"true", "false"}) {
            LookupElementBuilder builder = LookupElementBuilder.create(val)
                    .withIcon(AllIcons.Nodes.Property)
                    .withInsertHandler(insertHandler);
            result.addElement(PrioritizedLookupElement.withPriority(builder, 200));
        }
    }

    private static final String[] DURATION_VALUES = {
            "0s", "0.1s", "0.2s", "0.3s", "0.5s", "1s", "200ms", "500ms"
    };

    private static void addDurationValues(@NotNull CompletionResultSet result,
                                           @NotNull InsertHandler<LookupElement> insertHandler) {
        for (String val : DURATION_VALUES) {
            LookupElementBuilder builder = LookupElementBuilder.create(val)
                    .withIcon(AllIcons.Nodes.Property)
                    .withInsertHandler(insertHandler);
            result.addElement(PrioritizedLookupElement.withPriority(builder, 200));
        }
    }

    private static void addTransitionPropertyValues(@NotNull Project project,
                                                     @NotNull CompletionResultSet result,
                                                     @NotNull InsertHandler<LookupElement> insertHandler) {
        for (FxCssPropertyTable.PropertyInfo info : FxCssPropertyTable.getAllProperties(project)) {
            String name = info.getName();
            if (name.startsWith("-fx-")) {
                LookupElementBuilder builder = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.FileTypes.Css)
                        .withTailText("  " + info.getAppliesTo(), true)
                        .withInsertHandler(insertHandler);
                result.addElement(PrioritizedLookupElement.withPriority(builder, 100));
            }
        }
    }

    private static final String CUBIC_BEZIER_TEMPLATE = "cubic-bezier(0.25, 0.1, 0.25, 1.0)";

    private static void addCubicBezierTemplate(@NotNull CompletionResultSet result,
                                                @NotNull InsertHandler<LookupElement> insertHandler) {
        LookupElementBuilder builder = LookupElementBuilder.create(CUBIC_BEZIER_TEMPLATE)
                .withPresentableText("cubic-bezier(...)")
                .withIcon(AllIcons.Nodes.Property)
                .withTailText("  Custom easing curve", true)
                .withInsertHandler(insertHandler);
        result.addElement(PrioritizedLookupElement.withPriority(builder, 150));
    }

    // ---- Value type inference for custom properties ----

    private static final int MAX_INFER_DEPTH = 5;

    /**
     * Infers the value type of a custom CSS property by examining its existing values in the project.
     *
     * @return "color", "paint", "effect", "shape", "boolean", or null if unknown
     */
    @Nullable
    static String inferValueType(@NotNull String propertyName, @NotNull Project project) {
        Map<String, List<String>> allVars = FxCssPropertyIndex.getAllVariables(project);
        return inferValueTypeRecursive(propertyName, allVars, new HashSet<>(), 0);
    }

    @Nullable
    private static String inferValueTypeRecursive(@NotNull String propertyName,
                                                    @NotNull Map<String, List<String>> allVars,
                                                    @NotNull Set<String> visited,
                                                    int depth) {
        if (depth > MAX_INFER_DEPTH || !visited.add(propertyName)) {
            return null;
        }

        List<String> values = allVars.getOrDefault(propertyName, Collections.emptyList());

        for (String raw : values) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }

            // Boolean
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return "boolean";
            }
            // Most common first: direct color
            if (FxColorParser.isDirectColor(value)) {
                return "color";
            }
            // Gradient
            if (FxColorParser.isGradient(value)) {
                return "paint";
            }
            // Derive
            if (FxColorParser.isDerive(value)) {
                return "color";
            }
            // Effect
            if (FxEffectParser.isEffect(value)) {
                return "effect";
            }
            // SVG path
            java.util.regex.Matcher m = QUOTED_STRING.matcher(value);
            if (m.matches() && FxSvgRenderer.isSvgPath(m.group(1).trim())) {
                return "shape";
            }
            if (FxSvgRenderer.isSvgPath(value)) {
                return "shape";
            }
            // Variable reference → resolve recursively
            if (FxColorParser.isVariableReference(value)) {
                String resolved = inferValueTypeRecursive(value, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    // ---- Variable completion with type filtering ----

    enum VariableCategory { COLOR, SHAPE, EFFECT }

    private static void addFilteredVariables(@Nullable String propName,
                                              @NotNull CompletionParameters parameters,
                                              @NotNull CompletionResultSet result,
                                              @Nullable VariableCategory category,
                                              @NotNull InsertHandler<LookupElement> insertHandler) {
        Project project = parameters.getOriginalFile().getProject();
        Map<String, List<String>> allVars = FxCssPropertyIndex.getAllVariables(project);

        for (Map.Entry<String, List<String>> entry : allVars.entrySet()) {
            String varName = entry.getKey();
            if (varName.startsWith("-fx-")) {
                continue;
            }
            // Skip self-reference to avoid circular definitions
            if (varName.equals(propName)) {
                continue;
            }
            String firstValue = resolveFirstValue(entry.getValue());
            if (firstValue == null) {
                continue;
            }
            if (category != null && !matchesCategory(firstValue, category, varName, allVars)) {
                continue;
            }
            Icon icon = createVariableIcon(firstValue, category, varName, allVars);
            LookupElementBuilder builder = LookupElementBuilder.create(varName)
                    .withIcon(icon != null ? icon : AllIcons.Nodes.Variable)
                    .withInsertHandler(insertHandler);
            // SVG path strings are unreadable — the rendered icon is enough
            if (category != VariableCategory.SHAPE) {
                builder = builder.withTypeText(firstValue, true);
            }
            result.addElement(builder);
        }
    }

    // ---- Helpers ----

    @Nullable
    public static String extractPropertyName(@NotNull String segment) {
        String trimmed = segment.trim();
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        return trimmed.substring(0, colonIdx).trim();
    }

    public static boolean isCssTokenChar(char c) {
        return c == '-' || c == '_' || Character.isLetterOrDigit(c);
    }

    /**
     * Checks whether the given offset is inside a CSS declaration block ({...}).
     * Scans backwards from offset, counting unmatched braces while skipping
     * quoted strings and comments.
     */
    public static boolean isInsideDeclarationBlock(@NotNull String text, int offset) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < offset; i++) {
            char c = text.charAt(i);
            // ==================== Comment handling ====================
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < offset && text.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            // ==================== Quote handling ====================
            if (inSingleQuote) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            // ==================== State transitions ====================
            if (c == '/' && i + 1 < offset) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (c == '\'') {
                inSingleQuote = true;
            } else if (c == '"') {
                inDoubleQuote = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth > 0;
    }

    @Nullable
    private static String resolveFirstValue(@NotNull List<String> values) {
        for (String raw : values) {
            String trimmed = raw.contains("\n")
                    ? raw.substring(0, raw.indexOf('\n')).trim()
                    : raw.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static boolean matchesCategory(@NotNull String value,
                                             @NotNull VariableCategory category,
                                             @NotNull String varName,
                                             @NotNull Map<String, List<String>> allVars) {
        if (matchesCategoryDirect(value, category)) {
            return true;
        }
        // If value is a variable reference, infer the resolved type via recursive chain
        if (FxColorParser.isVariableReference(value)) {
            String resolved = inferValueTypeRecursive(varName, allVars, new HashSet<>(), 0);
            if (resolved != null) {
                return switch (category) {
                    case COLOR -> "color".equals(resolved) || "paint".equals(resolved);
                    case SHAPE -> "shape".equals(resolved);
                    case EFFECT -> "effect".equals(resolved);
                };
            }
        }
        return false;
    }

    private static boolean matchesCategoryDirect(@NotNull String value,
                                                   @NotNull VariableCategory category) {
        return switch (category) {
            case COLOR -> isColorValue(value);
            case SHAPE -> isSvgPathValue(value);
            case EFFECT -> isEffectValue(value);
        };
    }

    private static boolean isColorValue(@NotNull String value) {
        if (FxColorParser.isDirectColor(value) || FxColorParser.isGradient(value)) {
            return true;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("derive(") || lower.startsWith("ladder(");
    }

    private static boolean isSvgPathValue(@NotNull String value) {
        java.util.regex.Matcher m = QUOTED_STRING.matcher(value);
        if (m.matches()) {
            return FxSvgRenderer.isSvgPath(m.group(1).trim());
        }
        return FxSvgRenderer.isSvgPath(value);
    }

    private static boolean isEffectValue(@NotNull String value) {
        return EFFECT_PATTERN.matcher(value).find();
    }

    @Nullable
    private static Icon createVariableIcon(@NotNull String value,
                                            @Nullable VariableCategory category,
                                            @NotNull String varName,
                                            @NotNull Map<String, List<String>> allVars) {
        if (category == VariableCategory.COLOR || category == null) {
            // Try solid color first
            Color color = FxColorParser.parseColor(value);
            if (color == null && FxColorParser.isVariableReference(value)) {
                color = resolveColorFromVarMap(varName, allVars, new HashSet<>(), 0);
            }
            if (color != null) {
                return createColorIcon(color);
            }
            // Try gradient (direct or via variable chain)
            Paint gradient = parseGradientPaint(value);
            if (gradient == null && FxColorParser.isVariableReference(value)) {
                gradient = resolveGradientFromVarMap(varName, allVars, new HashSet<>(), 0);
            }
            if (gradient != null) {
                return createGradientIcon(gradient);
            }
        }
        if (category == VariableCategory.SHAPE || category == null) {
            String pathData = extractSvgPathData(value);
            if (pathData == null && FxColorParser.isVariableReference(value)) {
                pathData = resolveSvgPathFromVarMap(varName, allVars, new HashSet<>(), 0);
            }
            if (pathData != null) {
                return createSvgIcon(pathData);
            }
        }
        if (category == VariableCategory.EFFECT || category == null) {
            String effectExpr = value;
            if (!FxEffectParser.isEffect(effectExpr) && FxColorParser.isVariableReference(value)) {
                effectExpr = resolveEffectFromVarMap(varName, allVars, new HashSet<>(), 0);
            }
            if (effectExpr != null && FxEffectParser.isEffect(effectExpr)) {
                EffectConfig config = FxEffectParser.parseEffect(effectExpr, null);
                if (config != null) {
                    return CssPreviewIconRenderer.createEffectIcon(config);
                }
            }
        }
        return null;
    }

    /**
     * Resolves a variable chain to a Color using the in-memory variable map (no I/O).
     */
    @Nullable
    private static Color resolveColorFromVarMap(@NotNull String varName,
                                                 @NotNull Map<String, List<String>> allVars,
                                                 @NotNull Set<String> visited,
                                                 int depth) {
        if (depth > MAX_INFER_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String raw : values) {
            String v = raw.trim();
            if (v.isEmpty()) {
                continue;
            }
            Color color = FxColorParser.parseColor(v);
            if (color != null) {
                return color;
            }
            if (FxColorParser.isVariableReference(v)) {
                Color resolved = resolveColorFromVarMap(v, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a variable chain to a gradient Paint using the in-memory variable map (no I/O).
     */
    @Nullable
    private static Paint resolveGradientFromVarMap(@NotNull String varName,
                                                    @NotNull Map<String, List<String>> allVars,
                                                    @NotNull Set<String> visited,
                                                    int depth) {
        if (depth > MAX_INFER_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String raw : values) {
            String v = raw.trim();
            if (v.isEmpty()) {
                continue;
            }
            Paint gradient = parseGradientPaint(v);
            if (gradient != null) {
                return gradient;
            }
            if (FxColorParser.isVariableReference(v)) {
                Paint resolved = resolveGradientFromVarMap(v, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a variable chain to SVG path data using the in-memory variable map (no I/O).
     */
    @Nullable
    private static String resolveSvgPathFromVarMap(@NotNull String varName,
                                                    @NotNull Map<String, List<String>> allVars,
                                                    @NotNull Set<String> visited,
                                                    int depth) {
        if (depth > MAX_INFER_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String raw : values) {
            String v = raw.trim();
            if (v.isEmpty()) {
                continue;
            }
            String pathData = extractSvgPathData(v);
            if (pathData != null) {
                return pathData;
            }
            if (FxColorParser.isVariableReference(v)) {
                String resolved = resolveSvgPathFromVarMap(v, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a variable chain to an effect expression using the in-memory variable map (no I/O).
     */
    @Nullable
    private static String resolveEffectFromVarMap(@NotNull String varName,
                                                   @NotNull Map<String, List<String>> allVars,
                                                   @NotNull Set<String> visited,
                                                   int depth) {
        if (depth > MAX_INFER_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String raw : values) {
            String v = raw.trim();
            if (v.isEmpty()) {
                continue;
            }
            if (FxEffectParser.isEffect(v)) {
                return v;
            }
            if (FxColorParser.isVariableReference(v)) {
                String resolved = resolveEffectFromVarMap(v, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static final int ICON_SIZE = CssPreviewIconRenderer.ICON_SIZE;

    @NotNull
    static Icon createColorIcon(@NotNull Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setColor(color);
                g2.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
                g2.setColor(Color.GRAY);
                g2.drawRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return ICON_SIZE;
            }

            @Override
            public int getIconHeight() {
                return ICON_SIZE;
            }
        };
    }

    /**
     * Parses a gradient CSS value to an AWT Paint (LinearGradientPaint or RadialGradientPaint).
     * Passes null project/scope since completion icons don't need variable resolution in stops.
     */
    @Nullable
    private static Paint parseGradientPaint(@NotNull String value) {
        if (!FxGradientParser.isGradient(value)) {
            return null;
        }
        try {
            FxGradientParser.LinearGradientInfo linear =
                    FxGradientParser.parseLinearGradient(value, null, null);
            if (linear != null) {
                return linear.toAwtPaint(ICON_SIZE, ICON_SIZE);
            }
            FxGradientParser.RadialGradientInfo radial =
                    FxGradientParser.parseRadialGradient(value, null, null);
            if (radial != null) {
                return radial.toAwtPaint(ICON_SIZE, ICON_SIZE);
            }
        } catch (Exception ignored) {
            // Malformed gradient — fall through to null
        }
        return null;
    }

    @NotNull
    static Icon createGradientIcon(@NotNull Paint paint) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setPaint(paint);
                g2.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
                g2.setColor(Color.GRAY);
                g2.drawRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return ICON_SIZE;
            }

            @Override
            public int getIconHeight() {
                return ICON_SIZE;
            }
        };
    }

    /**
     * Extracts SVG path data from a value, stripping quotes if present.
     */
    @Nullable
    private static String extractSvgPathData(@NotNull String value) {
        String data = value;
        java.util.regex.Matcher m = QUOTED_STRING.matcher(value);
        if (m.matches()) {
            data = m.group(1).trim();
        }
        return FxSvgRenderer.isSvgPath(data) ? data : null;
    }

    @Nullable
    public static Icon createSvgIcon(@NotNull String pathData) {
        GeneralPath path = FxSvgRenderer.parseSvgPath(pathData);
        if (path == null) {
            return null;
        }
        Rectangle2D bounds = path.getBounds2D();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return null;
        }

        double scale = Math.min((ICON_SIZE - 2) / bounds.getWidth(),
                (ICON_SIZE - 2) / bounds.getHeight());
        double tx = (ICON_SIZE - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
        double ty = (ICON_SIZE - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;
        AffineTransform transform = new AffineTransform();
        transform.translate(tx, ty);
        transform.scale(scale, scale);
        Shape transformed = transform.createTransformedShape(path);

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(com.intellij.ui.JBColor.foreground());
                g2.fill(transformed);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return ICON_SIZE;
            }

            @Override
            public int getIconHeight() {
                return ICON_SIZE;
            }
        };
    }

    // ---- Icon code completion (Ikonli) ----

    /**
     * Insert handler for icon code values: skips past closing quote before adding ";".
     * Handles both .css ({@code "literal";}) and inline ({@code "...-fx-icon-code: \"literal\"; ..."}) contexts.
     */
    private static final InsertHandler<LookupElement> ICON_CODE_INSERT_HANDLER =
            (ctx, item) -> {
                Editor ed = ctx.getEditor();
                Document doc = ed.getDocument();
                int start = ctx.getStartOffset();
                int tail = ctx.getTailOffset();
                CharSequence chars = doc.getCharsSequence();

                // Check if already inside quotes (user typed " before triggering completion)
                boolean hasOpenQuote = start > 0
                        && (chars.charAt(start - 1) == '"' || chars.charAt(start - 1) == '\'');

                if (hasOpenQuote) {
                    // Already inside quotes — skip past the closing quote
                    if (tail < doc.getTextLength()
                            && (chars.charAt(tail) == '"' || chars.charAt(tail) == '\'')) {
                        tail++;
                    }
                } else {
                    // Need to wrap with quotes — determine the right quote style
                    String quote = resolveIconCodeQuote(ctx.getFile(), start);
                    doc.insertString(tail, quote);
                    doc.insertString(start, quote);
                    tail += quote.length() * 2; // past both inserted quotes
                }

                // Re-read after potential insertions
                chars = doc.getCharsSequence();
                int docLen = doc.getTextLength();

                // Insert ";" if not already there
                if (tail >= docLen || chars.charAt(tail) != ';') {
                    doc.insertString(tail, ";");
                }
                ed.getCaretModel().moveToOffset(tail + 1);
            };

    /**
     * Determines the correct quote string to wrap an icon literal, based on file context.
     * <ul>
     *   <li>CSS file: {@code "}</li>
     *   <li>Java regular string: {@code \"} (escaped)</li>
     *   <li>Java text block: {@code "}</li>
     *   <li>FXML (XML attribute): {@code '} (single quote to avoid conflict with attribute delimiters)</li>
     * </ul>
     */
    @NotNull
    private static String resolveIconCodeQuote(@NotNull PsiFile file, int offset) {
        String ext = file.getVirtualFile() != null ? file.getVirtualFile().getExtension() : "";
        if ("java".equals(ext)) {
            // Check if we're inside a text block or regular string
            PsiElement element = file.findElementAt(offset);
            while (element != null) {
                if (element instanceof PsiLiteralExpression literal) {
                    return literal.isTextBlock() ? "\"" : "\\\"";
                }
                element = element.getParent();
            }
            return "\\\"";
        }
        if ("xml".equalsIgnoreCase(ext) || "fxml".equalsIgnoreCase(ext)) {
            return "'";
        }
        return "\"";
    }

    /**
     * Adds Ikonli icon literal completions to the result set.
     * Only shows icons from packs whose enumClass is on the project classpath.
     *
     * @param insertHandler inserts the selected literal in a context-appropriate way
     *                      (e.g. {@link #ICON_CODE_INSERT_HANDLER} for CSS, or a plain
     *                      no-op handler for FXML attribute values where IntelliJ already
     *                      handles quoting)
     */
    public static void addIconCodeCompletions(@NotNull Project project,
                                              @NotNull CompletionResultSet result,
                                              @NotNull InsertHandler<LookupElement> insertHandler) {
        IconDataService service = IconDataService.getInstance();
        if (!service.isLoaded()) {
            return;
        }
        Set<String> availablePacks = IconDataService.getAvailablePacks(project);
        if (availablePacks.isEmpty()) {
            return;
        }

        // Ensure path data is loaded for available packs (for icon preview)
        for (IconDataService.PackInfo pack : service.getAllPacks()) {
            if (availablePacks.contains(pack.getId()) && !service.isPackLoaded(pack.getId())) {
                service.ensurePackLoaded(pack);
            }
        }

        Icon placeholderIcon = IconPlaceholder.createIcon(ICON_SIZE);
        for (IconDataService.IconEntry icon : service.getAllIcons()) {
            if (!availablePacks.contains(icon.getPackId())) {
                continue;
            }
            String literal = icon.getLiteral();
            String packName = icon.getPack().getName();
            LookupElementBuilder builder = LookupElementBuilder.create(literal)
                    .withTypeText(packName, true)
                    .withCaseSensitivity(false)
                    .withInsertHandler(insertHandler);

            // Resolve preview icon: SVG for renderable, placeholder for np:true
            Icon previewIcon = null;
            if (icon.isRenderable()) {
                String pathData = service.getPath(icon);
                if (pathData != null) {
                    previewIcon = createSvgIcon(pathData);
                }
            } else {
                previewIcon = placeholderIcon;
            }
            if (previewIcon != null) {
                builder = builder.withIcon(previewIcon);
            }

            result.addElement(PrioritizedLookupElement.withPriority(builder, 30));
        }
    }

    // ---- Insert handlers for .css files ----

    /** For .css files: inserts ": " after property name + triggers value popup. */
    public static final InsertHandler<LookupElement> CSS_PROPERTY_INSERT_HANDLER =
            (ctx, item) -> {
                Editor ed = ctx.getEditor();
                Project proj = ctx.getProject();
                int tail = ctx.getTailOffset();
                Document doc = ed.getDocument();
                String after = tail < doc.getTextLength()
                        ? doc.getText(TextRange.create(tail, Math.min(tail + 2, doc.getTextLength())))
                        : "";
                if (!after.startsWith(":")) {
                    doc.insertString(tail, ": ");
                    ed.getCaretModel().moveToOffset(tail + 2);
                }
                AutoPopupController.getInstance(proj).scheduleAutoPopup(ed);
            };

    /** For .css files: ensures space after colon, inserts ";" after value. */
    public static final InsertHandler<LookupElement> CSS_VALUE_INSERT_HANDLER =
            (ctx, item) -> {
                Editor ed = ctx.getEditor();
                Document doc = ed.getDocument();
                int start = ctx.getStartOffset();
                // Ensure space after colon
                if (start > 0 && doc.getCharsSequence().charAt(start - 1) == ':') {
                    doc.insertString(start, " ");
                    start++;
                }
                int tail = ctx.getTailOffset();
                String after = tail < doc.getTextLength()
                        ? doc.getText(TextRange.create(tail, Math.min(tail + 1, doc.getTextLength())))
                        : "";
                if (!after.startsWith(";")) {
                    doc.insertString(tail, ";");
                }
                ed.getCaretModel().moveToOffset(tail + 1);
            };

    // ---- Insert handlers for inline (Java/FXML) ----

    /** For inline: inserts ": " after property name + triggers value popup. */
    public static final InsertHandler<LookupElement> INLINE_PROPERTY_INSERT_HANDLER =
            (ctx, item) -> {
                Editor ed = ctx.getEditor();
                Project proj = ctx.getProject();
                int tail = ctx.getTailOffset();
                Document doc = ed.getDocument();
                String after = tail < doc.getTextLength()
                        ? doc.getText(TextRange.create(tail, Math.min(tail + 2, doc.getTextLength())))
                        : "";
                if (!after.startsWith(":")) {
                    doc.insertString(tail, ": ");
                    ed.getCaretModel().moveToOffset(tail + 2);
                }
                AutoPopupController.getInstance(proj).scheduleAutoPopup(ed);
            };

    /** For inline: ensures space after colon, inserts "; " after value. */
    public static final InsertHandler<LookupElement> INLINE_VALUE_INSERT_HANDLER =
            (ctx, item) -> {
                Editor ed = ctx.getEditor();
                Document doc = ed.getDocument();
                int start = ctx.getStartOffset();
                // Ensure space after colon
                if (start > 0 && doc.getCharsSequence().charAt(start - 1) == ':') {
                    doc.insertString(start, " ");
                    start++;
                }
                int tail = ctx.getTailOffset();
                String after = tail < doc.getTextLength()
                        ? doc.getText(TextRange.create(tail, Math.min(tail + 1, doc.getTextLength())))
                        : "";
                if (!after.startsWith(";")) {
                    doc.insertString(tail, "; ");
                    ed.getCaretModel().moveToOffset(tail + 2);
                } else {
                    ed.getCaretModel().moveToOffset(tail + 1);
                }
            };
}
