package io.github.leewyatt.fxtools.fxmlkit.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Reports FxmlViewProvider subclasses that use a misleading 'View' suffix.
 */
public class NamingConventionInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!FxmlKitDetector.isFxmlKitProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                if (!FxFileResolver.isFxmlViewProvider(aClass)) {
                    return;
                }

                String className = aClass.getName();
                if (className == null) {
                    return;
                }

                if (className.endsWith("View") && !className.endsWith("ViewProvider")) {
                    PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
                    if (nameIdentifier != null) {
                        holder.registerProblem(nameIdentifier,
                                FxToolsBundle.message("inspection.fxmlkit.naming.convention",
                                        className, "View"),
                                ProblemHighlightType.WARNING);
                    }
                }
            }
        };
    }
}
