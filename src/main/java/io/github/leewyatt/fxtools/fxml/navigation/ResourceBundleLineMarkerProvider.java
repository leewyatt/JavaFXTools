package io.github.leewyatt.fxtools.fxml.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.*;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.ui.awt.RelativePoint;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.reference.BundleFileElement;
import io.github.leewyatt.fxtools.fxml.reference.ResourceBundleUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Gutter icon for {@code ResourceBundle.getBundle("...")} calls,
 * navigating to the corresponding .properties files.
 */
public class ResourceBundleLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }
        if (!"getBundle".equals(element.getText())) {
            return null;
        }

        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression methodRef)) {
            return null;
        }
        PsiElement grandParent = methodRef.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression call)) {
            return null;
        }

        PsiExpression qualifier = methodRef.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression ref)
                || !"ResourceBundle".equals(ref.getReferenceName())) {
            return null;
        }

        String bundleName = ResourceBundleUtil.extractBundleName(call);
        if (bundleName == null || bundleName.isEmpty()) {
            return null;
        }

        List<PsiFile> files = ResourceBundleUtil.findBundleFiles(element.getProject(), bundleName);
        if (files.isEmpty()) {
            return null;
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Nodes.ResourceBundle,
                e -> FxToolsBundle.message("gutter.resource.bundle.tooltip", files.size()),
                new BundleNavigationHandler(files),
                GutterIconRenderer.Alignment.LEFT,
                () -> FxToolsBundle.message("gutter.resource.bundle.tooltip", files.size())
        );
    }

    private static class BundleNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
        private final List<PsiFile> files;

        BundleNavigationHandler(@NotNull List<PsiFile> files) {
            this.files = files;
        }

        @Override
        public void navigate(MouseEvent e, PsiElement elt) {
            if (files.size() == 1) {
                FileEditorManager.getInstance(elt.getProject())
                        .openFile(files.get(0).getVirtualFile(), true);
                return;
            }

            NavigatablePsiElement[] targets = new NavigatablePsiElement[files.size()];
            for (int i = 0; i < files.size(); i++) {
                targets[i] = new BundleFileElement(files.get(i));
            }

            NavigationUtil.getPsiElementPopup(
                    targets,
                    FxToolsBundle.message("gutter.resource.bundle.tooltip", files.size())
            ).show(new RelativePoint(e));
        }
    }
}
