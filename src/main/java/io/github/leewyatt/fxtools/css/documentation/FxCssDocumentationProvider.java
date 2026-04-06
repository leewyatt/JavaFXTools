package io.github.leewyatt.fxtools.css.documentation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import io.github.leewyatt.fxtools.util.FxDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Provides Quick Documentation for {@code -fx-*} CSS properties and custom CSS
 * variables in JavaFX projects.
 *
 * <p>Registered under the modern {@code platform.backend.documentation.targetProvider}
 * extension point. The {@code documentationTargets(PsiFile, offset)} signature gives
 * us the cursor offset directly, which is exactly what we need for the Community
 * Edition case where {@code .css} files are a single {@code PsiPlainText} element
 * and the token must be extracted by offset rather than via PSI navigation.</p>
 */
public final class FxCssDocumentationProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(
            @NotNull PsiFile file, int offset) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return Collections.emptyList();
        }
        Project project = file.getProject();
        if (!FxDetector.isJavaFxProject(project)) {
            return Collections.emptyList();
        }

        Document document = FileDocumentManager.getInstance().getDocument(vFile);
        if (document == null) {
            return Collections.emptyList();
        }

        String token = extractTokenAtOffset(document, offset);
        if (token == null || !(token.startsWith("-") || token.startsWith("transition"))) {
            return Collections.emptyList();
        }

        return List.of(new FxCssDocumentationTarget(project, token));
    }

    // ==================== Token extraction ====================

    @Nullable
    private static String extractTokenAtOffset(@NotNull Document document, int offset) {
        String text = document.getText();
        if (offset < 0 || offset >= text.length()) {
            return null;
        }

        int start = offset;
        while (start > 0 && isTokenChar(text.charAt(start - 1))) {
            start--;
        }

        int end = offset;
        while (end < text.length() && isTokenChar(text.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        return text.substring(start, end);
    }

    private static boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}
