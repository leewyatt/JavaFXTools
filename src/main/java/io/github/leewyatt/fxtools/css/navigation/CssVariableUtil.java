package io.github.leewyatt.fxtools.css.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utilities for CSS variable navigation.
 */
final class CssVariableUtil {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("-[\\w][\\w-]*");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private CssVariableUtil() {
    }

    /**
     * Extracts the CSS variable name at the given offset, only when used as a value reference.
     */
    @Nullable
    static String extractVariableAtOffset(@NotNull String text, int offset) {
        if (offset < 0 || offset >= text.length()) {
            return null;
        }

        int start = offset;
        while (start > 0 && isVariableChar(text.charAt(start - 1))) {
            start--;
        }

        int end = offset;
        while (end < text.length() && isVariableChar(text.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        String candidate = text.substring(start, end);
        Matcher m = VARIABLE_PATTERN.matcher(candidate);
        if (!m.matches()) {
            return null;
        }

        if (candidate.startsWith("-fx-")) {
            return null;
        }

        if (isDefinitionSite(text, start, candidate)) {
            return null;
        }

        return candidate;
    }

    /**
     * Resolves a CSS variable name to navigation targets at its definition sites.
     */
    @Nullable
    static PsiElement[] resolveTargets(@NotNull Project project, @NotNull String variableName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Collection<VirtualFile> defFiles = FxCssPropertyIndex.findFilesDefiningProperty(
                variableName, project, scope);
        if (defFiles.isEmpty()) {
            return null;
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiElement> targets = new ArrayList<>();

        for (VirtualFile defFile : defFiles) {
            PsiFile defPsiFile = psiManager.findFile(defFile);
            if (defPsiFile == null) {
                continue;
            }
            for (int defOffset : findDefinitionOffsets(defPsiFile.getText(), variableName)) {
                targets.add(new OffsetNavigationElement(defPsiFile, defOffset, variableName));
            }
        }

        return targets.isEmpty() ? null : targets.toArray(PsiElement.EMPTY_ARRAY);
    }

    /**
     * Finds all offsets where the given variable name is defined (left side of colon).
     */
    @NotNull
    static List<Integer> findDefinitionOffsets(@NotNull String text, @NotNull String variableName) {
        String cleaned = COMMENT_PATTERN.matcher(text).replaceAll(match -> " ".repeat(match.group().length()));
        List<Integer> offsets = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "(?:^|[;{}\\s])(" + Pattern.quote(variableName) + ")\\s*:", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(cleaned);
        while (matcher.find()) {
            offsets.add(matcher.start(1));
        }
        return offsets;
    }

    private static boolean isDefinitionSite(@NotNull String text, int varStart, @NotNull String varName) {
        int afterVar = varStart + varName.length();
        int i = afterVar;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i < text.length() && text.charAt(i) == ':';
    }

    private static boolean isVariableChar(char c) {
        return c == '-' || c == '_' || Character.isLetterOrDigit(c);
    }
}
