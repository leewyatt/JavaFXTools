package io.github.leewyatt.fxtools.ikonli;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.css.preview.CssGutterIconCodeHandler;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
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
 * Shows gutter preview icons for Ikonli icon references in Java code:
 * <ul>
 *   <li>Enum constant references: {@code FontAwesome.HOME}, {@code MaterialDesign.MDI_TAG}</li>
 *   <li>String literal arguments: {@code new FontIcon("mdi-tag")}, {@code setIconLiteral("fa-home")}</li>
 * </ul>
 * Clicking the icon opens the same preview popup as the CSS {@code -fx-icon-code} gutter.
 */
public class IkonliGutterIconProvider implements LineMarkerProvider {

    private static final String FONT_ICON_FQN = "org.kordamp.ikonli.javafx.FontIcon";

    /** Rendered icon cache, keyed by literal. */
    private static final ConcurrentHashMap<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();

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

        Set<PsiElement> elementSet = new HashSet<>(elements);

        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (!elementSet.contains(element)) {
                continue;
            }

            LineMarkerInfo<?> info = null;
            if (element instanceof PsiIdentifier identifier) {
                info = buildEnumMarker(identifier, service, availablePacks);
            } else if (element instanceof PsiJavaToken token
                    && token.getTokenType() == JavaTokenType.STRING_LITERAL) {
                info = buildStringLiteralMarker(token, service);
            }
            if (info != null) {
                result.add(info);
            }
        }
    }

    // ==================== Enum Constant Gutter ====================

    @Nullable
    private static LineMarkerInfo<?> buildEnumMarker(@NotNull PsiIdentifier identifier,
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

        // Direct lookup by real enum constant name — no guessing
        IconDataService.IconEntry iconEntry = service.findByEnumConstant(fqcn, enumConstant.getName());
        if (iconEntry == null) {
            return null;
        }

        String literal = iconEntry.getLiteral();
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
                    k -> CssPreviewIconRenderer.createSvgIcon(pathData, com.intellij.ui.JBColor.foreground()));
            if (icon == null) {
                return null;
            }
        } else {
            pathData = null;
            icon = ICON_CACHE.computeIfAbsent("__placeholder__",
                    k -> io.github.leewyatt.fxtools.toolwindow.iconbrowser
                            .IconPlaceholder.createIcon(CssPreviewIconRenderer.getGutterIconSize()));
        }

        GutterIconNavigationHandler<PsiElement> handler =
                (e, elt) -> CssGutterIconCodeHandler.openPreview(e, iconEntry, pathData);

        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                icon,
                psi -> literal,
                handler,
                CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                () -> literal);
    }

    // ==================== String Literal Gutter ====================

    /**
     * Builds a gutter marker for {@code new FontIcon("mdi-tag")} or
     * {@code setIconLiteral("mdi-tag")} string arguments.
     */
    @Nullable
    private static LineMarkerInfo<?> buildStringLiteralMarker(
            @NotNull PsiJavaToken token,
            @NotNull IconDataService service) {
        PsiElement parent = token.getParent();
        if (!(parent instanceof PsiLiteralExpression literalExpr)) {
            return null;
        }
        Object value = literalExpr.getValue();
        if (!(value instanceof String literalText) || literalText.isEmpty()) {
            return null;
        }

        // Check context: must be inside new FontIcon("...") or setIconLiteral("...")
        if (!isIconLiteralStringContext(literalExpr)) {
            return null;
        }

        // Look up the literal in the icon data
        IconDataService.IconEntry iconEntry = service.getLiteralMap().get(literalText);
        if (iconEntry == null) {
            return null;
        }

        String packId = iconEntry.getPackId();
        String literal = iconEntry.getLiteral();
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
                    k -> CssPreviewIconRenderer.createSvgIcon(pathData, com.intellij.ui.JBColor.foreground()));
            if (icon == null) {
                return null;
            }
        } else {
            pathData = null;
            icon = ICON_CACHE.computeIfAbsent("__placeholder__",
                    k -> io.github.leewyatt.fxtools.toolwindow.iconbrowser
                            .IconPlaceholder.createIcon(CssPreviewIconRenderer.getGutterIconSize()));
        }

        GutterIconNavigationHandler<PsiElement> handler =
                (e, elt) -> CssGutterIconCodeHandler.openPreview(e, iconEntry, pathData);

        return new LineMarkerInfo<>(
                token,
                token.getTextRange(),
                icon,
                psi -> literal,
                handler,
                CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                () -> literal);
    }

    /**
     * Returns {@code true} if the string literal is the first argument of
     * {@code new FontIcon("...")} or a call to {@code setIconLiteral("...")} /
     * {@code setIconCode("...")}.
     */
    private static boolean isIconLiteralStringContext(@NotNull PsiLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof PsiExpressionList argList)) {
            return false;
        }
        PsiExpression[] args = argList.getExpressions();
        if (args.length == 0 || args[0] != literal) {
            return false;
        }
        PsiElement grandParent = argList.getParent();
        if (grandParent instanceof PsiNewExpression newExpr) {
            var classRef = newExpr.getClassReference();
            if (classRef == null) {
                return false;
            }
            String name = classRef.getReferenceName();
            return "FontIcon".equals(name);
        }
        if (grandParent instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            return "setIconLiteral".equals(name);
        }
        return false;
    }
}
