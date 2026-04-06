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
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides gutter SVG icon preview for {@code -fx-icon-code} properties in .css files.
 * Resolves Ikonli icon literal to SVG path data and renders a 16x16 preview icon.
 */
public class FxCssIconCodePreviewProvider implements LineMarkerProvider {

    private static final Pattern ICON_CODE_PATTERN =
            Pattern.compile("-fx-icon-code\\s*:\\s*\"([^\"]+)\"\\s*;");
    private static final Color ICON_COLOR =
            new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY);

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

        Project project = file.getProject();
        if (!FxDetector.isJavaFxProject(project)) {
            return;
        }

        IconDataService service = IconDataService.getInstance();
        if (!service.isLoaded()) {
            return;
        }

        Set<String> availablePacks = IconDataService.getAvailablePacks(project);
        if (availablePacks.isEmpty()) {
            return;
        }

        String text = file.getText();
        Set<PsiElement> elementSet = new HashSet<>(elements);
        String matchText = CssPreviewIconRenderer.stripCommentsPreservingOffsets(text);

        Matcher matcher = ICON_CODE_PATTERN.matcher(matchText);
        while (matcher.find()) {
            ProgressManager.checkCanceled();
            String literal = matcher.group(1).trim();

            int matchStart = matcher.start();
            PsiElement anchor = file.findElementAt(matchStart);
            if (anchor == null || !elementSet.contains(anchor)) {
                continue;
            }

            // Look up icon and resolve SVG path
            IconDataService.IconEntry icon = service.getLiteralMap().get(literal);
            if (icon == null || !availablePacks.contains(icon.getPackId())) {
                continue;
            }

            Icon gutterIcon;
            String pathData = null;
            if (icon.isRenderable()) {
                if (!service.isPackLoaded(icon.getPackId())) {
                    service.ensurePackLoaded(icon.getPack());
                }
                pathData = service.getPath(icon);
                if (pathData == null) {
                    continue;
                }
                gutterIcon = CssPreviewIconRenderer.createSvgIcon(pathData, ICON_COLOR);
                if (gutterIcon == null) {
                    continue;
                }
            } else {
                gutterIcon = io.github.leewyatt.fxtools.toolwindow.iconbrowser
                        .IconPlaceholder.createIcon(CssPreviewIconRenderer.ICON_SIZE);
            }

            String tooltip = icon.getPack().getName() + " — " + literal;
            TextRange range = TextRange.create(matchStart, matchStart + 1);
            IconDataService.IconEntry iconRef = icon;
            String pathRef = pathData;
            result.add(new LineMarkerInfo<>(anchor, range, gutterIcon,
                    e -> tooltip,
                    (e, elt) -> CssGutterIconCodeHandler.openPreview(e, iconRef, pathRef),
                    GutterIconRenderer.Alignment.LEFT, () -> tooltip));
        }
    }
}
