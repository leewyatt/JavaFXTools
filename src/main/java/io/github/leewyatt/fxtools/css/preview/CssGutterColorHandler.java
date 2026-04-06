package io.github.leewyatt.fxtools.css.preview;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.paintpicker.PaintConvertUtil;
import io.github.leewyatt.fxtools.paintpicker.PaintPicker;
import io.github.leewyatt.fxtools.util.FxColorParser;
import io.github.leewyatt.fxtools.util.FxGradientParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles gutter icon click to open a PaintPicker popup for editing CSS color/gradient values.
 */
public final class CssGutterColorHandler {

    private CssGutterColorHandler() {
    }

    /**
     * Opens a PaintPicker popup for the given CSS value range.
     *
     * @param mouseEvent the click event for popup positioning
     * @param psiFile    the CSS file
     * @param valueStart start offset of the value text in the document
     * @param valueEnd   end offset of the value text in the document
     * @param valueText  the current CSS value text
     */
    public static void openEditor(@NotNull MouseEvent mouseEvent,
                                   @NotNull PsiFile psiFile,
                                   int valueStart, int valueEnd,
                                   @NotNull String valueText) {
        Project project = psiFile.getProject();
        Editor editor = findEditor(project, psiFile.getVirtualFile());
        if (editor == null) {
            return;
        }

        Document document = editor.getDocument();
        RangeMarker marker = document.createRangeMarker(valueStart, valueEnd);
        marker.setGreedyToRight(true);

        // Check if derive() — load base color, preserve offset
        FxColorParser.DeriveInfo deriveInfo = FxColorParser.parseDeriveInfo(valueText.trim());

        // Parse the current value to a Paint object
        Paint initialPaint;
        if (deriveInfo != null) {
            initialPaint = deriveInfo.getBaseColor(); // edit the base color
        } else {
            initialPaint = parseCssValueToPaint(valueText, project);
        }
        if (initialPaint == null) {
            marker.dispose();
            return;
        }

        PaintPicker picker = new PaintPicker(initialPaint);
        String groupId = "PaintPicker.ColorChange." + System.nanoTime();
        boolean[] changed = {false};

        picker.addPaintChangeListener(evt -> {
            Paint newPaint = (Paint) evt.getNewValue();
            if (newPaint == null || !marker.isValid()) {
                return;
            }
            String newText;
            if (deriveInfo != null && newPaint instanceof Color newColor) {
                // Preserve derive() wrapper with original offset
                String hex = String.format("#%02x%02x%02x", newColor.getRed(), newColor.getGreen(), newColor.getBlue());
                newText = "derive(" + hex + ", " + formatOffset(deriveInfo.getBrightnessOffset()) + "%)";
            } else {
                newText = PaintConvertUtil.convertPaintToCss(newPaint);
            }
            WriteCommandAction.runWriteCommandAction(project, "Change Color", groupId, () -> {
                document.replaceString(marker.getStartOffset(), marker.getEndOffset(), newText);
            });
            changed[0] = true;
        });

        // Wrap in a scroll pane with fixed size and padding
        picker.setBorder(com.intellij.util.ui.JBUI.Borders.empty(6));
        com.intellij.ui.components.JBScrollPane scrollPane =
                new com.intellij.ui.components.JBScrollPane(picker);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(420), JBUI.scale(665)));

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, picker)
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .createPopup();

        popup.addListener(new com.intellij.openapi.ui.popup.JBPopupListener() {
            @Override
            public void onClosed(com.intellij.openapi.ui.popup.@NotNull LightweightWindowEvent event) {
                marker.dispose();
            }
        });

        popup.show(new RelativePoint(mouseEvent));
    }

    /**
     * Parses a CSS value string into an AWT Paint object.
     */
    @Nullable
    static Paint parseCssValueToPaint(@NotNull String valueText, @NotNull Project project) {
        String trimmed = valueText.trim();

        // Direct color
        Color color = FxColorParser.parseColor(trimmed);
        if (color != null) {
            return color;
        }

        // Variable reference — resolve to color or gradient
        if (FxColorParser.isVariableReference(trimmed)) {
            com.intellij.psi.search.GlobalSearchScope scope =
                    com.intellij.psi.search.GlobalSearchScope.allScope(project);
            Color resolved = FxColorParser.resolveVariableColor(trimmed, project, scope);
            if (resolved != null) {
                return resolved;
            }
            // Try resolving variable chain to gradient
            String gradientExpr = resolveGradientVariable(trimmed, project, scope,
                    new HashSet<>(), 0);
            if (gradientExpr != null) {
                trimmed = gradientExpr;
            }
        }

        // Linear gradient
        if (trimmed.toLowerCase().startsWith("linear-gradient")) {
            FxGradientParser.LinearGradientInfo info =
                    FxGradientParser.parseLinearGradient(trimmed, project,
                            com.intellij.psi.search.GlobalSearchScope.allScope(project));
            if (info != null && info.getStops().size() >= 2) {
                return toProportionalLinearPaint(info);
            }
        }

        // Radial gradient
        if (trimmed.toLowerCase().startsWith("radial-gradient")) {
            FxGradientParser.RadialGradientInfo info =
                    FxGradientParser.parseRadialGradient(trimmed, project,
                            com.intellij.psi.search.GlobalSearchScope.allScope(project));
            if (info != null && info.getStops().size() >= 2) {
                return toProportionalRadialPaint(info);
            }
        }

        return null;
    }

    private static final int MAX_GRADIENT_RESOLVE_DEPTH = 10;

    @Nullable
    private static String resolveGradientVariable(@NotNull String varName,
                                                    @NotNull Project project,
                                                    @NotNull com.intellij.psi.search.GlobalSearchScope scope,
                                                    @NotNull Set<String> visited,
                                                    int depth) {
        if (depth >= MAX_GRADIENT_RESOLVE_DEPTH || !visited.add(varName)) {
            return null;
        }
        List<String> values = io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex
                .getPropertyValues(varName, project, scope);
        for (String rawValue : values) {
            String v = rawValue.trim();
            if (FxColorParser.isGradient(v)) {
                return v;
            }
            if (FxColorParser.isVariableReference(v)) {
                String resolved = resolveGradientVariable(v, project, scope, visited, depth + 1);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    @NotNull
    private static LinearGradientPaint toProportionalLinearPaint(@NotNull FxGradientParser.LinearGradientInfo info) {
        java.util.List<FxGradientParser.Stop> stops = info.getStops();
        float[] fractions = new float[stops.size()];
        Color[] colors = new Color[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            fractions[i] = stops.get(i).getOffset();
            colors[i] = stops.get(i).getColor();
        }
        ensureStrictlyIncreasing(fractions);

        float sx = info.getStartX(), sy = info.getStartY();
        float ex = info.getEndX(), ey = info.getEndY();
        if (sx == ex && sy == ey) {
            ex = sx + 0.001f;
        }
        return new LinearGradientPaint(
                new Point2D.Float(sx, sy), new Point2D.Float(ex, ey),
                fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    @NotNull
    private static RadialGradientPaint toProportionalRadialPaint(@NotNull FxGradientParser.RadialGradientInfo info) {
        java.util.List<FxGradientParser.Stop> stops = info.getStops();
        float[] fractions = new float[stops.size()];
        Color[] colors = new Color[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            fractions[i] = stops.get(i).getOffset();
            colors[i] = stops.get(i).getColor();
        }
        ensureStrictlyIncreasing(fractions);

        float cx = info.getCenterX(), cy = info.getCenterY();
        float r = info.getRadius();
        if (r <= 0) {
            r = 0.5f;
        }
        float fa = info.getFocusAngle();
        float fd = info.getFocusDistance();
        float fx = (float) (cx + fd * r * Math.cos(Math.toRadians(fa)));
        float fy = (float) (cy + fd * r * Math.sin(Math.toRadians(fa)));

        return new RadialGradientPaint(
                new Point2D.Float(cx, cy), r, new Point2D.Float(fx, fy),
                fractions, colors, MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    private static void ensureStrictlyIncreasing(float[] fractions) {
        for (int i = 1; i < fractions.length; i++) {
            if (fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + 0.001f;
            }
        }
        if (fractions.length > 0 && fractions[fractions.length - 1] > 1.0f) {
            fractions[fractions.length - 1] = 1.0f;
        }
    }

    @Nullable
    @NotNull
    private static String formatOffset(double offset) {
        if (offset == (int) offset) {
            return Integer.toString((int) offset);
        }
        return Double.toString(offset);
    }

    @Nullable
    private static Editor findEditor(@NotNull Project project, @Nullable VirtualFile vFile) {
        if (vFile == null) {
            return null;
        }
        var editors = FileEditorManager.getInstance(project).getEditors(vFile);
        for (var fe : editors) {
            if (fe instanceof TextEditor te) {
                return te.getEditor();
            }
        }
        return null;
    }
}
