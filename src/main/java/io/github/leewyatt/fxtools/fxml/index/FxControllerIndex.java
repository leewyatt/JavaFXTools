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
 * Indexes fx:controller attribute values in FXML files.
 */
public class FxControllerIndex extends ScalarIndexExtension<String> {

    public static final ID<String, Void> NAME =
            ID.create("io.github.leewyatt.fxtools.FxControllerIndex");

    private static final Pattern CONTROLLER_PATTERN =
            Pattern.compile("fx:controller\\s*=\\s*\"([^\"]+)\"");

    /**
     * Finds all FXML files that reference the given controller class.
     */
    public static Collection<VirtualFile> findFxmlFilesForController(
            @NotNull String controllerFqn, @NotNull GlobalSearchScope scope) {
        return FileBasedIndex.getInstance().getContainingFiles(NAME, controllerFqn, scope);
    }

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return inputData -> {
            String content = inputData.getContentAsText().toString();
            Matcher matcher = CONTROLLER_PATTERN.matcher(content);
            if (matcher.find()) {
                Map<String, Void> result = new HashMap<>();
                result.put(matcher.group(1), null);
                return result;
            }
            return Collections.emptyMap();
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
