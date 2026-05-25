package io.github.leewyatt.fxtools.javacolor;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.List;

/**
 * Gutter line-marker provider for {@code javafx.scene.paint.Color} expressions
 * in {@code .java} sources.
 *
 * <p>Covers all nine shapes listed in {@code JAVA_COLOR_PREVIEW_RESEARCH.md §3}.
 * Clicking the icon opens {@link io.github.leewyatt.fxtools.paintpicker.PaintPicker}
 * and writes the edited color back via {@link JavaColorWriter} with maximum
 * format preservation.</p>
 */
public class JavaColorPreviewProvider implements LineMarkerProvider {

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

        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (!(element instanceof PsiIdentifier identifier)) {
                continue;
            }
            if (!JavaColorExpressionDetector.isCandidateIdentifier(identifier)) {
                continue;
            }
            JavaColorExpression expression = JavaColorExpressionDetector.detect(identifier);
            if (expression == null) {
                continue;
            }
            Icon icon = CssPreviewIconRenderer.createSquareIcon(expression.color());
            PsiFile file = identifier.getContainingFile();
            if (file == null) {
                continue;
            }
            GutterIconNavigationHandler<PsiElement> handler =
                    (e, elt) -> JavaColorGutterHandler.openEditor(e, file, expression);
            result.add(new LineMarkerInfo<>(
                    identifier,
                    identifier.getTextRange(),
                    icon,
                    null,
                    handler,
                    CssPreviewIconRenderer.GUTTER_ALIGNMENT,
                    () -> ""));
        }
    }
}
