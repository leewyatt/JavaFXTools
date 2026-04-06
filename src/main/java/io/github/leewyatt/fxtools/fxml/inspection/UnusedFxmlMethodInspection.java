package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.index.FxControllerIndex;
import io.github.leewyatt.fxtools.fxml.index.FxEventHandlerIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Reports @FXML methods not referenced by any event handler in associated FXML files.
 */
public class UnusedFxmlMethodInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String FXML_ANNOTATION = "javafx.fxml.FXML";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                String fqn = aClass.getQualifiedName();
                if (fqn == null) {
                    return;
                }

                Project project = holder.getProject();
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
                Collection<VirtualFile> controllerFxmlFiles =
                        FxControllerIndex.findFxmlFilesForController(fqn, scope);
                if (controllerFxmlFiles.isEmpty()) {
                    return;
                }

                Set<VirtualFile> controllerFxmlSet = new HashSet<>(controllerFxmlFiles);

                for (PsiMethod method : aClass.getMethods()) {
                    if (method.getAnnotation(FXML_ANNOTATION) == null) {
                        continue;
                    }

                    String methodName = method.getName();

                    // Skip initialize() — auto-called by FXMLLoader
                    if ("initialize".equals(methodName)) {
                        continue;
                    }

                    Collection<VirtualFile> handlerFxmlFiles =
                            FxEventHandlerIndex.findFxmlFilesWithHandler(methodName, scope);

                    boolean referenced = false;
                    for (VirtualFile f : handlerFxmlFiles) {
                        if (controllerFxmlSet.contains(f)) {
                            referenced = true;
                            break;
                        }
                    }

                    if (!referenced) {
                        PsiIdentifier nameIdentifier = method.getNameIdentifier();
                        if (nameIdentifier != null) {
                            holder.registerProblem(nameIdentifier,
                                    FxToolsBundle.message("inspection.unused.fxml.method", methodName),
                                    ProblemHighlightType.WEAK_WARNING);
                        }
                    }
                }
            }
        };
    }
}
