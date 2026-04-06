package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for ResourceBundle.getBundle() detection and .properties file resolution.
 */
public final class ResourceBundleUtil {

    private ResourceBundleUtil() {
    }

    /**
     * Checks if a method call is {@code ResourceBundle.getBundle(...)} and the given expression
     * is its first argument.
     */
    public static boolean isGetBundleCall(@NotNull PsiMethodCallExpression call,
                                   @NotNull PsiExpression firstArg) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0 || args[0] != firstArg) {
            return false;
        }
        PsiReferenceExpression methodExpr = call.getMethodExpression();
        if (!"getBundle".equals(methodExpr.getReferenceName())) {
            return false;
        }
        PsiExpression qualifier = methodExpr.getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression ref) {
            return "ResourceBundle".equals(ref.getReferenceName());
        }
        return false;
    }

    /**
     * Extracts the bundle name from a {@code ResourceBundle.getBundle(...)} call.
     * Supports string literals and constant/field references.
     *
     * @return the bundle name string, or null if not resolvable
     */
    public static @Nullable String extractBundleName(@NotNull PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) {
            return null;
        }
        return resolveStringValue(args[0]);
    }

    /**
     * Resolves a PSI expression to its string value.
     * Supports string literals and references to constant fields.
     */
    public static @Nullable String resolveStringValue(@NotNull PsiExpression expr) {
        // Direct string literal
        if (expr instanceof PsiLiteralExpression literal) {
            Object value = literal.getValue();
            return value instanceof String s ? s : null;
        }
        // Reference to a constant field
        if (expr instanceof PsiReferenceExpression ref) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiField field) {
                PsiExpression initializer = field.getInitializer();
                if (initializer instanceof PsiLiteralExpression literal) {
                    Object value = literal.getValue();
                    return value instanceof String s ? s : null;
                }
            }
        }
        return null;
    }

    /**
     * Finds all .properties files matching the given bundle name.
     *
     * @param bundleName dot-separated bundle name (e.g., "com.example.words")
     * @return list of matching PsiFile instances (base + locale variants)
     */
    public static @NotNull List<PsiFile> findBundleFiles(@NotNull Project project,
                                                   @NotNull String bundleName) {
        String basePath = bundleName.replace('.', '/');
        String baseFileName = basePath.contains("/")
                ? basePath.substring(basePath.lastIndexOf('/') + 1)
                : basePath;
        String dirPath = basePath.contains("/")
                ? basePath.substring(0, basePath.lastIndexOf('/'))
                : "";

        List<PsiFile> result = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (file.isDirectory() || !"properties".equals(file.getExtension())) {
                return true;
            }
            String fileName = file.getNameWithoutExtension();
            if (!fileName.equals(baseFileName) && !fileName.startsWith(baseFileName + "_")) {
                return true;
            }
            if (!matchesPath(file, dirPath)) {
                return true;
            }
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
                result.add(psiFile);
            }
            return true;
        });

        return result;
    }

    private static boolean matchesPath(@NotNull VirtualFile file, @NotNull String dirPath) {
        if (dirPath.isEmpty()) {
            return true;
        }
        VirtualFile parent = file.getParent();
        if (parent == null) {
            return false;
        }
        return parent.getPath().endsWith(dirPath);
    }
}
