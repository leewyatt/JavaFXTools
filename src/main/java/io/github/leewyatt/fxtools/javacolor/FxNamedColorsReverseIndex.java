package io.github.leewyatt.fxtools.javacolor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reverse lookup from {@code 6-digit lowercase hex} to its JavaFX named-color
 * constant ({@code DARKORANGE}, {@code RED}, ...). Only colors with alpha == 1
 * are indexed; {@code TRANSPARENT} is excluded since it is not a meaningful
 * editing start point.
 *
 * <p>When several names share the same hex (e.g. {@code DARKGRAY} and
 * {@code DARKGREY}) the index keeps the first occurrence in the JSON,
 * which is the preferred US spelling.</p>
 */
public final class FxNamedColorsReverseIndex {

    private static final Logger LOG = Logger.getInstance(FxNamedColorsReverseIndex.class);

    /**
     * Immutable holder published as a single volatile reference so that the
     * two tables are observed atomically: either both visible (after the
     * volatile store on {@link #TABLES}) or neither (before it). Avoids the
     * partial-initialization window that would exist if {@code hexToName} and
     * {@code allUpperNames} were two separate volatile fields written in
     * sequence.
     */
    private static final class Tables {
        final Map<String, String> hexToName;
        final Set<String> allUpperNames;

        Tables(Map<String, String> hexToName, Set<String> allUpperNames) {
            this.hexToName = hexToName;
            this.allUpperNames = allUpperNames;
        }
    }

    private static volatile Tables TABLES;

    private FxNamedColorsReverseIndex() {
    }

    /**
     * Returns the preferred uppercase JavaFX constant name for the given color
     * (alpha must be 255), or {@code null} if no constant matches.
     */
    @Nullable
    public static String findConstantName(@NotNull Color color) {
        if (color.getAlpha() != 255) {
            return null;
        }
        String key = String.format("%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue());
        return tables().hexToName.get(key);
    }

    /**
     * Returns the set of all named-color constant names in uppercase form,
     * <b>including alias spellings</b> like {@code DARKGREY} and {@code GREY}.
     * Used as a leaf-text prefilter so that the detector does not call
     * {@code resolve()} on every identifier.
     */
    @NotNull
    public static Set<String> allUpperCaseNames() {
        return tables().allUpperNames;
    }

    @NotNull
    private static Tables tables() {
        Tables local = TABLES;
        if (local == null) {
            synchronized (FxNamedColorsReverseIndex.class) {
                local = TABLES;
                if (local == null) {
                    local = load();
                    TABLES = local;
                }
            }
        }
        return local;
    }

    /**
     * Loads both tables:
     * <ul>
     *   <li>{@code hexToName} — hex (lowercase 6 digits) → preferred US
     *       spelling (first occurrence wins).</li>
     *   <li>{@code allUpperNames} — all JSON names uppercased, including
     *       alias spellings, so the leaf-text prefilter accepts
     *       {@code Color.DARKGREY} as well as {@code Color.DARKGRAY}.</li>
     * </ul>
     */
    @NotNull
    private static Tables load() {
        Map<String, String> hexToName = new HashMap<>();
        Set<String> allNames = new HashSet<>();
        try (InputStream is = FxNamedColorsReverseIndex.class
                .getResourceAsStream("/data/fx-named-colors.json")) {
            if (is == null) {
                LOG.warn("fx-named-colors.json not found");
                return new Tables(hexToName, Collections.unmodifiableSet(allNames));
            }
            Gson gson = new Gson();
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            LinkedHashMap<String, String> raw = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), type);
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    String name = entry.getKey();
                    if ("transparent".equalsIgnoreCase(name)) {
                        continue;
                    }
                    String upper = name.toUpperCase(Locale.ROOT);
                    allNames.add(upper);

                    String normalized = normalizeHex(entry.getValue());
                    if (normalized != null) {
                        // Keep the first occurrence — the canonical US spelling
                        // (DARKGRAY before DARKGREY, etc.).
                        hexToName.putIfAbsent(normalized, upper);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to load fx-named-colors.json for reverse index", ex);
        }
        return new Tables(hexToName, Collections.unmodifiableSet(allNames));
    }

    @Nullable
    private static String normalizeHex(@Nullable String hex) {
        if (hex == null) {
            return null;
        }
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) {
            return null;
        }
        return h.toLowerCase(Locale.ROOT);
    }
}
