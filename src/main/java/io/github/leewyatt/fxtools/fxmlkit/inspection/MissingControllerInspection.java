package io.github.leewyatt.fxtools.fxmlkit.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.fxmlkit.quickfix.CreateControllerQuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * Reports fx:controller attributes in FXML files that reference a non-existent class.
 */
public class MissingControllerInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!(file instanceof XmlFile)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        if (file.getVirtualFile() == null
                || !"fxml".equals(file.getVirtualFile().getExtension())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        if (!FxmlKitDetector.isFxmlKitProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                if (!"fx:controller".equals(attribute.getName())) {
                    return;
                }

                XmlTag tag = attribute.getParent();
                if (tag == null || tag.getParentTag() != null) {
                    return;
                }

                String controllerFqn = attribute.getValue();
                if (controllerFqn == null || controllerFqn.isEmpty()) {
                    return;
                }

                PsiClass controllerClass = JavaPsiFacade.getInstance(holder.getProject())
                        .findClass(controllerFqn, GlobalSearchScope.allScope(holder.getProject()));

                if (controllerClass == null) {
                    XmlAttributeValue valueElement = attribute.getValueElement();
                    if (valueElement != null) {
                        holder.registerProblem(valueElement,
                                FxToolsBundle.message("inspection.fxmlkit.missing.controller",
                                        controllerFqn),
                                ProblemHighlightType.ERROR,
                                new CreateControllerQuickFix(controllerFqn));
                    }
                }
            }
        };
    }
}
