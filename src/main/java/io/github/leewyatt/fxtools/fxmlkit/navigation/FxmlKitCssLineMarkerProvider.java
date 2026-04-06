package io.github.leewyatt.fxtools.fxmlkit.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.fxmlkit.FxmlKitDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a gutter icon on the first line of CSS/BSS files
 * for navigating to the associated FXML file.
 */
public class FxmlKitCssLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only process leaf elements at the very start of the file
        if (element.getTextRange().getStartOffset() != 0) {
            return null;
        }
        if (element.getFirstChild() != null) {
            return null;
        }

        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return null;
        }

        String ext = vFile.getExtension();
        if (!"css".equals(ext) && !"bss".equals(ext)) {
            return null;
        }

        Project project = element.getProject();
        if (!FxmlKitDetector.isFxmlKitProject(project)) {
            return null;
        }

        VirtualFile parent = vFile.getParent();
        if (parent == null) {
            return null;
        }

        String baseName = vFile.getNameWithoutExtension();
        VirtualFile fxmlFile = parent.findChild(baseName + ".fxml");
        if (fxmlFile == null) {
            return null;
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.FileTypes.Xml,
                e -> FxToolsBundle.message("navigate.to.fxml"),
                (mouseEvent, elt) -> FileEditorManager.getInstance(project).openFile(fxmlFile, true),
                GutterIconRenderer.Alignment.LEFT,
                () -> FxToolsBundle.message("navigate.to.fxml")
        );
    }
}
