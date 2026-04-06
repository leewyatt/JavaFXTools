package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.quickfix.CreateFxmlFieldQuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * Reports fx:id attributes in FXML files whose @FXML field is missing in the controller.
 */
public class MissingFxIdFieldInspection extends LocalInspectionTool {

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
                if (!"fx:id".equals(attribute.getName())) {
                    return;
                }

                String fxId = attribute.getValue();
                if (fxId == null || fxId.isEmpty()) {
                    return;
                }

                PsiField field = controllerClass.findFieldByName(fxId, true);
                if (field != null) {
                    return;
                }

                XmlAttributeValue valueElement = attribute.getValueElement();
                if (valueElement == null) {
                    return;
                }

                XmlTag parentTag = attribute.getParent();
                String tagName = parentTag != null ? parentTag.getName() : "Node";

                holder.registerProblem(valueElement,
                        FxToolsBundle.message("inspection.missing.fxid.field", fxId, ctrlName),
                        ProblemHighlightType.WARNING,
                        new CreateFxmlFieldQuickFix(controllerFqn, fxId, tagName, vFile));
            }
        };
    }
}
