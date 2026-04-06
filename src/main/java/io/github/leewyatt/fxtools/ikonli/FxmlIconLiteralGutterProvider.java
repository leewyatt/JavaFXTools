package io.github.leewyatt.fxtools.ikonli;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.leewyatt.fxtools.css.completion.FxCssCompletionUtil;
import io.github.leewyatt.fxtools.css.preview.CssGutterIconCodeHandler;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a gutter preview icon for Ikonli icon literals inside FXML
 * {@code <FontIcon iconLiteral="..."/>} attributes. Clicking opens the same
 * preview popup used by the CSS {@code -fx-icon-code} gutter.
 */
public class FxmlIconLiteralGutterProvider implements LineMarkerProvider {

    /** Rendered icon cache shared across passes, keyed by icon literal. */
    private static final ConcurrentHashMap<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

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
        if (vFile == null || !"fxml".equalsIgnoreCase(vFile.getExtension())) {
            return;
        }

        Project project = file.getProject();
        if (!FxDetector.isJavaFxProject(project)) {
            return;
        }

        IconDataService service = IconDataService.getInstance();
        service.ensureLoaded();
        if (!service.isLoaded()) {
            return;
        }
        Set<String> availablePacks = IconDataService.getAvailablePacks(project);
        if (availablePacks.isEmpty()) {
            return;
        }

        Set<PsiElement> elementSet = new HashSet<>(elements);

        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (!(element instanceof XmlAttributeValue attrValue)) {
                continue;
            }
            if (!elementSet.contains(attrValue)) {
                continue;
            }

            LineMarkerInfo<?> info = buildMarker(attrValue, service, availablePacks);
            if (info != null) {
                result.add(info);
            }
        }
    }

    @Nullable
    private static LineMarkerInfo<?> buildMarker(@NotNull XmlAttributeValue attrValue,
                                                  @NotNull IconDataService service,
                                                  @NotNull Set<String> availablePacks) {
        PsiElement parent = attrValue.getParent();
        if (!(parent instanceof XmlAttribute attr) || !"iconLiteral".equals(attr.getName())) {
            return null;
        }
        XmlTag tag = attr.getParent();
        if (tag == null || !IkonliFxmlUtil.isFontIconTag(tag)) {
            return null;
        }

        String literal = attrValue.getValue();
        if (literal == null || literal.isEmpty()) {
            return null;
        }

        IconDataService.IconEntry iconEntry = service.getLiteralMap().get(literal);
        if (iconEntry == null || !availablePacks.contains(iconEntry.getPackId())) {
            return null;
        }

        String pathData;
        Icon icon;
        if (iconEntry.isRenderable()) {
            if (!service.isPackLoaded(iconEntry.getPackId())) {
                service.ensurePackLoaded(iconEntry.getPack());
            }
            pathData = service.getPath(iconEntry);
            if (pathData == null) {
                return null;
            }
            icon = ICON_CACHE.computeIfAbsent(literal,
                    k -> FxCssCompletionUtil.createSvgIcon(pathData));
            if (icon == null) {
                return null;
            }
        } else {
            pathData = null;
            icon = ICON_CACHE.computeIfAbsent("__placeholder__",
                    k -> io.github.leewyatt.fxtools.toolwindow.iconbrowser
                            .IconPlaceholder.createIcon(CssPreviewIconRenderer.ICON_SIZE));
        }

        // Anchor on a leaf token of the attribute value (plugin best-practice:
        // LineMarkerProvider should return markers anchored on leaves, not composite PSI)
        PsiElement anchor = findLeafAnchor(attrValue);
        if (anchor == null) {
            anchor = attrValue;
        }

        final String pathRef = pathData;
        GutterIconNavigationHandler<PsiElement> handler =
                (e, elt) -> CssGutterIconCodeHandler.openPreview(e, iconEntry, pathRef);

        return new LineMarkerInfo<>(
                anchor,
                anchor.getTextRange(),
                icon,
                psi -> literal,
                handler,
                CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                () -> literal);
    }

    /**
     * Returns the {@code XML_ATTRIBUTE_VALUE_TOKEN} leaf inside an attribute value,
     * or null if none is found. Using a leaf avoids LineMarker "should only be applied
     * to leaf PSI" warnings.
     */
    @Nullable
    private static PsiElement findLeafAnchor(@NotNull XmlAttributeValue attrValue) {
        PsiElement child = attrValue.getFirstChild();
        while (child != null) {
            if (child instanceof XmlToken token
                    && token.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
                return token;
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
