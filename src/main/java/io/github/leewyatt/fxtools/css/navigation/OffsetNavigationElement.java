package io.github.leewyatt.fxtools.css.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A lightweight navigatable element that targets a precise offset in a file.
 */
final class OffsetNavigationElement extends FakePsiElement {
    private final PsiFile file;
    private final int offset;
    private final String name;

    OffsetNavigationElement(@NotNull PsiFile file, int offset, @NotNull String name) {
        this.file = file;
        this.offset = offset;
        this.name = name;
    }

    @Override
    public @NotNull PsiElement getParent() {
        return file;
    }

    @Override
    public @NotNull TextRange getTextRange() {
        return TextRange.create(offset, offset + name.length());
    }

    @Override
    public int getTextOffset() {
        return offset;
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
            new OpenFileDescriptor(file.getProject(), vFile, offset).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return file.getVirtualFile() != null;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OffsetNavigationElement other)) {
            return false;
        }
        return offset == other.offset && Objects.equals(file, other.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, offset);
    }
}
