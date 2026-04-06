package io.github.leewyatt.fxtools.fxmlkit.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection that validates @FxmlPath annotation values resolve to existing FXML files.
 */
public class FxmlPathInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String FXML_PATH_ANNOTATION = "com.dlsc.fxmlkit.annotations.FxmlPath";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!FxmlKitDetector.isFxmlKitProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!FXML_PATH_ANNOTATION.equals(annotation.getQualifiedName())) {
                    return;
                }

                PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
                if (!(value instanceof PsiLiteralExpression)) {
                    return;
                }

                Object literalValue = ((PsiLiteralExpression) value).getValue();
                if (!(literalValue instanceof String)) {
                    return;
                }

                String path = (String) literalValue;
                if (path.isEmpty()) {
                    return;
                }

                PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
                if (containingClass == null) {
                    return;
                }

                VirtualFile resolved = FxFileResolver.resolveFxmlPath(containingClass, path);
                if (resolved == null) {
                    holder.registerProblem(value,
                            FxToolsBundle.message("inspection.fxmlkit.fxml.path", path),
                            ProblemHighlightType.ERROR);
                }
            }
        };
    }
}
