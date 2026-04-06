package io.github.leewyatt.fxtools.css.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.leewyatt.fxtools.util.FxDetector;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers auto-popup completion in .css files when typing '-' or ':'.
 * Required because Community Edition treats CSS as plain text and does not
 * auto-trigger completion for plain text files.
 */
public class FxCssTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project,
                                      @NotNull Editor editor, @NotNull PsiFile file) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return Result.CONTINUE;
        }
        if (!FxDetector.isJavaFxProject(project)) {
            return Result.CONTINUE;
        }
        if (c == '-' || c == ':') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        } else if (c == ' ') {
            // Trigger popup on space in value position (after colon)
            int offset = editor.getCaretModel().getOffset();
            if (offset >= 2) {
                CharSequence text = editor.getDocument().getCharsSequence();
                // Check if there's a colon before this space (possibly with the space we just typed)
                for (int i = offset - 2; i >= Math.max(0, offset - 3); i--) {
                    if (text.charAt(i) == ':') {
                        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
                        break;
                    }
                }
            }
        }
        return Result.CONTINUE;
    }
}
