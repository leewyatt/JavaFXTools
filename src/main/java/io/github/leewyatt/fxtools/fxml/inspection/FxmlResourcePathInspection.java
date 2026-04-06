package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports resource paths in FXML files that point to non-existent files.
 */
public class FxmlResourcePathInspection extends LocalInspectionTool {

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

        VirtualFile fxmlDir = vFile.getParent();
        if (fxmlDir == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new XmlElementVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
                String path = extractResourcePath(attribute);
                if (path == null) {
                    return;
                }

                String relativePath = path.startsWith("@") ? path.substring(1) : path;
                if (relativePath.isEmpty()) {
                    return;
                }

                VirtualFile target = fxmlDir.findFileByRelativePath(relativePath);
                if (target != null) {
                    return;
                }

                XmlAttributeValue valueElement = attribute.getValueElement();
                if (valueElement != null) {
                    holder.registerProblem(valueElement,
                            FxToolsBundle.message("inspection.fxml.resource.path", path),
                            ProblemHighlightType.ERROR);
                }
            }
        };
    }

    @Nullable
    private String extractResourcePath(@NotNull XmlAttribute attribute) {
        XmlTag tag = attribute.getParent();
        if (tag == null) {
            return null;
        }

        String tagName = tag.getName();
        String attrName = attribute.getName();
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return null;
        }

        // <Image url="@..."/>
        if ("Image".equals(tagName) && "url".equals(attrName) && value.startsWith("@")) {
            return value;
        }

        // <fx:include source="..."/>
        if ("fx:include".equals(tagName) && "source".equals(attrName)) {
            return value;
        }

        // <URL value="@..."/>
        if ("URL".equals(tagName) && "value".equals(attrName) && value.startsWith("@")) {
            return value;
        }

        return null;
    }
}
