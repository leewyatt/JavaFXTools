package io.github.leewyatt.fxtools.fxml.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
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
 * Indexes fx:id attribute values and their element tag names in FXML files.
 */
public class FxIdIndex extends FileBasedIndexExtension<String, String> {

    public static final ID<String, String> NAME =
            ID.create("io.github.leewyatt.fxtools.FxIdIndex");

    private static final Pattern FX_ID_PATTERN =
            Pattern.compile("<([\\w.:]+)\\s[^>]*?\\bfx:id\\s*=\\s*\"([^\"]+)\"");

    /**
     * Finds all FXML files that contain the given fx:id.
     */
    public static Collection<VirtualFile> findFxmlFilesWithId(
            @NotNull String fxId, @NotNull GlobalSearchScope scope) {
        return FileBasedIndex.getInstance().getContainingFiles(NAME, fxId, scope);
    }

    /**
     * Gets all fx:id to element type mappings in the given file.
     */
    public static Map<String, String> getFxIdsInFile(
            @NotNull VirtualFile file, @NotNull Project project) {
        Map<String, String> result = new HashMap<>();
        FileBasedIndex instance = FileBasedIndex.getInstance();
        instance.processAllKeys(NAME, key -> {
            instance.processValues(NAME, key, file, (f, tagName) -> {
                result.put(key, tagName);
                return true;
            }, GlobalSearchScope.fileScope(project, file));
            return true;
        }, GlobalSearchScope.fileScope(project, file), null);
        return result;
    }

    @Override
    public @NotNull ID<String, String> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
        return inputData -> {
            String content = inputData.getContentAsText().toString();
            Matcher matcher = FX_ID_PATTERN.matcher(content);
            Map<String, String> result = new HashMap<>();
            while (matcher.find()) {
                result.put(matcher.group(2), matcher.group(1));
            }
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
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
