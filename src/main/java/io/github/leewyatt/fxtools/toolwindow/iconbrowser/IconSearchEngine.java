package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Searches icon entries by keyword matching against name and tags.
 * Pure function — no state, no side effects.
 */
public final class IconSearchEngine {

    // No minimum query length — even single character search is supported

    private IconSearchEngine() {
    }

    /**
     * Searches icons within the enabled packs.
     *
     * @param query          search text, or null/blank for browse-all mode
     * @param enabledPackIds set of pack IDs to include
     * @param service        icon data service
     * @return matching icons, sorted by relevance
     */
    @NotNull
    public static List<IconDataService.IconEntry> search(@Nullable String query,
                                                          @NotNull Set<String> enabledPackIds,
                                                          @NotNull IconDataService service) {
        List<IconDataService.IconEntry> scope = new ArrayList<>();
        for (IconDataService.IconEntry icon : service.getAllIcons()) {
            if (enabledPackIds.contains(icon.getPackId())) {
                scope.add(icon);
            }
        }

        if (query == null || query.isBlank()) {
            return scope;
        }

        String normalized = query.toLowerCase().trim();
        if (normalized.isEmpty()) {
            return scope;
        }

        // Step 1: exact literal match. For FA5/FA6 literal collisions, picks the
        // user-enabled version (preferring FA6 when both enabled).
        IconDataService.IconEntry exact = service.resolveLiteral(normalized, enabledPackIds);
        if (exact != null) {
            List<IconDataService.IconEntry> results = new ArrayList<>();
            results.add(exact);
            results.addAll(fuzzySearch(scope, normalized, exact));
            return results;
        }

        // Step 2: fuzzy search
        return fuzzySearch(scope, normalized, null);
    }

    @NotNull
    private static List<IconDataService.IconEntry> fuzzySearch(
            @NotNull List<IconDataService.IconEntry> scope,
            @NotNull String query,
            @Nullable IconDataService.IconEntry exclude) {
        String[] keywords = query.split("[\\s,\\-_]+");

        List<Map.Entry<IconDataService.IconEntry, Integer>> scored = new ArrayList<>();
        for (IconDataService.IconEntry icon : scope) {
            if (icon == exclude) {
                continue;
            }
            int hits = 0;
            for (String kw : keywords) {
                if (kw.isEmpty()) {
                    continue;
                }
                boolean match = false;
                for (String tag : icon.getTags()) {
                    if (tag.contains(kw)) {
                        match = true;
                        break;
                    }
                }
                if (!match && icon.getName().contains(kw)) {
                    match = true;
                }
                if (match) {
                    hits++;
                }
            }
            if (hits > 0) {
                scored.add(Map.entry(icon, hits));
            }
        }

        scored.sort((a, b) -> b.getValue() - a.getValue());

        List<IconDataService.IconEntry> result = new ArrayList<>(scored.size());
        for (Map.Entry<IconDataService.IconEntry, Integer> e : scored) {
            result.add(e.getKey());
        }
        return result;
    }
}
