package io.github.leewyatt.fxtools.css.preview;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.css.preview.effect.CssGutterEffectHandler;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provides gutter preview icons for colors, gradients, SVG paths, effects and
 * Ikonli icon codes inside FXML {@code style="..."} attributes. Emits one icon
 * per previewable declaration (up to {@link InlineCssGutterUtil#MAX_INLINE_MARKERS}).
 */
public class FxmlInlineCssLineMarkerProvider implements LineMarkerProvider {

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
        VirtualFile vFile = file != null ? file.getVirtualFile() : null;
        if (vFile == null || !"fxml".equalsIgnoreCase(vFile.getExtension())) {
            return;
        }

        Map<String, List<String>> allVars =
                FxCssPropertyIndex.getAllVariables(file.getProject());

        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            // Trigger on the XML_NAME token of the "style" attribute — each attribute
            // contributes exactly one such token per highlighting pass.
            if (!(element instanceof XmlToken token)) {
                continue;
            }
            if (token.getTokenType() != XmlTokenType.XML_NAME
                    || !"style".equals(token.getText())) {
                continue;
            }
            if (!(token.getParent() instanceof XmlAttribute attr)) {
                continue;
            }
            XmlAttributeValue attrValue = attr.getValueElement();
            if (attrValue == null) {
                continue;
            }
            String cssText = attrValue.getValue();
            if (cssText == null || cssText.isBlank()) {
                continue;
            }

            PsiElement leafAnchor = findValueTokenLeaf(attrValue);
            if (leafAnchor == null) {
                continue;
            }

            emitMarkers(attrValue, cssText, leafAnchor, file, allVars, result);
        }
    }

    private static void emitMarkers(@NotNull XmlAttributeValue attrValue,
                                    @NotNull String cssText,
                                    @NotNull PsiElement leafAnchor,
                                    @NotNull PsiFile file,
                                    @NotNull Map<String, List<String>> allVars,
                                    @NotNull Collection<? super LineMarkerInfo<?>> result) {
        List<InlineCssGutterUtil.PreviewMatch> matches =
                InlineCssGutterUtil.findAllPreviewables(cssText, allVars);
        if (matches.isEmpty()) {
            return;
        }

        int contentStart = attrValue.getValueTextRange().getStartOffset();
        int contentEnd = contentStart + cssText.length();

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
     * Returns the {@code XML_ATTRIBUTE_VALUE_TOKEN} leaf inside an attribute value,
     * anchored on a leaf to avoid LineMarker "should target a leaf" warnings.
     */
    @Nullable
    private static PsiElement findValueTokenLeaf(@NotNull XmlAttributeValue attrValue) {
        PsiElement child = attrValue.getFirstChild();
        while (child != null) {
            if (child instanceof XmlToken t
                    && t.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
                return t;
            }
            child = child.getNextSibling();
        }
        return null;
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
        java.util.Set<String> availablePacks = IconDataService.getAvailablePacks(project);
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
