package io.github.leewyatt.fxtools.fxml.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxml.index.FxControllerIndex;
import io.github.leewyatt.fxtools.fxml.index.FxIdIndex;
import io.github.leewyatt.fxtools.fxml.quickfix.ChangeFieldTypeQuickFix;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * Reports @FXML fields whose declared type does not match the FXML element type.
 */
public class FxmlFieldTypeMismatchInspection extends AbstractBaseJavaLocalInspectionTool {

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
                    checkFieldType(field, fxmlFiles, project, holder);
                }
            }
        };
    }

    private void checkFieldType(@NotNull PsiField field,
                                @NotNull Collection<VirtualFile> fxmlFiles,
                                @NotNull Project project,
                                @NotNull ProblemsHolder holder) {
        String fieldName = field.getName();
        PsiType fieldType = field.getType();

        String resolvedTagFqn = null;
        boolean conflicting = false;

        for (VirtualFile fxmlFile : fxmlFiles) {
            Map<String, String> fxIds = FxIdIndex.getFxIdsInFile(fxmlFile, project);
            String tagName = fxIds.get(fieldName);
            if (tagName == null) {
                continue;
            }

            String tagFqn = FxFileResolver.resolveTagNameToFqn(tagName, fxmlFile, project);
            if (tagFqn == null) {
                return;
            }

            if (resolvedTagFqn == null) {
                resolvedTagFqn = tagFqn;
            } else if (!resolvedTagFqn.equals(tagFqn)) {
                conflicting = true;
                break;
            }
        }

        if (resolvedTagFqn == null || conflicting) {
            return;
        }

        PsiClass fxmlElementClass = JavaPsiFacade.getInstance(project)
                .findClass(resolvedTagFqn, GlobalSearchScope.allScope(project));
        if (fxmlElementClass == null) {
            return;
        }

        PsiClass fieldTypeClass = com.intellij.psi.util.PsiUtil.resolveClassInType(fieldType);
        if (fieldTypeClass == null) {
            return;
        }

        if (InheritanceUtil.isInheritorOrSelf(fxmlElementClass, fieldTypeClass, true)) {
            return;
        }

        String fieldTypeSimple = fieldTypeClass.getName();
        String fxmlTypeSimple = fxmlElementClass.getName();
        PsiIdentifier nameIdentifier = field.getNameIdentifier();

        holder.registerProblem(nameIdentifier,
                FxToolsBundle.message("inspection.fxml.field.type.mismatch",
                        fieldName, fxmlTypeSimple, fieldTypeSimple),
                ProblemHighlightType.WARNING,
                new ChangeFieldTypeQuickFix(resolvedTagFqn));
    }
}
