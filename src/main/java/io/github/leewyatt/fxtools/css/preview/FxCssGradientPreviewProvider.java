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
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import io.github.leewyatt.fxtools.util.FxDetector;
import io.github.leewyatt.fxtools.util.FxGradientParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides Gutter gradient preview icons for linear-gradient/radial-gradient in .css files.
 */
public class FxCssGradientPreviewProvider implements LineMarkerProvider {

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
            String propertyName = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            int valueDocStart = matcher.start(2);
            int matchStart = matcher.start();

            // For multi-paint properties scan each segment (comma for background,
            // comma+space for border-color); otherwise treat the whole value as one.
            List<CssPaintListScanner.Segment> segments;
            if (CssPaintListScanner.isPaintListProperty(propertyName)) {
                segments = CssPaintListScanner.scanForProperty(propertyName, value);
            } else {
                segments = List.of(new CssPaintListScanner.Segment(value, 0, value.length()));
            }

            PsiElement anchor = file.findElementAt(matchStart);
            if (anchor == null || !elementSet.contains(anchor)) {
                continue;
            }

            for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
                CssPaintListScanner.Segment seg = segments.get(segIdx);
                String segText = seg.getText();

                String gradientExpr = resolveGradientExpression(segText, allVars);
                if (gradientExpr == null) {
                    continue;
                }
                Paint paint = parseGradientPaint(gradientExpr, allVars);
                if (paint == null) {
                    continue;
                }

                Icon icon = CssPreviewIconRenderer.createGradientIcon(paint);
                String tooltip = FxToolsBundle.message("css.preview.tooltip.gradient",
                        summarizeGradient(gradientExpr));
                // 1-char marker range offset by segment index so each marker is distinct;
                // always inside the property-name token of the matched declaration.
                TextRange range = TextRange.create(matchStart + segIdx, matchStart + segIdx + 1);
                int segDocStart = valueDocStart + seg.getStartInValue();
                int segDocEnd = valueDocStart + seg.getEndInValue();
                String segVText = segText;
                result.add(new LineMarkerInfo<>(anchor, range, icon,
                        e -> tooltip,
                        (e, elt) -> CssGutterColorHandler.openEditor(
                                e, file, segDocStart, segDocEnd, segVText),
                        CssPreviewIconRenderer.GUTTER_ALIGNMENT, () -> tooltip));
            }
        }
    }

    private static final int MAX_GRADIENT_RESOLVE_DEPTH = 10;

    /**
     * Resolves the gradient expression from a CSS property value.
     * Handles both direct gradient values and variable references (with recursive chain resolution).
     *
     * @return the gradient expression string, or null if not a gradient
     */
    @Nullable
    private static String resolveGradientExpression(@NotNull String value,
                                                     @NotNull Map<String, List<String>> allVars) {
        if (FxColorParser.isGradient(value)) {
            return value;
        }

        if (FxColorParser.isVariableReference(value)) {
            return resolveGradientVariable(value, allVars, new HashSet<>(), 0);
        }

        return null;
    }

    @Nullable
    private static String resolveGradientVariable(@NotNull String varName,
                                                    @NotNull Map<String, List<String>> allVars,
                                                    @NotNull Set<String> visited,
                                                    int depth) {
        if (depth >= MAX_GRADIENT_RESOLVE_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = allVars.getOrDefault(varName, Collections.emptyList());
        for (String rawValue : values) {
            String trimmed = rawValue.trim();
            if (FxColorParser.isGradient(trimmed)) {
                return trimmed;
            }
            if (FxColorParser.isVariableReference(trimmed)) {
                String resolved = resolveGradientVariable(trimmed, allVars, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    /**
     * Parses a gradient expression string to an AWT Paint,
     * resolving color variable references from the in-memory map.
     */
    @Nullable
    private static Paint parseGradientPaint(@NotNull String gradientExpr,
                                            @Nullable Map<String, List<String>> allVars) {
        String lower = gradientExpr.trim().toLowerCase();
        if (lower.startsWith("linear-gradient")) {
            FxGradientParser.LinearGradientInfo info =
                    FxGradientParser.parseLinearGradient(gradientExpr, allVars);
            if (info != null && info.getStops().size() >= 2) {
                return info.toAwtPaint(16, 16);
            }
        } else if (lower.startsWith("radial-gradient")) {
            FxGradientParser.RadialGradientInfo info =
                    FxGradientParser.parseRadialGradient(gradientExpr, allVars);
            if (info != null && info.getStops().size() >= 2) {
                return info.toAwtPaint(16, 16);
            }
        }
        return null;
    }

    @NotNull
    private static String summarizeGradient(@NotNull String expr) {
        if (expr.length() > 60) {
            return expr.substring(0, 57) + "...";
        }
        return expr;
    }
}
