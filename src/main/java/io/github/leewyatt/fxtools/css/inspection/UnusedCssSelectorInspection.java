package io.github.leewyatt.fxtools.css.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxDetector;
import io.github.leewyatt.fxtools.fxml.index.FxStyleClassIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports CSS class selectors not referenced by any FXML styleClass attribute.
 */
public class UnusedCssSelectorInspection extends LocalInspectionTool {

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SELECTOR_PATTERN = Pattern.compile("\\.([a-zA-Z][\\w-]*)");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        if (!FxDetector.isJavaFxProject(holder.getProject())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        return new PsiElementVisitor() {
            private boolean checked = false;

            @Override
            public void visitFile(@NotNull PsiFile psiFile) {
                if (checked) {
                    return;
                }
                checked = true;

                String text = psiFile.getText();
                String cleaned = COMMENT_PATTERN.matcher(text).replaceAll(
                        m -> " ".repeat(m.group().length()));

                GlobalSearchScope scope = GlobalSearchScope.projectScope(holder.getProject());
                Set<String> reported = new HashSet<>();

                Matcher matcher = SELECTOR_PATTERN.matcher(cleaned);
                while (matcher.find()) {
                    String className = matcher.group(1);
                    if (reported.contains(className)) {
                        continue;
                    }

                    int colonPos = cleaned.indexOf(':', matcher.end());
                    int bracePos = cleaned.indexOf('{', matcher.end());
                    int nextDot = cleaned.indexOf('.', matcher.end());
                    if (colonPos >= 0 && (bracePos < 0 || colonPos < bracePos)
                            && (nextDot < 0 || colonPos < nextDot)) {
                        if (colonPos == matcher.end()) {
                            continue;
                        }
                    }

                    Collection<VirtualFile> fxmlFiles =
                            FxStyleClassIndex.findFxmlFilesWithStyleClass(className, scope);
                    if (!fxmlFiles.isEmpty()) {
                        continue;
                    }

                    reported.add(className);
                    int start = matcher.start();
                    PsiElement elementAt = psiFile.findElementAt(start);
                    if (elementAt != null) {
                        holder.registerProblem(elementAt,
                                FxToolsBundle.message("inspection.unused.css.selector", className),
                                ProblemHighlightType.WEAK_WARNING);
                    }
                }
            }
        };
    }
}
