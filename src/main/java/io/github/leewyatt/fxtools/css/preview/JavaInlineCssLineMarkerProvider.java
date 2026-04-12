package io.github.leewyatt.fxtools.css.preview;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.css.preview.effect.CssGutterEffectHandler;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides gutter preview icons for:
 * <ul>
 *   <li>Colors, gradients, SVG paths inside {@code setStyle("...")} string literals and text blocks</li>
 *   <li>SVG paths inside {@code SVGPath.setContent("...")} (read-only preview)</li>
 * </ul>
 */
public class JavaInlineCssLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (!(element instanceof PsiIdentifier)) {
                continue;
            }

            String methodName = element.getText();
            if ("setStyle".equals(methodName)) {
                processSetStyleCall(element, result);
            } else if ("setContent".equals(methodName)) {
                processSetContentCall(element, result);
            }
        }
    }

    // ---- setStyle("...") handling ----

    private static void processSetStyleCall(@NotNull PsiElement element,
                                             @NotNull Collection<? super LineMarkerInfo<?>> result) {
        PsiMethodCallExpression call = resolveMethodCall(element);
        if (call == null) {
            return;
        }
        PsiLiteralExpression literal = getSingleStringArg(call);
        if (literal == null) {
            return;
        }
        Object value = literal.getValue();
        if (!(value instanceof String cssText) || cssText.isBlank()) {
            return;
        }

        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }

        Map<String, List<String>> allVars = FxCssPropertyIndex.getAllVariables(file.getProject());
        if (literal.isTextBlock()) {
            collectTextBlockMarkers(literal, file, allVars, result);
        } else {
            collectStringMarkers(element, literal, cssText, file, allVars, result);
        }
    }

    /**
     * Handles regular string: one gutter icon per previewable declaration (up to
     * {@link InlineCssGutterUtil#MAX_INLINE_MARKERS}). All markers are anchored on
     * the string literal leaf so their sub-ranges fall inside the anchor range.
     */
    private static void collectStringMarkers(@NotNull PsiElement trigger,
                                              @NotNull PsiLiteralExpression literal,
                                              @NotNull String cssText,
                                              @NotNull PsiFile file,
                                              @NotNull Map<String, List<String>> allVars,
                                              @NotNull Collection<? super LineMarkerInfo<?>> result) {
        List<InlineCssGutterUtil.PreviewMatch> matches =
                InlineCssGutterUtil.findAllPreviewables(cssText, allVars);
        if (matches.isEmpty()) {
            return;
        }

        PsiElement leafAnchor = literal.getFirstChild();
        if (leafAnchor == null) {
            leafAnchor = trigger;
        }

        int contentStart = literal.getTextRange().getStartOffset() + 1;
        int contentEnd = literal.getTextRange().getEndOffset() - 1;

        for (InlineCssGutterUtil.PreviewMatch match : matches) {
            if (!InlineCssGutterUtil.isMatchTypeEnabled(match.getType())) {
                continue;
            }
            Icon icon = InlineCssGutterUtil.createIcon(match, file.getProject(), allVars);
            if (icon == null) {
                continue;
            }
            int docValueStart = contentStart + match.getValueStart();
            int docValueEnd = contentStart + match.getValueEnd();

            String tooltip = match.getPropertyName() + ": " + match.getValue();
            GutterIconNavigationHandler<PsiElement> handler = createClickHandler(
                    match, file, docValueStart, docValueEnd, contentStart, contentEnd);

            result.add(new LineMarkerInfo<>(leafAnchor,
                    new TextRange(docValueStart, docValueEnd),
                    icon,
                    e -> tooltip,
                    handler,
                    CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                    () -> tooltip));
        }
    }

    /**
     * Handles text block: one gutter icon per line that has a previewable value.
     */
    private static void collectTextBlockMarkers(@NotNull PsiLiteralExpression literal,
                                                 @NotNull PsiFile file,
                                                 @NotNull Map<String, List<String>> allVars,
                                                 @NotNull Collection<? super LineMarkerInfo<?>> result) {
        String rawText = literal.getText();
        int literalStart = literal.getTextRange().getStartOffset();

        int contentStartIdx = rawText.indexOf('\n');
        if (contentStartIdx < 0) {
            return;
        }
        contentStartIdx++;

        int contentEndIdx = rawText.lastIndexOf("\"\"\"");
        if (contentEndIdx <= contentStartIdx) {
            return;
        }

        int docContentStart = literalStart + contentStartIdx;
        int docContentEnd = literalStart + contentEndIdx;

        String content = rawText.substring(contentStartIdx, contentEndIdx);
        String[] lines = content.split("\n", -1);

        PsiElement textBlockToken = literal.getFirstChild();
        if (textBlockToken == null) {
            textBlockToken = literal;
        }

        int lineOffset = 0;
        for (String line : lines) {
            ProgressManager.checkCanceled();
            if (!line.isBlank()) {
                List<InlineCssGutterUtil.PreviewMatch> matches =
                        InlineCssGutterUtil.findAllPreviewables(line, allVars);
                int docLineStart = docContentStart + lineOffset;
                for (InlineCssGutterUtil.PreviewMatch match : matches) {
                    if (!InlineCssGutterUtil.isMatchTypeEnabled(match.getType())) {
                        continue;
                    }
                    Icon icon = InlineCssGutterUtil.createIcon(match, file.getProject(), allVars);
                    if (icon == null) {
                        continue;
                    }
                    int docValueStart = docLineStart + match.getValueStart();
                    int docValueEnd = docLineStart + match.getValueEnd();

                    String tooltip = match.getPropertyName() + ": " + match.getValue();
                    GutterIconNavigationHandler<PsiElement> handler = createClickHandler(
                            match, file, docValueStart, docValueEnd,
                            docContentStart, docContentEnd);

                    result.add(new LineMarkerInfo<>(textBlockToken,
                            new TextRange(docValueStart, docValueEnd), icon,
                            e -> tooltip, handler,
                            CssPreviewIconRenderer.GUTTER_ALIGNMENT, () -> tooltip));
                }
            }
            lineOffset += line.length() + 1;
        }
    }

    @NotNull
    private static GutterIconNavigationHandler<PsiElement> createClickHandler(
            @NotNull InlineCssGutterUtil.PreviewMatch match,
            @NotNull PsiFile file, int docValueStart, int docValueEnd,
            int styleContentStart, int styleContentEnd) {
        if (match.getType() == InlineCssGutterUtil.MatchType.SVG_PATH) {
            return (e, elt) -> CssGutterSvgHandler.openPreviewInline(
                    e, file, match.getValue(),
                    styleContentStart, styleContentEnd);
        } else if (match.getType() == InlineCssGutterUtil.MatchType.EFFECT) {
            return (e, elt) -> CssGutterEffectHandler.openEditor(
                    e, file, docValueStart, docValueEnd, match.getValue());
        } else if (match.getType() == InlineCssGutterUtil.MatchType.ICON_CODE) {
            return (e, elt) -> openIconCodePreview(e, match.getValue(), file.getProject());
        } else {
            return (e, elt) -> CssGutterColorHandler.openEditor(
                    e, file, docValueStart, docValueEnd, match.getValue());
        }
    }

    // ---- SVGPath.setContent("...") handling ----

    private static void processSetContentCall(@NotNull PsiElement element,
                                               @NotNull Collection<? super LineMarkerInfo<?>> result) {
        PsiMethodCallExpression call = resolveMethodCall(element);
        if (call == null) {
            return;
        }
        PsiLiteralExpression literal = getSingleStringArg(call);
        if (literal == null) {
            return;
        }
        Object value = literal.getValue();
        if (!(value instanceof String pathData) || pathData.isBlank()) {
            return;
        }
        if (!FxSvgRenderer.isSvgPath(pathData)) {
            return;
        }

        // Optional: verify the receiver is SVGPath via type resolution
        if (!isSvgPathSetContent(call)) {
            return;
        }

        Icon icon = CssPreviewIconRenderer.createSvgIcon(pathData, JBColor.foreground());
        if (icon == null) {
            return;
        }

        String tooltip = "SVG path preview";
        result.add(new LineMarkerInfo<>(element,
                element.getTextRange(),
                icon,
                e -> tooltip,
                (e, elt) -> CssGutterSvgHandler.openPreviewReadOnly(e, pathData),
                CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                () -> tooltip));
    }

    /**
     * Checks if the method call is on an SVGPath instance.
     * First tries type resolution; falls back to true if resolution fails
     * (since isSvgPath already validated the content).
     */
    private static boolean isSvgPathSetContent(@NotNull PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method != null) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qName = containingClass.getQualifiedName();
                // Only match SVGPath.setContent, not other setContent methods
                return "javafx.scene.shape.SVGPath".equals(qName)
                        || com.intellij.psi.util.InheritanceUtil.isInheritor(
                        containingClass, "javafx.scene.shape.SVGPath");
            }
        }
        // Resolution failed (incomplete classpath) — fall back to content-based detection
        // isSvgPath() already validated the string, so false positives are unlikely
        return true;
    }

    // ---- Shared helpers ----

    @Nullable
    private static PsiMethodCallExpression resolveMethodCall(@NotNull PsiElement identifier) {
        PsiElement parent = identifier.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
            return null;
        }
        PsiElement grandparent = parent.getParent();
        return grandparent instanceof PsiMethodCallExpression call ? call : null;
    }

    @Nullable
    private static PsiLiteralExpression getSingleStringArg(@NotNull PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 1 || !(args[0] instanceof PsiLiteralExpression literal)) {
            return null;
        }
        return literal.getValue() instanceof String ? literal : null;
    }

    /**
     * Resolves an Ikonli icon literal and opens the preview popup. For {@code np: true}
     * icons a placeholder preview is shown with a "cannot render" hint.
     */
    private static void openIconCodePreview(@NotNull MouseEvent mouseEvent,
                                            @NotNull String literal,
                                            @NotNull Project project) {
        IconDataService service = IconDataService.getInstance();
        // Defensive ensureLoaded — normally data is already loaded by the time a gutter
        // icon is clickable, but we want this click handler to be self-sufficient.
        service.ensureLoaded();
        if (!service.isLoaded()) {
            return;
        }
        Set<String> availablePacks = IconDataService.getAvailablePacks(project);
        IconDataService.IconEntry icon = service.resolveLiteral(literal, availablePacks);
        if (icon == null) {
            return;
        }
        String pathData = null;
        if (icon.isRenderable()) {
            if (!service.isPackLoaded(icon.getPackId())) {
                service.ensurePackLoaded(icon.getPack());
            }
            pathData = service.getPath(icon);
        }
        CssGutterIconCodeHandler.openPreview(mouseEvent, icon, pathData);
    }
}
