package io.github.leewyatt.fxtools.css.preview;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import io.github.leewyatt.fxtools.util.FxDetector;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides Gutter SVG path preview icons for -fx-shape properties and SVG path variables in .css files.
 */
public class FxCssSvgPreviewProvider implements LineMarkerProvider {

    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("([\\w-]+)\\s*:\\s*([^;{}]+?)\\s*;");
    private static final Pattern QUOTED_STRING_PATTERN =
            Pattern.compile("^[\"']([^\"']+)[\"']$");
    private static final Color SVG_COLOR =
            new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY);

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
            String propName = matcher.group(1);
            String value = matcher.group(2).trim();
            boolean isFxShape = "-fx-shape".equals(propName);

            String pathData = resolvePathData(value, isFxShape, allVars);
            if (pathData == null) {
                continue;
            }

            int matchStart = matcher.start();
            PsiElement anchor = file.findElementAt(matchStart);
            if (anchor == null || !elementSet.contains(anchor)) {
                continue;
            }

            Icon icon = CssPreviewIconRenderer.createSvgIcon(pathData, SVG_COLOR);
            if (icon != null) {
                String tooltip = formatTooltip(pathData);
                TextRange range = TextRange.create(matchStart, matchStart + 1);
                String pd = pathData;
                int ms = matchStart;
                result.add(new LineMarkerInfo<>(anchor, range, icon,
                        e -> tooltip,
                        (e, elt) -> CssGutterSvgHandler.openPreview(e, file, pd, ms),
                        CssPreviewIconRenderer.GUTTER_ALIGNMENT, () -> tooltip));
            } else if (isFxShape) {
                Icon errorIcon = CssPreviewIconRenderer.createErrorIcon();
                String tooltip = FxToolsBundle.message("css.preview.tooltip.shape.invalid", value);
                TextRange range = TextRange.create(matchStart, matchStart + 1);
                result.add(new LineMarkerInfo<>(anchor, range, errorIcon,
                        e -> tooltip, null, CssPreviewIconRenderer.GUTTER_ALIGNMENT, () -> tooltip));
            }
        }
    }

    /**
     * Resolves the actual SVG path data from a CSS property value.
     *
     * @return the path data string, or null if not an SVG path
     */
    @Nullable
    private static String resolvePathData(@NotNull String value, boolean isFxShape,
                                           @NotNull Map<String, List<String>> allVars) {
        // Case 1: quoted string value — "M9 11L6..."
        Matcher quotedMatcher = QUOTED_STRING_PATTERN.matcher(value);
        if (quotedMatcher.matches()) {
            String inner = quotedMatcher.group(1).trim();
            if (inner.isEmpty()) {
                return null;
            }
            if (FxSvgRenderer.isSvgPath(inner)) {
                return inner;
            }
            // Quoted but not SVG: return inner only for -fx-shape (to trigger error icon)
            return isFxShape ? inner : null;
        }

        // Case 2: "null" literal — intentional empty
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }

        // Case 3: variable reference — -shape-close
        if (FxColorParser.isVariableReference(value)) {
            return resolveVariablePathData(value, allVars);
        }

        return null;
    }

    private static final int MAX_SVG_RESOLVE_DEPTH = 5;

    /**
     * Resolves a CSS variable reference to SVG path data using the in-memory variable map.
     * Follows variable chains up to MAX_SVG_RESOLVE_DEPTH levels.
     */
    @Nullable
    private static String resolveVariablePathData(@NotNull String variableName,
                                                   @NotNull Map<String, List<String>> allVars) {
        return resolveVariablePathDataRecursive(variableName, allVars, new HashSet<>(), 0);
    }

    @Nullable
    private static String resolveVariablePathDataRecursive(@NotNull String varName,
                                                            @NotNull Map<String, List<String>> allVars,
                                                            @NotNull Set<String> visited,
                                                            int depth) {
        if (depth >= MAX_SVG_RESOLVE_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String rawValue : values) {
            String trimmed = rawValue.trim();
            // Strip quotes if present
            Matcher quotedMatcher = QUOTED_STRING_PATTERN.matcher(trimmed);
            if (quotedMatcher.matches()) {
                String inner = quotedMatcher.group(1).trim();
                if (FxSvgRenderer.isSvgPath(inner)) {
                    return inner;
                }
            } else if (FxSvgRenderer.isSvgPath(trimmed)) {
                return trimmed;
            }
            // Follow variable chain
            if (FxColorParser.isVariableReference(trimmed)) {
                String resolved = resolveVariablePathDataRecursive(trimmed, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    @NotNull
    private static String formatTooltip(@NotNull String pathData) {
        String display = pathData.length() > 40 ? pathData.substring(0, 37) + "..." : pathData;
        return "-fx-shape: \"" + display + "\"";
    }
}
