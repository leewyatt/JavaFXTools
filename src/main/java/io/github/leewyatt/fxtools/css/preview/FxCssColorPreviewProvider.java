package io.github.leewyatt.fxtools.css.preview;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides Gutter color preview icons for CSS color values in .css files.
 * Supports multi-value paint properties (e.g. {@code -fx-background-color: red, #112233;})
 * by emitting one gutter marker per comma-separated segment.
 */
public class FxCssColorPreviewProvider implements LineMarkerProvider {

    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("([\\w-]+)\\s*:\\s*([^;{}]+?)\\s*;");
    private static final boolean ULTIMATE_CSS_AVAILABLE =
            PluginManagerCore.getPlugin(PluginId.getId("com.intellij.css")) != null;

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                        @NotNull Collection<? super LineMarkerInfo<?>> result) {
        if (elements.isEmpty()) {
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

            List<CssPaintListScanner.Segment> segments = selectSegments(propertyName, value);
            if (segments.isEmpty()) {
                continue;
            }

            PsiElement anchor = file.findElementAt(matcher.start());
            if (anchor == null || !elementSet.contains(anchor)) {
                continue;
            }

            for (int segIdx = 0; segIdx < segments.size(); segIdx++) {
                CssPaintListScanner.Segment seg = segments.get(segIdx);
                String segText = seg.getText();

                Icon icon = null;
                String tooltip = null;

                if (FxColorParser.isDerive(segText)) {
                    Color derivedColor = FxColorParser.parseDeriveColor(segText);
                    if (derivedColor == null) {
                        continue;
                    }
                    icon = CssPreviewIconRenderer.createSquareIcon(derivedColor);
                    tooltip = FxToolsBundle.message("css.preview.tooltip.direct", segText);
                } else if (FxColorParser.isGradient(segText)) {
                    // Handled by FxCssGradientPreviewProvider — skip in this pass.
                    continue;
                } else if (FxColorParser.isDirectColor(segText)) {
                    // In Ultimate, IDEA's built-in CSS gutter already previews direct
                    // colors — skip this segment and let the built-in handle it.
                    if (ULTIMATE_CSS_AVAILABLE) {
                        continue;
                    }
                    Color color = FxColorParser.parseColor(segText);
                    if (color == null) {
                        continue;
                    }
                    icon = CssPreviewIconRenderer.createSquareIcon(color);
                    tooltip = FxToolsBundle.message("css.preview.tooltip.direct", segText);
                } else if (FxColorParser.isVariableReference(segText)) {
                    List<FxColorParser.ResolvedColor> resolved =
                            FxColorParser.resolveVariableColors(segText, allVars);
                    if (resolved.isEmpty()) {
                        continue;
                    }
                    if (resolved.size() == 1) {
                        icon = CssPreviewIconRenderer.createCircleIcon(resolved.get(0).getColor());
                        tooltip = FxToolsBundle.message("css.preview.tooltip.resolved",
                                segText, resolved.get(0).getHexValue());
                    } else {
                        icon = CssPreviewIconRenderer.createHalfCircleIcon(
                                resolved.get(0).getColor(), resolved.get(1).getColor());
                        tooltip = FxToolsBundle.message("css.preview.tooltip.multi",
                                segText, resolved.size());
                    }
                }

                if (icon != null) {
                    // Marker range is a 1-char slice inside the property-name token,
                    // offset by segment index so each marker has a distinct range.
                    TextRange range = TextRange.create(
                            matcher.start() + segIdx, matcher.start() + segIdx + 1);
                    String tip = tooltip;
                    int segDocStart = valueDocStart + seg.getStartInValue();
                    int segDocEnd = valueDocStart + seg.getEndInValue();
                    String segVText = segText;
                    result.add(new LineMarkerInfo<>(anchor, range, icon,
                            e -> tip,
                            (e, elt) -> CssGutterColorHandler.openEditor(
                                    e, file, segDocStart, segDocEnd, segVText),
                            GutterIconRenderer.Alignment.LEFT, () -> tip));
                }
            }
        }
    }

    /**
     * For multi-paint properties returns all segments (flat list for background-color,
     * nested grid for border-color); for other properties returns just the first segment.
     */
    @NotNull
    private static List<CssPaintListScanner.Segment> selectSegments(
            @NotNull String propertyName, @NotNull String value) {
        if (CssPaintListScanner.isPaintListProperty(propertyName)) {
            return CssPaintListScanner.scanForProperty(propertyName, value);
        }
        List<CssPaintListScanner.Segment> all = CssPaintListScanner.scan(value);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(all.get(0));
    }
}
