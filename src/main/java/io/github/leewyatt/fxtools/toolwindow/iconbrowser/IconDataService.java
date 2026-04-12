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
        private final @Nullable String title;

        PackInfo(String id, String name, String file, int total, int renderable, String license,
                 String enumClass, String maven, String sourceUrl, int index, @Nullable String title) {
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
            this.title = nullIfEmpty(title);
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
         * Returns the human-readable group title (from upstream ikonlipacks.json, e.g.
         * {@code "MaterialDesign2 (Latest)"}). All member packs of the same aggregation
         * group share this title. Null only when the {@code title} field is missing from
         * {@code icon-packs.json} (defensive fallback — should not happen post-migration).
         */
        public @Nullable String getTitle() { return title; }

        /**
         * Returns true if this pack is an Ikonli library (has enumClass).
         */
        public boolean isIkonli() { return enumClass != null && !enumClass.isEmpty(); }

        @Override
        public String toString() { return name; }
    }

    /**
     * An aggregated group of one or more {@link PackInfo} instances that share the same
     * Maven artifactId. Ikonli libraries like MaterialDesign2 are split into multiple
     * Java enum classes (MaterialDesignA~Z) due to the JVM 64KB class file limit; to
     * users these are one logical library.
     *
     * <p>A group's {@link #getName() display name} comes from {@link PackInfo#getTitle()}
     * which is populated at data-migration time from {@code ikonlipacks.json}. Groups
     * that consist of only one pack behave identically to the single pack.</p>
     *
     * <p>Group membership is stable: an icon's {@link IconEntry#getPackId() packId} is
     * never changed by aggregation. Callers that need group-level information should go
     * through {@link IconDataService#getGroupByPackId(String)} or
     * {@link IconDataService#getGroupForIcon(IconEntry)}.</p>
     */
    public static final class PackGroup {
        private final String id;
        private final @Nullable String title;
        private final List<PackInfo> packs;
        private final List<String> packIds;
        private final int total;
        private final int renderable;
        private final @Nullable String maven;
        private final @Nullable String sourceUrl;
        private final @Nullable String license;
        private final Set<String> enumClasses;
        private final int index;

        private PackGroup(String id, @Nullable String title, List<PackInfo> packs,
                          List<String> packIds, int total, int renderable,
                          @Nullable String maven, @Nullable String sourceUrl,
                          @Nullable String license, Set<String> enumClasses, int index) {
            this.id = id;
            this.title = title;
            this.packs = packs;
            this.packIds = packIds;
            this.total = total;
            this.renderable = renderable;
            this.maven = maven;
            this.sourceUrl = sourceUrl;
            this.license = license;
            this.enumClasses = enumClasses;
            this.index = index;
        }

        /**
         * Aggregates a list of member packs into a PackGroup.
         * Totals are summed; title / maven / sourceUrl / license take the first non-null
         * value (guaranteed consistent within a group because group key = maven artifactId).
         * enumClasses is the union of all member packs' enumClasses.
         */
        static PackGroup build(@NotNull String id, @NotNull List<PackInfo> members, int index) {
            String title = null;
            int total = 0;
            int renderable = 0;
            String maven = null;
            String sourceUrl = null;
            String license = null;
            Set<String> enumClasses = new LinkedHashSet<>();
            List<String> packIds = new ArrayList<>(members.size());

            for (PackInfo p : members) {
                total += p.getTotal();
                renderable += p.getRenderable();
                packIds.add(p.getId());
                if (title == null && p.getTitle() != null) {
                    title = p.getTitle();
                }
                if (maven == null && p.getMaven() != null) {
                    maven = p.getMaven();
                }
                if (sourceUrl == null && p.getSourceUrl() != null) {
                    sourceUrl = p.getSourceUrl();
                }
                if (license == null && p.getLicense() != null && !p.getLicense().isEmpty()) {
                    license = p.getLicense();
                }
                if (p.getEnumClass() != null) {
                    enumClasses.add(p.getEnumClass());
                }
            }

            return new PackGroup(id, title,
                    Collections.unmodifiableList(new ArrayList<>(members)),
                    Collections.unmodifiableList(packIds),
                    total, renderable, maven, sourceUrl, license,
                    Collections.unmodifiableSet(enumClasses), index);
        }

        public String getId() { return id; }

        /**
         * Returns the user-visible display name for this group. Primary source is
         * {@link PackInfo#getTitle()} (populated from upstream ikonlipacks.json during
         * data migration). Falls back to a capitalized version of {@link #getId()} if
         * the title field is missing (should not happen with migrated data).
         */
        public String getName() {
            if (title != null && !title.isEmpty()) {
                return title;
            }
            return displayNameOf(id);
        }

        public @Nullable String getTitle() { return title; }
        public List<PackInfo> getPacks() { return packs; }

        /** Returns the raw pack ids of all member packs (stable, preserves insertion order). */
        public List<String> getPackIds() {
            return packIds;
        }

        public int getTotal() { return total; }
        public int getRenderable() { return renderable; }
        public @Nullable String getMaven() { return maven; }
        public @Nullable String getSourceUrl() { return sourceUrl; }
        public @Nullable String getLicense() { return license; }
        public Set<String> getEnumClasses() { return enumClasses; }
        public int getIndex() { return index; }

        /** True if this group contains more than one underlying pack. */
        public boolean isAggregated() { return packs.size() > 1; }

        @Override
        public String toString() { return getName(); }
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
    private List<IconEntry> allIcons = Collections.emptyList();
    /**
     * One-to-many literal index: same literal can appear in multiple packs.
     * In practice this only happens for FA5 vs FA6 (Ikonli upstream uses the same
     * {@code fab-/far-/fas-} prefixes for both versions). Used by
     * {@link #resolveLiteral(String, Set)} to pick the correct version based on the
     * caller's allowed-pack set.
     */
    private Map<String, List<IconEntry>> literalMultiMap = Collections.emptyMap();
    /** packId → all enumClass FQCNs (pack-level + icon-level overrides). */
    private Map<String, Set<String>> packEnumClasses = Collections.emptyMap();
    /** enumClass FQCN → packId (reverse lookup for Ikonli gutter icons). */
    private Map<String, String> enumClassToPackId = Collections.emptyMap();
    /** Reverse lookup: enum constant name → IconEntry, scoped per enumClass FQCN.
     *  Used by {@link io.github.leewyatt.fxtools.ikonli.IkonliGutterIconProvider} to
     *  match Java enum references to icon entries without name guessing. */
    private Map<String, Map<String, IconEntry>> enumConstantIndex = Collections.emptyMap();
    private final Map<String, Map<String, String>> pathCache = new ConcurrentHashMap<>();

    // Group aggregation (see PackGroup Javadoc)
    private List<PackGroup> allGroups = Collections.emptyList();
    private Map<String, PackGroup> groupsById = Collections.emptyMap();
    private Map<String, PackGroup> groupByRawPackId = Collections.emptyMap();
    /**
     * Raw pack ids that belong to the {@code fontawesome6} group, used by
     * {@link #resolveLiteral(String, Set)} to implement the "prefer FA6 over FA5
     * when both are available" disambiguation rule without hard-coded string matching.
     */
    private Set<String> fontawesome6PackIds = Collections.emptySet();

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

    /**
     * Returns all aggregated groups, sorted by group index (insertion order of the
     * first member pack encountered). Each group aggregates one or more packs that
     * share a Maven artifactId — see {@link PackGroup}.
     */
    public List<PackGroup> getAllGroups() { return allGroups; }

    /**
     * Returns the group containing the given raw packId, or null if unknown.
     */
    @Nullable
    public PackGroup getGroupByPackId(@NotNull String packId) {
        return groupByRawPackId.get(packId);
    }

    /**
     * Convenience: {@code getGroupByPackId(icon.getPackId())}.
     */
    @Nullable
    public PackGroup getGroupForIcon(@NotNull IconEntry icon) {
        return groupByRawPackId.get(icon.getPackId());
    }

    /**
     * Returns the group by group key (e.g. {@code "materialdesign2"}).
     */
    @Nullable
    public PackGroup getGroupById(@NotNull String groupId) {
        return groupsById.get(groupId);
    }

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
     * Resolves an icon literal (e.g. {@code "fab-accessible-icon"}) to the best matching
     * {@link IconEntry}, constrained to {@code allowedPackIds}.
     *
     * <p>The Ikonli {@code ikonli-fontawesome5-pack} and {@code ikonli-fontawesome6-pack}
     * use identical literal prefixes ({@code fab-/far-/fas-}), so a single literal can
     * match up to two entries. This method picks one according to the following rules:</p>
     * <ol>
     *   <li>If any candidate's pack is {@code fontawesome6}, return it (FA6 preference
     *       rule — matches <em>exactly one</em> Ikonli runtime behavior but is consistent
     *       and documented).</li>
     *   <li>Otherwise return the first candidate whose packId is in {@code allowedPackIds}.</li>
     *   <li>Returns {@code null} if no candidate is in {@code allowedPackIds}.</li>
     * </ol>
     *
     * <p>Callers that want classpath-aware resolution should pass
     * {@link #getAvailablePacks(Project)}; callers that want user-filter-aware resolution
     * (e.g. Icon Browser search) should pass the user's enabled pack set.</p>
     *
     * @param literal          the Ikonli literal to look up
     * @param allowedPackIds   the set of raw packIds this caller wants to consider
     * @return the preferred matching {@link IconEntry}, or {@code null}
     */
    @Nullable
    public IconEntry resolveLiteral(@NotNull String literal, @NotNull Set<String> allowedPackIds) {
        List<IconEntry> candidates = literalMultiMap.get(literal);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        IconEntry fallback = null;
        for (IconEntry candidate : candidates) {
            if (!allowedPackIds.contains(candidate.getPackId())) {
                continue;
            }
            // FA6 preference rule: if both FA5 and FA6 are allowed, prefer FA6.
            // Uses a precomputed packId set so we don't do string matching in the hot path
            // and we're robust to upstream package renames as long as groupKeyOf still
            // derives "fontawesome6" from the Maven artifactId.
            if (fontawesome6PackIds.contains(candidate.getPackId())) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
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
                                    rp.url, idx++, rp.title);
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
                Map<String, List<IconEntry>> litMulti = new HashMap<>();
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
                        litMulti.computeIfAbsent(entry.getLiteral(), k -> new ArrayList<>()).add(entry);
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

                // Build PackGroup aggregation by maven artifactId
                LinkedHashMap<String, List<PackInfo>> byGroupKey = new LinkedHashMap<>();
                for (PackInfo pack : packs) {
                    byGroupKey.computeIfAbsent(groupKeyOf(pack), k -> new ArrayList<>()).add(pack);
                }
                List<PackGroup> groupList = new ArrayList<>(byGroupKey.size());
                Map<String, PackGroup> groupById = new HashMap<>();
                Map<String, PackGroup> groupByPackIdMap = new HashMap<>();
                Set<String> fa6PackIds = new HashSet<>();
                int groupIdx = 0;
                for (Map.Entry<String, List<PackInfo>> e : byGroupKey.entrySet()) {
                    PackGroup group = PackGroup.build(e.getKey(), e.getValue(), groupIdx++);
                    groupList.add(group);
                    groupById.put(group.getId(), group);
                    boolean isFa6 = "fontawesome6".equals(group.getId());
                    for (PackInfo pack : e.getValue()) {
                        groupByPackIdMap.put(pack.getId(), group);
                        if (isFa6) {
                            fa6PackIds.add(pack.getId());
                        }
                    }
                }

                // Freeze sub-lists in literalMultiMap (value lists become immutable).
                // HashMap initial capacity formula: expectedSize / loadFactor + 1 to
                // avoid rehashing during population (default loadFactor is 0.75).
                Map<String, List<IconEntry>> frozenLitMulti =
                        new HashMap<>((int) (litMulti.size() / 0.75f) + 1);
                for (Map.Entry<String, List<IconEntry>> e : litMulti.entrySet()) {
                    frozenLitMulti.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
                }

                this.allPacks = Collections.unmodifiableList(packs);
                this.allIcons = Collections.unmodifiableList(icons);
                this.literalMultiMap = Collections.unmodifiableMap(frozenLitMulti);
                this.packEnumClasses = Collections.unmodifiableMap(enumClassMap);
                this.enumConstantIndex = Collections.unmodifiableMap(ecIndex);
                this.enumClassToPackId = Collections.unmodifiableMap(enumToPack);
                this.allGroups = Collections.unmodifiableList(groupList);
                this.groupsById = Collections.unmodifiableMap(groupById);
                this.groupByRawPackId = Collections.unmodifiableMap(groupByPackIdMap);
                this.fontawesome6PackIds = Collections.unmodifiableSet(fa6PackIds);

                LOG.info("Loaded icon index: " + packs.size() + " packs, " + icons.size()
                        + " icons, " + groupList.size() + " groups");
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
        String id, name, file, license, enumClass, maven, url, title;
        int total;
        int renderable;
    }

    private static class RawIcon {
        String n, p, e, ec;
        List<String> t;
        Boolean np; // null when omitted; Boolean (not primitive) so Gson preserves absence
    }

    /**
     * Derives the group key from a pack's Maven artifactId.
     * <p>Pattern: {@code org.kordamp.ikonli:ikonli-{name}-pack} → {@code name}.
     * Falls back to {@link PackInfo#getId()} when maven is null (defensive — all
     * Ikonli packs should have maven via {@link #deriveMavenFromEnumClass} fallback).</p>
     *
     * <p>Using maven artifactId (not enumClass simpleName or literal name) guarantees
     * that libraries with identical simpleNames but different packages — e.g.
     * {@code fontawesome5.FontAwesomeBrands} and {@code fontawesome6.FontAwesomeBrands} —
     * stay in separate groups.</p>
     */
    @NotNull
    private static String groupKeyOf(@NotNull PackInfo pack) {
        String maven = pack.getMaven();
        if (maven == null) {
            return pack.getId();
        }
        int colon = maven.indexOf(':');
        String artifact = colon >= 0 ? maven.substring(colon + 1) : maven;
        if (artifact.startsWith("ikonli-")) {
            artifact = artifact.substring("ikonli-".length());
        }
        if (artifact.endsWith("-pack")) {
            artifact = artifact.substring(0, artifact.length() - "-pack".length());
        }
        return artifact;
    }

    /**
     * Defensive fallback for {@link PackGroup#getName()} when the {@code title} field
     * is missing from {@code icon-packs.json}. Capitalizes the first letter of the
     * group key. Should never be reached with migrated data — every pack's title is
     * populated at data-migration time from upstream ikonlipacks.json.
     */
    @NotNull
    private static String displayNameOf(@NotNull String groupKey) {
        if (groupKey.isEmpty()) {
            return groupKey;
        }
        return Character.toUpperCase(groupKey.charAt(0)) + groupKey.substring(1);
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
