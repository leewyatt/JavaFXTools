package io.github.leewyatt.fxtools.fxml.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes styleClass attribute values in FXML files.
 */
public class FxStyleClassIndex extends ScalarIndexExtension<String> {

    public static final ID<String, Void> NAME =
            ID.create("io.github.leewyatt.fxtools.FxStyleClassIndex");

    private static final Pattern STYLE_CLASS_PATTERN =
            Pattern.compile("\\bstyleClass\\s*=\\s*\"([^\"]+)\"");

    /**
     * Finds all FXML files that use the given style class.
     */
    public static Collection<VirtualFile> findFxmlFilesWithStyleClass(
            @NotNull String styleClass, @NotNull GlobalSearchScope scope) {
        return FileBasedIndex.getInstance().getContainingFiles(NAME, styleClass, scope);
    }

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            String content = inputData.getContentAsText().toString();
            Matcher matcher = STYLE_CLASS_PATTERN.matcher(content);
            Map<String, Void> result = new HashMap<>();
            while (matcher.find()) {
                String value = matcher.group(1);
                for (String cls : value.split("[,\\s]+")) {
                    String trimmed = cls.trim();
                    if (!trimmed.isEmpty()) {
                        result.put(trimmed, null);
                    }
                }
            }
            return result.isEmpty() ? Collections.emptyMap() : result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
        return file -> "fxml".equals(file.getExtension());
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
