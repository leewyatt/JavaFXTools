package io.github.leewyatt.fxtools.ikonli;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import io.github.leewyatt.fxtools.css.completion.FxCssCompletionUtil;
import io.github.leewyatt.fxtools.css.preview.CssGutterIconCodeHandler;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
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
 * Shows a gutter preview icon next to Java references to Ikonli icon enum constants
 * (e.g. {@code FontAwesome.HOME}, {@code BootstrapIcons.ARROW_UP}). Clicking the icon
 * opens the same preview popup as the CSS {@code -fx-icon-code} gutter.
 */
public class IkonliGutterIconProvider implements LineMarkerProvider {

    /** Rendered icon cache, keyed by {@code packId + ":" + iconName}. */
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
        Project project = elements.get(0).getProject();
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

        // elementSet guards against dual-pass collection over the same identifiers
        Set<PsiElement> elementSet = new HashSet<>(elements);

        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (!(element instanceof PsiIdentifier identifier)) {
                continue;
            }
            if (!elementSet.contains(identifier)) {
                continue;
            }

            LineMarkerInfo<?> info = buildMarker(identifier, service, availablePacks);
            if (info != null) {
                result.add(info);
            }
        }
    }

    @Nullable
    private static LineMarkerInfo<?> buildMarker(@NotNull PsiIdentifier identifier,
                                                  @NotNull IconDataService service,
                                                  @NotNull Set<String> availablePacks) {
        PsiElement parent = identifier.getParent();
        if (!(parent instanceof PsiReferenceExpression refExpr)) {
            return null;
        }
        // Must be qualified (e.g. FontAwesome.HOME, not a bare HOME inside the enum class)
        if (refExpr.getQualifierExpression() == null) {
            return null;
        }

        PsiElement resolved = refExpr.resolve();
        if (!(resolved instanceof PsiEnumConstant enumConstant)) {
            return null;
        }

        PsiClass enumClass = enumConstant.getContainingClass();
        if (enumClass == null) {
            return null;
        }
        String fqcn = enumClass.getQualifiedName();
        if (fqcn == null) {
            return null;
        }

        String packId = service.getPackIdByEnumClass(fqcn);
        if (packId == null || !availablePacks.contains(packId)) {
            return null;
        }

        String iconName = toIconName(enumConstant.getName());
        if (iconName == null) {
            return null;
        }

        String literal = packId + "-" + iconName;
        IconDataService.IconEntry iconEntry = service.getLiteralMap().get(literal);
        if (iconEntry == null) {
            return null;
        }

        String pathData;
        Icon icon;
        if (iconEntry.isRenderable()) {
            if (!service.isPackLoaded(packId)) {
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

        final String pathRef = pathData;
        GutterIconNavigationHandler<PsiElement> handler =
                (e, elt) -> CssGutterIconCodeHandler.openPreview(e, iconEntry, pathRef);

        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                icon,
                psi -> literal,
                handler,
                CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                () -> literal);
    }

    /**
     * Converts a Java enum constant name to the Ikonli icon name.
     * Example: {@code ARROW_UP} → {@code arrow-up}, {@code HOME} → {@code home}.
     *
     * @return the icon name, or {@code null} if the name is empty
     */
    @Nullable
    private static String toIconName(@Nullable String constantName) {
        if (constantName == null || constantName.isEmpty()) {
            return null;
        }
        return constantName.toLowerCase().replace('_', '-');
    }
}
