package io.github.leewyatt.fxtools.fxmlkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides gutter icons on FxmlView/FxmlViewProvider class declarations
 * for navigating to the associated FXML and CSS files.
 */
public class FxmlKitViewLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier)) {
            return;
        }

        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiClass)) {
            return;
        }

        PsiClass psiClass = (PsiClass) parent;
        Project project = element.getProject();

        if (!FxmlKitDetector.isFxmlKitProject(project)) {
            return;
        }
        if (!FxFileResolver.isFxmlKitViewClass(psiClass)) {
            return;
        }

        VirtualFile fxmlFile = FxFileResolver.findFxmlFile(psiClass);
        if (fxmlFile == null) {
            return;
        }

        PsiFile fxmlPsiFile = PsiManager.getInstance(project).findFile(fxmlFile);
        if (fxmlPsiFile != null) {
            NavigationGutterIconBuilder<PsiElement> fxmlBuilder = NavigationGutterIconBuilder
                    .create(AllIcons.FileTypes.Xml)
                    .setTarget(fxmlPsiFile)
                    .setTooltipText(FxToolsBundle.message("navigate.to.fxml"));
            result.add(fxmlBuilder.createLineMarkerInfo(element));
        }

        VirtualFile cssFile = FxFileResolver.findCssFile(fxmlFile);
        if (cssFile != null) {
            PsiFile cssPsiFile = PsiManager.getInstance(project).findFile(cssFile);
            if (cssPsiFile != null) {
                NavigationGutterIconBuilder<PsiElement> cssBuilder = NavigationGutterIconBuilder
                        .create(AllIcons.FileTypes.Css)
                        .setTarget(cssPsiFile)
                        .setTooltipText(FxToolsBundle.message("navigate.to.css"));
                result.add(cssBuilder.createLineMarkerInfo(element));
            }
        }
    }
}
