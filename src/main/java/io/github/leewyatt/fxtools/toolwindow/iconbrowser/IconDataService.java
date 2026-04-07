package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-level service that manages icon pack data.
 * Loads icon-packs.json lazily on first access, loads per-pack path data on demand.
 */
@Service(Service.Level.APP)
public final class IconDataService {

    private static final Logger LOG = Logger.getInstance(IconDataService.class);
    private static final String INDEX_PATH = "/data/icons/icon-packs.json";

    // ==================== Data Classes ====================

    /**
     * Metadata for a single icon pack.
     */
    public static final class PackInfo {
        private final String id;
        private final String name;
        private final List<String> files;
        private int total;
        private int renderable;
        private final String license;
        private String enumClass;
        private String maven;
        private final String sourceUrl;
        private final int index;

        PackInfo(String id, String name, String file, int total, int renderable, String license,
                 String enumClass, String maven, String sourceUrl, int index) {
            this.id = id;
            this.name = name;
            this.files = new ArrayList<>();
            if (file != null && !file.isEmpty()) {
                this.files.add(file);
            }
            this.total = total;
            this.renderable = renderable;
            this.license = license;
            this.enumClass = nullIfEmpty(enumClass);
            this.maven = nullIfEmpty(maven);
            this.sourceUrl = nullIfEmpty(sourceUrl);
            this.index = index;
        }

        /**
         * Merges an additional data file from a duplicate pack entry.
         * Also fills in missing optional fields from the other entry.
         */
        void mergeFile(RawPack other) {
            if (other.file != null && !other.file.isEmpty()) {
                this.files.add(other.file);
            }
            this.total += other.total;
            this.renderable += other.renderable;
            if (this.maven == null) {
                this.maven = nullIfEmpty(other.maven);
            }
            if (this.enumClass == null) {
                this.enumClass = nullIfEmpty(other.enumClass);
            }
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<String> getFiles() { return files; }
        public int getTotal() { return total; }
        /** Number of icons in this pack that have extractable SVG paths. {@code total - renderable} icons will render as placeholders. */
        public int getRenderable() { return renderable; }
        public String getLicense() { return license; }
        public @Nullable String getEnumClass() { return enumClass; }
        public @Nullable String getMaven() { return maven; }
        public @Nullable String getSourceUrl() { return sourceUrl; }
        public int getIndex() { return index; }

        /**
         * Returns true if this pack is an Ikonli library (has enumClass).
         */
        public boolean isIkonli() { return enumClass != null && !enumClass.isEmpty(); }

        @Override
        public String toString() { return name; }
    }

    /**
     * A single icon entry with name, pack reference, and search tags.
     */
    public static final class IconEntry {
        /** The icon's true Ikonli literal (e.g. {@code "mdi-tag"}), stored as-is from the {@code n} field. */
        private final String name;
        private final PackInfo pack;
        private final String[] tags;
        /** Java enum constant name (e.g. {@code "MDI_TAG"}), from the {@code e} field. */
        private final String enumConstantName;
        private final String enumClassOverride;
        private final boolean noPath;

        IconEntry(String name, PackInfo pack, String[] tags,
                  @Nullable String enumConstantName,
                  @Nullable String enumClassOverride, boolean noPath) {
            this.name = name;
            this.pack = pack;
            this.tags = tags;
            this.enumConstantName = enumConstantName;
            this.enumClassOverride = enumClassOverride;
            this.noPath = noPath;
        }

        /** Returns the {@code n} field value — the true Ikonli literal (e.g. {@code "mdi-tag"}). */
        public String getName() { return name; }
        public PackInfo getPack() { return pack; }
        public String[] getTags() { return tags; }
        public String getPackId() { return pack.getId(); }
        /** Returns the real Java enum constant name (e.g. {@code "MDI_TAG"}), or null for non-Ikonli icons. */
        public @Nullable String getEnumConstantName() { return enumConstantName; }
        public @Nullable String getEnumClassOverride() { return enumClassOverride; }

        /**
         * Returns true if this icon has a renderable SVG path (i.e. {@code np != true}
         * in {@code icon-packs.json}). Placeholder rendering should be used when this
         * returns false.
         */
        public boolean isRenderable() { return !noPath; }

        /**
         * Returns the effective enumClass for this icon.
         * Uses the icon-level override if present, otherwise falls back to pack-level.
         */
        public @Nullable String getEffectiveEnumClass() {
            return enumClassOverride != null ? enumClassOverride : pack.getEnumClass();
        }

        /**
         * Returns the icon literal — the {@code n} field value, globally unique.
         */
        public String getLiteral() { return name; }
    }

    // ==================== State ====================

    private volatile boolean loaded;
    private String ikonliVersion = "";
    private List<PackInfo> allPacks = Collections.emptyList();
    private Map<String, PackInfo> packById = Collections.emptyMap();
    private List<IconEntry> allIcons = Collections.emptyList();
    private Map<String, IconEntry> literalMap = Collections.emptyMap();
    /** packId → all enumClass FQCNs (pack-level + icon-level overrides). */
    private Map<String, Set<String>> packEnumClasses = Collections.emptyMap();
    /** enumClass FQCN → packId (reverse lookup for Ikonli gutter icons). */
    private Map<String, String> enumClassToPackId = Collections.emptyMap();
    /** Reverse lookup: enum constant name → IconEntry, scoped per enumClass FQCN.
     *  Used by {@link io.github.leewyatt.fxtools.ikonli.IkonliGutterIconProvider} to
     *  match Java enum references to icon entries without name guessing. */
    private Map<String, Map<String, IconEntry>> enumConstantIndex = Collections.emptyMap();
    private final Map<String, Map<String, String>> pathCache = new ConcurrentHashMap<>();

    private static final Key<com.intellij.psi.util.CachedValue<Set<String>>> AVAILABLE_PACKS_KEY =
            Key.create("fx.icon.available.packs");

    // ==================== Accessor ====================

    public static IconDataService getInstance() {
        return ApplicationManager.getApplication().getService(IconDataService.class);
    }

    // ==================== Public API ====================

    public boolean isLoaded() { return loaded; }
    public String getIkonliVersion() { return ikonliVersion; }
    public List<PackInfo> getAllPacks() { return allPacks; }
    public List<IconEntry> getAllIcons() { return allIcons; }
    public Map<String, IconEntry> getLiteralMap() { return literalMap; }

    /**
     * Ensures the index is loaded. Call from background thread for first load;
     * subsequent calls return immediately.
     */
    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            loadIndex();
            loaded = true;
        }
    }

    /**
     * Returns true if the given pack's path data is already loaded into cache.
     */
    public boolean isPackLoaded(@NotNull String packId) {
        return pathCache.containsKey(packId);
    }

    /**
     * Gets the SVG path for an icon. Returns null if the pack is not yet loaded.
     */
    @Nullable
    public String getPath(@NotNull IconEntry icon) {
        Map<String, String> paths = pathCache.get(icon.getPackId());
        return paths != null ? paths.get(icon.getName()) : null;
    }

    /**
     * Resolves an Ikonli enum class FQCN (e.g. {@code org.kordamp.ikonli.fontawesome.FontAwesome})
     * to its pack id. Returns null if the class is not a known Ikonli enum class.
     */
    @Nullable
    public String getPackIdByEnumClass(@NotNull String enumClassFqcn) {
        return enumClassToPackId.get(enumClassFqcn);
    }

    /**
     * Finds an icon entry by its enum class FQCN and enum constant name.
     * Returns null if no match is found.
     */
    @Nullable
    public IconEntry findByEnumConstant(@NotNull String enumClassFqcn, @NotNull String constantName) {
        Map<String, IconEntry> byConstant = enumConstantIndex.get(enumClassFqcn);
        return byConstant != null ? byConstant.get(constantName) : null;
    }

    /**
     * Ensures a pack's path data is loaded into cache.
     * Safe to call from any thread.
     */
    public void ensurePackLoaded(@NotNull PackInfo pack) {
        if (pathCache.containsKey(pack.getId())) {
            return;
        }
        loadPackPaths(pack);
    }

    /**
     * Returns the total icon count across the given packs.
     */
    public int countIcons(@NotNull Set<String> packIds) {
        int count = 0;
        for (PackInfo pack : allPacks) {
            if (packIds.contains(pack.getId())) {
                count += pack.getTotal();
            }
        }
        return count;
    }

    /**
     * Returns the set of pack IDs whose Ikonli enumClass is on the project's classpath.
     * Result is cached per project and invalidated on dependency changes.
     *
     * @param project the current project
     * @return unmodifiable set of available pack IDs, empty if no Ikonli dependency found
     */
    @NotNull
    public static Set<String> getAvailablePacks(@NotNull Project project) {
        IconDataService service = getInstance();
        if (!service.loaded) {
            return Collections.emptySet();
        }
        return CachedValuesManager.getManager(project).getCachedValue(
                project, AVAILABLE_PACKS_KEY, () -> {
                    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                    Set<String> result = new HashSet<>();
                    for (PackInfo pack : service.allPacks) {
                        Set<String> enumClasses = service.packEnumClasses.get(pack.getId());
                        if (enumClasses == null) {
                            continue;
                        }
                        for (String fqcn : enumClasses) {
                            if (facade.findClass(fqcn, scope) != null) {
                                result.add(pack.getId());
                                break;
                            }
                        }
                    }
                    return CachedValueProvider.Result.create(
                            Collections.unmodifiableSet(result),
                            ProjectRootModificationTracker.getInstance(project));
                }, false);
    }

    // ==================== Internal Loading ====================

    private void loadIndex() {
        try (InputStream is = getClass().getResourceAsStream(INDEX_PATH)) {
            if (is == null) {
                LOG.warn("Icon pack index not found: " + INDEX_PATH);
                return;
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                RawIndex rawIndex = gson.fromJson(reader, RawIndex.class);
                if (rawIndex == null) {
                    return;
                }

                ikonliVersion = rawIndex.version != null ? rawIndex.version : "";

                // Build packs (merge duplicate IDs with multiple data files)
                List<PackInfo> packs = new ArrayList<>();
                Map<String, PackInfo> byId = new HashMap<>();
                if (rawIndex.packs != null) {
                    int idx = 0;
                    for (RawPack rp : rawIndex.packs) {
                        PackInfo existing = byId.get(rp.id);
                        if (existing != null) {
                            existing.mergeFile(rp);
                        } else {
                            // If `renderable` is missing from JSON (legacy data), assume all icons are renderable
                            int renderable = rp.renderable > 0 ? rp.renderable : rp.total;
                            PackInfo pi = new PackInfo(rp.id, rp.name, rp.file, rp.total, renderable,
                                    rp.license, rp.enumClass, rp.maven,
                                    rp.url, idx++);
                            packs.add(pi);
                            byId.put(pi.getId(), pi);
                        }
                    }
                }

                // Derive missing maven from enumClass for Ikonli packs
                for (PackInfo pi : packs) {
                    if (pi.maven == null && pi.enumClass != null) {
                        pi.maven = deriveMavenFromEnumClass(pi.enumClass);
                    }
                }

                // Build icons
                List<IconEntry> icons = new ArrayList<>();
                Map<String, IconEntry> litMap = new HashMap<>();
                if (rawIndex.icons != null) {
                    for (RawIcon ri : rawIndex.icons) {
                        PackInfo pack = byId.get(ri.p);
                        if (pack == null) {
                            continue;
                        }
                        String[] tags = ri.t != null ? ri.t.toArray(new String[0]) : new String[0];
                        boolean noPath = ri.np != null && ri.np;
                        IconEntry entry = new IconEntry(ri.n, pack, tags, ri.e, ri.ec, noPath);
                        icons.add(entry);
                        litMap.put(entry.getLiteral(), entry);
                    }
                }

                // Build packId → enumClasses map (pack-level + icon-level overrides)
                Map<String, Set<String>> enumClassMap = new HashMap<>();
                for (PackInfo pack : packs) {
                    if (pack.getEnumClass() != null) {
                        enumClassMap.computeIfAbsent(pack.getId(), k -> new HashSet<>())
                                .add(pack.getEnumClass());
                    }
                }
                for (IconEntry icon : icons) {
                    if (icon.getEnumClassOverride() != null) {
                        enumClassMap.computeIfAbsent(icon.getPackId(), k -> new HashSet<>())
                                .add(icon.getEnumClassOverride());
                    }
                }

                // Invert packId → enumClasses to enumClass → packId for reverse lookup
                Map<String, String> enumToPack = new HashMap<>();
                for (Map.Entry<String, Set<String>> e : enumClassMap.entrySet()) {
                    for (String fqcn : e.getValue()) {
                        enumToPack.put(fqcn, e.getKey());
                    }
                }

                // Build enumConstantIndex: enumClassFqcn → (constantName → IconEntry)
                Map<String, Map<String, IconEntry>> ecIndex = new HashMap<>();
                for (IconEntry icon : icons) {
                    String ec = icon.getEffectiveEnumClass();
                    String cn = icon.getEnumConstantName();
                    if (ec != null && cn != null) {
                        ecIndex.computeIfAbsent(ec, k -> new HashMap<>()).put(cn, icon);
                    }
                }

                this.allPacks = Collections.unmodifiableList(packs);
                this.packById = Collections.unmodifiableMap(byId);
                this.allIcons = Collections.unmodifiableList(icons);
                this.literalMap = Collections.unmodifiableMap(litMap);
                this.packEnumClasses = Collections.unmodifiableMap(enumClassMap);
                this.enumConstantIndex = Collections.unmodifiableMap(ecIndex);
                this.enumClassToPackId = Collections.unmodifiableMap(enumToPack);

                LOG.info("Loaded icon index: " + packs.size() + " packs, " + icons.size() + " icons");
            }
        } catch (IOException e) {
            LOG.warn("Failed to load icon pack index", e);
        }
    }

    private void loadPackPaths(@NotNull PackInfo pack) {
        Map<String, String> merged = new HashMap<>();
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, String>>() {}.getType();

        for (String file : pack.getFiles()) {
            String filePath = "/data/icons/" + file;
            try (InputStream raw = getClass().getResourceAsStream(filePath)) {
                if (raw == null) {
                    LOG.warn("Pack data file not found: " + filePath);
                    continue;
                }
                try (Reader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
                    Map<String, String> paths = gson.fromJson(reader, mapType);
                    if (paths != null) {
                        merged.putAll(paths);
                    }
                }
            } catch (IOException e) {
                LOG.warn("Failed to load pack data: " + filePath, e);
            }
        }
        pathCache.put(pack.getId(), merged);
    }

    // ==================== Raw JSON Model ====================

    private static class RawIndex {
        String version;
        List<RawPack> packs;
        List<RawIcon> icons;
    }

    private static class RawPack {
        String id, name, file, license, enumClass, maven, url;
        int total;
        int renderable;
    }

    private static class RawIcon {
        String n, p, e, ec;
        List<String> t;
        Boolean np; // null when omitted; Boolean (not primitive) so Gson preserves absence
    }

    /**
     * Derives maven coordinate from enumClass FQCN.
     * Pattern: org.kordamp.ikonli.{module}.ClassName → org.kordamp.ikonli:ikonli-{module}-pack
     */
    @Nullable
    private static String deriveMavenFromEnumClass(@NotNull String enumClass) {
        int lastDot = enumClass.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String pkg = enumClass.substring(0, lastDot);
        int moduleDot = pkg.lastIndexOf('.');
        if (moduleDot <= 0) {
            return null;
        }
        String module = pkg.substring(moduleDot + 1);
        return "org.kordamp.ikonli:ikonli-" + module + "-pack";
    }

    @Nullable
    private static String nullIfEmpty(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
