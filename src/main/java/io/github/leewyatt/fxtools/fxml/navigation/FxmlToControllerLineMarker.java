package io.github.leewyatt.fxtools.fxml.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides a gutter icon on fx:controller attribute values in FXML files
 * for navigating to the Controller class. Works in all JavaFX projects.
 */
public class FxmlToControllerLineMarker extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof XmlToken)) {
            return;
        }

        XmlToken token = (XmlToken) element;
        if (token.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return;
        }

        PsiElement parent = token.getParent();
        if (!(parent instanceof XmlAttributeValue)) {
            return;
        }

        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof XmlAttribute)) {
            return;
        }

        XmlAttribute attr = (XmlAttribute) grandParent;
        if (!"fx:controller".equals(attr.getName())) {
            return;
        }

        XmlTag tag = attr.getParent();
        if (tag == null || tag.getParentTag() != null) {
            return;
        }

        VirtualFile vFile = element.getContainingFile().getVirtualFile();
        if (vFile == null || !"fxml".equals(vFile.getExtension())) {
            return;
        }

        String controllerFqn = attr.getValue();
        if (controllerFqn == null || controllerFqn.isEmpty()) {
            return;
        }

        PsiClass controllerClass = JavaPsiFacade.getInstance(element.getProject())
                .findClass(controllerFqn, GlobalSearchScope.allScope(element.getProject()));
        if (controllerClass == null) {
            return;
        }

        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.Nodes.Class)
                .setTarget(controllerClass)
                .setTooltipText(FxToolsBundle.message("navigate.to.controller"));
        result.add(builder.createLineMarkerInfo(element));
    }
}
