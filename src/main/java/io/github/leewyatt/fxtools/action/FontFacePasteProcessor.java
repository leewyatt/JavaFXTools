package io.github.leewyatt.fxtools.action;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts paste operations in CSS files to resolve font file paths.
 * When @font-face blocks (copied by {@link CopyFontFaceCssAction}) are pasted into a CSS file,
 * this processor replaces the bare file name in src: url("...") with the correct relative path.
 */
public class FontFacePasteProcessor implements CopyPastePreProcessor {

    private static final Pattern SRC_URL_PATTERN =
            Pattern.compile("(src:\\s*url\\(\")([^\"]+)(\"\\))");

    @Nullable
    @Override
    public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
        return null;
    }

    @NotNull
    @Override
    public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text,
                                     RawText rawText) {
        if (!isCssFile(file) || !text.contains("@font-face")) {
            return text;
        }

        VirtualFile cssVFile = file.getVirtualFile();
        if (cssVFile == null || cssVFile.getParent() == null) {
            return text;
        }
        String cssDir = cssVFile.getParent().getPath();

        Matcher matcher = SRC_URL_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String fileName = matcher.group(2);
            String fontAbsPath = FontFaceClipboard.getPath(fileName);
            if (fontAbsPath != null) {
                String relativePath = computeRelativePath(cssDir, fontAbsPath);
                result.append(text, lastEnd, matcher.start());
                result.append(matcher.group(1)).append(relativePath).append(matcher.group(3));
            } else {
                result.append(text, lastEnd, matcher.end());
            }
            lastEnd = matcher.end();
        }

        if (lastEnd == 0) {
            return text;
        }

        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    @NotNull
    private static String computeRelativePath(@NotNull String cssDir, @NotNull String fontAbsPath) {
        Path cssPath = Paths.get(cssDir);
        Path fontPath = Paths.get(fontAbsPath);
        String relative = cssPath.relativize(fontPath).toString();
        // Use forward slashes for CSS URLs
        return relative.replace('\\', '/');
    }

    private static boolean isCssFile(@NotNull PsiFile file) {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        String ext = vFile.getExtension();
        return "css".equals(ext);
    }
}
