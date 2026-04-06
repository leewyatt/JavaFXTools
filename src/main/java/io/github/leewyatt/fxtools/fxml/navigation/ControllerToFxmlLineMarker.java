package io.github.leewyatt.fxtools.fxml.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.index.FxControllerIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides a gutter icon on Controller class declarations for navigating
 * to the FXML files that reference them. Works in all JavaFX projects.
 */
public class ControllerToFxmlLineMarker extends RelatedItemLineMarkerProvider {

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
        String fqn = psiClass.getQualifiedName();
        if (fqn == null) {
            return;
        }

        Project project = element.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> fxmlFiles = FxControllerIndex.findFxmlFilesForController(fqn, scope);
        if (fxmlFiles.isEmpty()) {
            return;
        }

        List<PsiFile> targets = new ArrayList<>();
        for (VirtualFile vf : fxmlFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
            if (psiFile != null) {
                targets.add(psiFile);
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        String tooltip;
        if (targets.size() == 1) {
            tooltip = FxToolsBundle.message("navigate.to.fxml.file");
        } else {
            tooltip = FxToolsBundle.message("navigate.to.fxml.files", targets.size());
        }

        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.FileTypes.Xml)
                .setTargets(targets)
                .setTooltipText(tooltip);
        result.add(builder.createLineMarkerInfo(element));
    }
}
