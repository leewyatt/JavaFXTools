package io.github.leewyatt.fxtools.fxmlkit.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import io.github.leewyatt.fxtools.fxmlkit.quickfix.CreateCssQuickFix;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Reports FxmlView/FxmlViewProvider subclasses that have a FXML file but no CSS file.
 */
public class MissingCssInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!FxmlKitDetector.isFxmlKitProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                if (!FxFileResolver.isFxmlKitViewClass(aClass)) {
                    return;
                }

                VirtualFile fxmlFile = FxFileResolver.findFxmlFile(aClass);
                if (fxmlFile == null) {
                    return;
                }

                if (FxFileResolver.findCssFile(fxmlFile) != null) {
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

                String cssBaseName = fxmlFile.getNameWithoutExtension();
                holder.registerProblem(nameIdentifier,
                        FxToolsBundle.message("inspection.fxmlkit.missing.css", className),
                        ProblemHighlightType.WEAK_WARNING,
                        new CreateCssQuickFix(cssBaseName));
            }
        };
    }
}
