package io.github.leewyatt.fxtools.fxmlkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides gutter icons on the root element of FXML files
 * for navigating to the associated View class and CSS file.
 */
public class FxmlKitFxmlLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof XmlToken)) {
            return;
        }

        XmlToken token = (XmlToken) element;
        if (token.getTokenType() != XmlTokenType.XML_NAME) {
            return;
        }

        PsiElement parent = token.getParent();
        if (!(parent instanceof XmlTag)) {
            return;
        }

        XmlTag tag = (XmlTag) parent;

        // Only the root tag
        if (tag.getParentTag() != null) {
            return;
        }

        // Only the opening tag name (previous sibling is "<")
        PsiElement prev = element.getPrevSibling();
        if (!(prev instanceof XmlToken)
                || ((XmlToken) prev).getTokenType() != XmlTokenType.XML_START_TAG_START) {
            return;
        }

        PsiFile file = element.getContainingFile();
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"fxml".equals(vFile.getExtension())) {
            return;
        }

        Project project = element.getProject();
        if (!FxmlKitDetector.isFxmlKitProject(project)) {
            return;
        }

        // Navigate to View class
        PsiClass viewClass = FxFileResolver.findViewClassForFxml(file);
        if (viewClass != null) {
            NavigationGutterIconBuilder<PsiElement> viewBuilder = NavigationGutterIconBuilder
                    .create(AllIcons.Nodes.Class)
                    .setTarget(viewClass)
                    .setTooltipText(FxToolsBundle.message("navigate.to.view"));
            result.add(viewBuilder.createLineMarkerInfo(element));
        }

        // Navigate to CSS
        VirtualFile cssFile = FxFileResolver.findCssFile(vFile);
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
