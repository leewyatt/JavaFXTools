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
import io.github.leewyatt.fxtools.fxml.index.FxIdIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * Reports @FXML fields not referenced by any fx:id in associated FXML files.
 */
public class UnusedFxmlFieldInspection extends AbstractBaseJavaLocalInspectionTool {

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
                Collection<VirtualFile> fxmlFiles = FxControllerIndex.findFxmlFilesForController(fqn, scope);
                if (fxmlFiles.isEmpty()) {
                    return;
                }

                for (PsiField field : aClass.getFields()) {
                    if (field.getAnnotation(FXML_ANNOTATION) == null) {
                        continue;
                    }

                    String fieldName = field.getName();
                    boolean referenced = false;

                    for (VirtualFile fxmlFile : fxmlFiles) {
                        Map<String, String> fxIds = FxIdIndex.getFxIdsInFile(fxmlFile, project);
                        if (fxIds.containsKey(fieldName)) {
                            referenced = true;
                            break;
                        }
                    }

                    if (!referenced) {
                        PsiIdentifier nameIdentifier = field.getNameIdentifier();
                        holder.registerProblem(nameIdentifier,
                                FxToolsBundle.message("inspection.unused.fxml.field", fieldName),
                                ProblemHighlightType.WEAK_WARNING);
                    }
                }
            }
        };
    }
}
