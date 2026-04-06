package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Wraps a .properties PsiFile for navigation, displaying only the file name
 * without the full path in chooser popups.
 */
public final class BundleFileElement extends FakePsiElement {
    private final PsiFile file;

    public BundleFileElement(@NotNull PsiFile file) {
        this.file = file;
    }

    @Override
    public @NotNull PsiElement getParent() {
        return file;
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void navigate(boolean requestFocus) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
            new OpenFileDescriptor(file.getProject(), vFile).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return file.getVirtualFile() != null;
    }

    @Override
    public @NotNull String getName() {
        return file.getName();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @NotNull String getPresentableText() {
                return file.getName();
            }

            @Override
            public @Nullable String getLocationString() {
                return null;
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return file.getIcon(0);
            }
        };
    }

    @Override
    public String toString() {
        return file.getName();
    }
}
