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
 * Indexes event handler method references (#methodName) in FXML files.
 */
public class FxEventHandlerIndex extends ScalarIndexExtension<String> {

    public static final ID<String, Void> NAME =
            ID.create("io.github.leewyatt.fxtools.FxEventHandlerIndex");

    private static final Pattern HANDLER_PATTERN =
            Pattern.compile("\\bon\\w+\\s*=\\s*\"#([^\"]+)\"");

    /**
     * Finds all FXML files that reference the given event handler method name.
     */
    public static Collection<VirtualFile> findFxmlFilesWithHandler(
            @NotNull String methodName, @NotNull GlobalSearchScope scope) {
        return FileBasedIndex.getInstance().getContainingFiles(NAME, methodName, scope);
    }

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            String content = inputData.getContentAsText().toString();
            Matcher matcher = HANDLER_PATTERN.matcher(content);
            Map<String, Void> result = new HashMap<>();
            while (matcher.find()) {
                result.put(matcher.group(1), null);
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
