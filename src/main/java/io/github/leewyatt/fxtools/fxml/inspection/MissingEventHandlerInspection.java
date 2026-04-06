package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.quickfix.CreateEventHandlerQuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * Reports event handler references in FXML files whose method is missing in the controller.
 */
public class MissingEventHandlerInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (!(file instanceof XmlFile)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"fxml".equals(vFile.getExtension())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        XmlTag rootTag = ((XmlFile) file).getRootTag();
        if (rootTag == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        String controllerFqn = rootTag.getAttributeValue("fx:controller");
        if (controllerFqn == null || controllerFqn.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        PsiClass controllerClass = JavaPsiFacade.getInstance(holder.getProject())
                .findClass(controllerFqn, GlobalSearchScope.allScope(holder.getProject()));
        if (controllerClass == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        String simpleCtrlName = controllerClass.getName();
        if (simpleCtrlName == null) {
            simpleCtrlName = controllerFqn;
        }
        String ctrlName = simpleCtrlName;

        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                String attrName = attribute.getName();
                if (!attrName.startsWith("on") || attrName.length() <= 2) {
                    return;
                }

                String attrValue = attribute.getValue();
                if (attrValue == null || !attrValue.startsWith("#")) {
                    return;
                }

                String methodName = attrValue.substring(1);
                if (methodName.isEmpty()) {
                    return;
                }

                PsiMethod[] methods = controllerClass.findMethodsByName(methodName, true);
                if (methods.length > 0) {
                    return;
                }

                XmlAttributeValue valueElement = attribute.getValueElement();
                if (valueElement == null) {
                    return;
                }

                holder.registerProblem(valueElement,
                        FxToolsBundle.message("inspection.missing.event.handler", methodName, ctrlName),
                        ProblemHighlightType.WARNING,
                        new CreateEventHandlerQuickFix(controllerFqn, methodName, attrName));
            }
        };
    }
}
