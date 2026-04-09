package io.github.leewyatt.fxtools.css.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides Ctrl+Click navigation from CSS variable references to their definitions.
 */
public class CssVariableGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                              int offset,
                                                              @NotNull Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        PsiFile file = sourceElement.getContainingFile();
        if (file == null) {
            return null;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null || !"css".equals(vFile.getExtension())) {
            return null;
        }

        String variableName = CssVariableUtil.extractVariableAtOffset(file.getText(), offset);
        if (variableName == null) {
            return null;
        }

        return CssVariableUtil.resolveTargets(sourceElement.getProject(), variableName);
    }
}
