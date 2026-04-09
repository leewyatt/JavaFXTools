package io.github.leewyatt.fxtools.css.preview.effect;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides gutter preview icons for dropshadow() and innershadow() in .css files.
 * Supports both direct effect expressions and CSS variable references.
 */
public class FxCssEffectPreviewProvider implements LineMarkerProvider {

    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("([\\w-]+)\\s*:\\s*([^;{}]+?)\\s*;");

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                        @NotNull Collection<? super LineMarkerInfo<?>> result) {
        if (elements.isEmpty() || !FxToolsSettingsState.getInstance().enableGutterPreviews) {
            return;
        }
        PsiFile file = elements.get(0).getContainingFile();
        if (file == null) {
            return;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return;
        }

        String text = file.getText();
        Project project = file.getProject();
        if (!FxDetector.isJavaFxProject(project)) {
            return;
        }
        Map<String, List<String>> allVars = FxCssPropertyIndex.getAllVariables(project);
        // Build element set to avoid duplicates when called from multiple highlighting passes
        Set<PsiElement> elementSet = new HashSet<>(elements);
        // Strip comments to prevent "key: value" inside comments from consuming real properties
        String matchText = CssPreviewIconRenderer.stripCommentsPreservingOffsets(text);

        Matcher matcher = PROPERTY_PATTERN.matcher(matchText);
        while (matcher.find()) {
            ProgressManager.checkCanceled();
            String propName = matcher.group(1).trim();
            String value = matcher.group(2).trim();

            // Handle both -fx-effect usage and custom variable definitions
            String effectExpr = resolveEffectExpression(value, propName, allVars);
            if (effectExpr == null) {
                continue;
            }

            EffectConfig config = FxEffectParser.parseEffect(effectExpr, project);
            if (config == null) {
                continue;
            }

            Icon icon = CssPreviewIconRenderer.createEffectIcon(config);
            if (icon == null) {
                continue;
            }

            int matchStart = matcher.start();
            PsiElement anchor = file.findElementAt(matchStart);
            if (anchor != null && elementSet.contains(anchor)) {
                String tooltip = propName + ": " + summarize(value);
                TextRange range = TextRange.create(matchStart, matchStart + 1);
                int vStart = matcher.start(2);
                int vEnd = matcher.end(2);
                result.add(new LineMarkerInfo<>(anchor, range, icon,
                        e -> tooltip,
                        (e, elt) -> CssGutterEffectHandler.openEditor(
                                e, file, vStart, vEnd, value),
                        CssPreviewIconRenderer.GUTTER_ALIGNMENT, () -> tooltip));
            }
        }
    }

    /**
     * Resolves an effect expression from a CSS property value.
     * Handles direct effect values, variable references, and custom variable definitions.
     *
     * @return the effect expression string, or null if not an effect
     */
    private static final int MAX_EFFECT_RESOLVE_DEPTH = 5;

    @Nullable
    private static String resolveEffectExpression(@NotNull String value,
                                                   @NotNull String propName,
                                                   @NotNull Map<String, List<String>> allVars) {
        if (FxEffectParser.isEffect(value)) {
            return value;
        }
        if (FxColorParser.isVariableReference(value)) {
            return resolveEffectVariable(value, allVars, new HashSet<>(), 0);
        }
        return null;
    }

    @Nullable
    private static String resolveEffectVariable(@NotNull String varName,
                                                 @NotNull Map<String, List<String>> allVars,
                                                 @NotNull Set<String> visited,
                                                 int depth) {
        if (depth >= MAX_EFFECT_RESOLVE_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String rawValue : values) {
            String trimmed = rawValue.trim();
            if (FxEffectParser.isEffect(trimmed)) {
                return trimmed;
            }
            if (FxColorParser.isVariableReference(trimmed)) {
                String resolved = resolveEffectVariable(trimmed, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    @NotNull
    private static String summarize(@NotNull String expr) {
        if (expr.length() > 60) {
            return expr.substring(0, 57) + "...";
        }
        return expr;
    }
}
