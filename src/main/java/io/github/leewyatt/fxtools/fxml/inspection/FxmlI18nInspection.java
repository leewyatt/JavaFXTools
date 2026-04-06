package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Reports %key references in FXML files where the key is not found in any .properties file.
 */
public class FxmlI18nInspection extends LocalInspectionTool {

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

        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                String attrName = attribute.getName();
                if (attrName.startsWith("fx:") || attrName.startsWith("xmlns")) {
                    return;
                }

                String value = attribute.getValue();
                if (value == null || !value.startsWith("%") || value.length() <= 1) {
                    return;
                }

                String key = value.substring(1);
                if (isKeyInAnyProperties(key, holder.getProject())) {
                    return;
                }

                XmlAttributeValue valueElement = attribute.getValueElement();
                if (valueElement != null) {
                    holder.registerProblem(valueElement,
                            FxToolsBundle.message("inspection.fxml.i18n.key", key),
                            ProblemHighlightType.WARNING);
                }
            }
        };
    }

    private boolean isKeyInAnyProperties(@NotNull String key, @NotNull Project project) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "properties", scope);

        for (VirtualFile f : files) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(f);
            if (psiFile instanceof PropertiesFile) {
                IProperty property = ((PropertiesFile) psiFile).findPropertyByKey(key);
                if (property != null) {
                    return true;
                }
            }
        }

        return false;
    }
}
