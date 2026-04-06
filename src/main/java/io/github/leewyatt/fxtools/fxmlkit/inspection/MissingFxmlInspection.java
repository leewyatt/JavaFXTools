package io.github.leewyatt.fxtools.fxmlkit.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.fxmlkit.quickfix.CreateFxmlQuickFix;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Reports FxmlView/FxmlViewProvider subclasses that have no corresponding FXML file.
 */
public class MissingFxmlInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String FXML_PATH_ANNOTATION = "com.dlsc.fxmlkit.annotations.FxmlPath";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!FxmlKitDetector.isFxmlKitProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                if (aClass.getAnnotation(FXML_PATH_ANNOTATION) != null) {
                    return;
                }
                if (!FxFileResolver.isFxmlKitViewClass(aClass)) {
                    return;
                }
                if (FxFileResolver.findFxmlFile(aClass) != null) {
                    return;
                }

                String className = aClass.getName();
                if (className == null) {
                    return;
                }

                PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
                if (nameIdentifier == null) {
                    return;
                }

                holder.registerProblem(nameIdentifier,
                        FxToolsBundle.message("inspection.fxmlkit.missing.fxml",
                                className + ".fxml", className),
                        ProblemHighlightType.ERROR,
                        new CreateFxmlQuickFix(className));
            }
        };
    }
}
