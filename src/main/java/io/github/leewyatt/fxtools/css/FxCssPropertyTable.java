package io.github.leewyatt.fxtools.css;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and caches CSS property definitions from all JSON files under /data/css/.
 * Properties are cached per project — only files whose library marker class is on the
 * project classpath are loaded.
 */
public final class FxCssPropertyTable {

    private static final Logger LOG = Logger.getInstance(FxCssPropertyTable.class);
    private static final String CSS_DATA_DIR = "/data/css/";
    private static final String CSS_DATA_INDEX = "/data/css/index.txt";

    private static final Map<Project, Map<String, PropertyInfo>> CACHE = new WeakHashMap<>();

    private FxCssPropertyTable() {
    }

    // ==================== PropertyInfo ====================

    /**
     * A source entry with full property definition from one library.
     */
    public static final class SourceEntry {
        private final String valueType;
        private final @Nullable List<String> values;
        private final String defaultValue;
        private final String appliesTo;
        private final String description;
        private final String example;
        private final @Nullable String library;

        SourceEntry(String valueType, @Nullable List<String> values, String defaultValue,
                    String appliesTo, String description, String example,
                    @Nullable String library) {
            this.valueType = valueType;
            this.values = values;
            this.defaultValue = defaultValue;
            this.appliesTo = appliesTo;
            this.description = description;
            this.example = example;
            this.library = library;
        }

        public String getValueType() { return valueType; }

        @Nullable
        public List<String> getValues() { return values; }

        public String getDefaultValue() { return defaultValue; }
        public String getAppliesTo() { return appliesTo; }
        public String getDescription() { return description; }
        public String getExample() { return example; }

        @Nullable
        public String getLibrary() { return library; }
    }

    /**
     * Aggregates one or more SourceEntry instances for a CSS property name.
     * The first source is the primary (used for completion type info).
     */
    public static final class PropertyInfo {
        private final String name;
        private final List<SourceEntry> sources;

        PropertyInfo(@NotNull String name, @NotNull SourceEntry firstSource) {
            this.name = name;
            this.sources = new ArrayList<>();
            this.sources.add(firstSource);
        }

        public String getName() { return name; }

        /** All sources. First entry is the primary source. */
        @NotNull
        public List<SourceEntry> getSources() { return sources; }

        // ---- Convenience delegates to the first (primary) source ----

        public String getValueType() { return sources.get(0).getValueType(); }

        @Nullable
        public List<String> getValues() { return sources.get(0).getValues(); }

        public String getDefaultValue() { return sources.get(0).getDefaultValue(); }
        public String getAppliesTo() { return sources.get(0).getAppliesTo(); }
        public String getDescription() { return sources.get(0).getDescription(); }
        public String getExample() { return sources.get(0).getExample(); }

        @Nullable
        public String getLibrary() { return sources.get(0).getLibrary(); }

        /** Returns true if the primary source is built-in JavaFX. */
        public boolean isBuiltIn() { return sources.get(0).getLibrary() == null; }

        void addSource(@NotNull SourceEntry source) {
            sources.add(source);
        }
    }

    // ==================== Public API ====================

    /**
     * Returns the PropertyInfo for the given property name, or null if unknown.
     */
    @Nullable
    public static PropertyInfo getProperty(@NotNull String propertyName, @NotNull Project project) {
        return getProperties(project).get(propertyName.toLowerCase());
    }

    /**
     * Returns all known CSS property names for the given project.
     */
    @NotNull
    public static Set<String> getAllPropertyNames(@NotNull Project project) {
        return Collections.unmodifiableSet(getProperties(project).keySet());
    }

    /**
     * Returns all PropertyInfo entries for the given project.
     */
    @NotNull
    public static Collection<PropertyInfo> getAllProperties(@NotNull Project project) {
        return Collections.unmodifiableCollection(getProperties(project).values());
    }

    /**
     * Clears the cached properties for the given project.
     * Call this if dependencies might have changed.
     */
    public static void clearCache(@NotNull Project project) {
        synchronized (CACHE) {
            CACHE.remove(project);
        }
    }

    // ==================== Loading ====================

    @NotNull
    private static Map<String, PropertyInfo> getProperties(@NotNull Project project) {
        synchronized (CACHE) {
            Map<String, PropertyInfo> result = CACHE.get(project);
            if (result == null) {
                result = loadProperties(project);
                CACHE.put(project, result);
            }
            return result;
        }
    }

    @NotNull
    private static Map<String, PropertyInfo> loadProperties(@NotNull Project project) {
        Map<String, PropertyInfo> result = new LinkedHashMap<>();
        List<IndexEntry> entries = loadIndex();
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, RawProperty>>() {}.getType();
        for (IndexEntry entry : entries) {
            if (entry.markerClass != null && !isClassOnClasspath(entry.markerClass, project)) {
                continue;
            }
            loadSingleFile(CSS_DATA_DIR + entry.fileName, gson, type, result);
        }
        return result;
    }

    private static final class IndexEntry {
        final @Nullable String markerClass;
        final @NotNull String fileName;

        IndexEntry(@Nullable String markerClass, @NotNull String fileName) {
            this.markerClass = markerClass;
            this.fileName = fileName;
        }
    }

    @NotNull
    private static List<IndexEntry> loadIndex() {
        List<IndexEntry> entries = new ArrayList<>();
        try (InputStream is = FxCssPropertyTable.class.getResourceAsStream(CSS_DATA_INDEX)) {
            if (is == null) {
                LOG.warn("CSS data index not found: " + CSS_DATA_INDEX);
                return entries;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        entries.add(new IndexEntry(line.substring(0, eq), line.substring(eq + 1)));
                    } else {
                        entries.add(new IndexEntry(null, line));
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to load CSS data index", ex);
        }
        return entries;
    }

    private static void loadSingleFile(@NotNull String resourcePath, @NotNull Gson gson,
                                        @NotNull Type type,
                                        @NotNull Map<String, PropertyInfo> result) {
        try (InputStream is = FxCssPropertyTable.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("CSS property file not found: " + resourcePath);
                return;
            }
            Map<String, RawProperty> raw = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), type);
            if (raw == null) {
                return;
            }
            // Extract library name from _meta (null for built-in JavaFX)
            RawProperty meta = raw.get("_meta");
            String library = (meta != null && meta.library != null
                    && !"JavaFX".equals(meta.library)) ? meta.library : null;

            for (Map.Entry<String, RawProperty> entry : raw.entrySet()) {
                String name = entry.getKey();
                if ("_meta".equals(name)) {
                    continue;
                }
                RawProperty rp = entry.getValue();
                SourceEntry source = new SourceEntry(
                        rp.valueType, rp.values, rp.defaultValue,
                        rp.appliesTo, rp.description, rp.example, library);
                String key = name.toLowerCase();
                PropertyInfo existing = result.get(key);
                if (existing != null) {
                    existing.addSource(source);
                    continue;
                }
                result.put(key, new PropertyInfo(name, source));
            }
        } catch (Exception ex) {
            LOG.error("Failed to load " + resourcePath, ex);
        }
    }

    private static boolean isClassOnClasspath(@NotNull String fqcn, @NotNull Project project) {
        try {
            return JavaPsiFacade.getInstance(project)
                    .findClass(fqcn, GlobalSearchScope.allScope(project)) != null;
        } catch (ProcessCanceledException pce) {
            throw pce;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Raw JSON model ====================

    @SuppressWarnings("all")
    private static class RawProperty {
        String valueType;
        List<String> values;
        @com.google.gson.annotations.SerializedName("default")
        String defaultValue;
        String appliesTo;
        String description;
        String example;
        // _meta fields
        String library;
        String version;
        String markerClass;
    }
}
