package io.github.leewyatt.fxtools.css.index;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import io.github.leewyatt.fxtools.css.FxCssModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes custom CSS property definitions in .css files.
 * Provides a direct-scan fallback when the index returns empty results.
 */
public class FxCssPropertyIndex extends FileBasedIndexExtension<String, String> {

    public static final ID<String, String> NAME =
            ID.create("io.github.leewyatt.fxtools.FxCssPropertyIndex");

    private static final Key<CachedValue<Map<String, List<String>>>> VAR_CACHE_KEY =
            Key.create("fx.css.variable.cache");

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("(-[\\w][\\w-]*)\\s*:\\s*([^;{}]+?)\\s*;");

    /**
     * Returns a cached snapshot of all CSS variables defined in the project.
     * Key = variable name, Value = list of raw values across all files.
     *
     * <p>In dumb mode (indexing in progress), returns a direct scan result
     * without caching, to avoid persisting incomplete data.</p>
     *
     * <p>In normal mode, returns a CachedValue that auto-invalidates when
     * any CSS file is created, modified, or deleted.</p>
     */
    @NotNull
    public static Map<String, List<String>> getAllVariables(@NotNull Project project) {
        if (DumbService.isDumb(project)) {
            return buildVariableMap(project, true);
        }
        return CachedValuesManager.getManager(project).getCachedValue(
                project, VAR_CACHE_KEY, () -> {
                    Map<String, List<String>> map = buildVariableMap(project, false);
                    return CachedValueProvider.Result.create(
                            map,
                            PsiModificationTracker.getInstance(project),
                            FxCssModificationTracker.getInstance(project));
                }, false);
    }

    @NotNull
    private static Map<String, List<String>> buildVariableMap(
            @NotNull Project project, boolean forceScan) {
        Map<String, List<String>> map = new HashMap<>();
        if (!forceScan) {
            // Use projectScope to only include CSS files in the project source,
            // not library JARs (e.g., modena.css inside javafx.controls)
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            FileBasedIndex index = FileBasedIndex.getInstance();
            index.processAllKeys(NAME, key -> {
                List<String> values = index.getValues(NAME, key, scope);
                if (!values.isEmpty()) {
                    map.put(key, values);
                }
                return true;
            }, scope, null);
        }
        if (map.isEmpty()) {
            for (Map.Entry<String, String> entry : scanAllProperties(project).entrySet()) {
                List<String> list = new ArrayList<>();
                for (String part : entry.getValue().split("\n")) {
                    if (!part.isEmpty()) {
                        list.add(part);
                    }
                }
                map.put(entry.getKey(), list);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Finds all CSS files that define the given property name.
     */
    public static Collection<VirtualFile> findFilesDefiningProperty(
            @NotNull String propertyName, @NotNull GlobalSearchScope scope) {
        return FileBasedIndex.getInstance().getContainingFiles(NAME, propertyName, scope);
    }

    /**
     * Finds all CSS files that define the given property name.
     * Falls back to direct file scanning if the index returns empty.
     */
    public static Collection<VirtualFile> findFilesDefiningProperty(
            @NotNull String propertyName, @NotNull Project project,
            @NotNull GlobalSearchScope scope) {
        Collection<VirtualFile> files = FileBasedIndex.getInstance()
                .getContainingFiles(NAME, propertyName, scope);
        if (!files.isEmpty()) {
            return files;
        }
        return scanFilesDefiningProperty(propertyName, project);
    }

    @NotNull
    private static Collection<VirtualFile> scanFilesDefiningProperty(
            @NotNull String propertyName, @NotNull Project project) {
        Set<VirtualFile> result = new HashSet<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (!"css".equals(file.getExtension())) {
                return true;
            }
            try {
                String content = new String(file.contentsToByteArray(), file.getCharset());
                String cleaned = COMMENT_PATTERN.matcher(content).replaceAll("");
                Matcher matcher = PROPERTY_PATTERN.matcher(cleaned);
                while (matcher.find()) {
                    if (propertyName.equals(matcher.group(1))) {
                        result.add(file);
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
            return true;
        });
        return result;
    }

    /**
     * Gets the value(s) of a property in a specific file.
     */
    @Nullable
    public static String getPropertyValue(@NotNull String propertyName,
                                          @NotNull VirtualFile file,
                                          @NotNull Project project) {
        List<String> values = new ArrayList<>();
        FileBasedIndex.getInstance().processValues(
                NAME, propertyName, file,
                (f, value) -> {
                    values.add(value);
                    return true;
                },
                GlobalSearchScope.fileScope(project, file)
        );
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Gets all values of a property across the project.
     * Falls back to direct file scanning if the index returns empty.
     */
    @NotNull
    public static List<String> getPropertyValues(@NotNull String propertyName,
                                                 @NotNull Project project,
                                                 @NotNull GlobalSearchScope scope) {
        List<String> values = FileBasedIndex.getInstance().getValues(NAME, propertyName, scope);
        if (!values.isEmpty()) {
            return values;
        }
        return scanPropertyValues(propertyName, project);
    }

    /**
     * Gets all custom property names defined across the project.
     * Falls back to direct file scanning if the index returns empty.
     */
    public static Collection<String> getAllPropertyNames(@NotNull Project project,
                                                         @NotNull GlobalSearchScope scope) {
        Set<String> keys = new HashSet<>();
        FileBasedIndex.getInstance().processAllKeys(NAME, key -> {
            keys.add(key);
            return true;
        }, scope, null);
        if (!keys.isEmpty()) {
            return keys;
        }
        return scanAllPropertyNames(project);
    }

    /**
     * Direct scan fallback: reads all .css files in the project and extracts property values.
     */
    @NotNull
    private static List<String> scanPropertyValues(@NotNull String propertyName,
                                                    @NotNull Project project) {
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : scanAllProperties(project).entrySet()) {
            if (propertyName.equals(entry.getKey())) {
                results.add(entry.getValue());
            }
        }
        return results;
    }

    /**
     * Direct scan fallback: reads all .css files and extracts all property names.
     */
    @NotNull
    private static Collection<String> scanAllPropertyNames(@NotNull Project project) {
        return scanAllProperties(project).keySet();
    }

    @NotNull
    private static Map<String, String> scanAllProperties(@NotNull Project project) {
        Map<String, String> result = new HashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (!"css".equals(file.getExtension())) {
                return true;
            }
            try {
                String content = new String(file.contentsToByteArray(), file.getCharset());
                String cleaned = COMMENT_PATTERN.matcher(content).replaceAll("");
                Matcher matcher = PROPERTY_PATTERN.matcher(cleaned);
                while (matcher.find()) {
                    String propName = matcher.group(1);
                    String propValue = matcher.group(2).trim();
                    if (result.containsKey(propName)) {
                        result.put(propName, result.get(propName) + "\n" + propValue);
                    } else {
                        result.put(propName, propValue);
                    }
                }
            } catch (IOException ignored) {
            }
            return true;
        });
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
            String cleaned = COMMENT_PATTERN.matcher(content).replaceAll("");
            Matcher matcher = PROPERTY_PATTERN.matcher(cleaned);
            Map<String, String> result = new HashMap<>();
            while (matcher.find()) {
                String propName = matcher.group(1);
                String propValue = matcher.group(2).trim();
                if (result.containsKey(propName)) {
                    result.put(propName, result.get(propName) + "\n" + propValue);
                } else {
                    result.put(propName, propValue);
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
    public @NotNull DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
        return file -> {
            String ext = file.getExtension();
            return "css".equals(ext);
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
