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
import java.util.LinkedHashMap;
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

    private static volatile Map<String, String> HEX_TO_NAME;
    private static volatile Set<String> ALL_UPPER_NAMES;

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
        return getMap().get(key);
    }

    /**
     * Returns the set of all named-color constant names in uppercase form
     * (e.g. {@code DARKORANGE}). Used as a leaf-text prefilter so that the
     * detector does not call {@code resolve()} on every identifier.
     */
    @NotNull
    public static Set<String> allUpperCaseNames() {
        getMap();
        return ALL_UPPER_NAMES;
    }

    @NotNull
    private static Map<String, String> getMap() {
        Map<String, String> result = HEX_TO_NAME;
        if (result == null) {
            synchronized (FxNamedColorsReverseIndex.class) {
                result = HEX_TO_NAME;
                if (result == null) {
                    result = load();
                    HEX_TO_NAME = result;
                    Set<String> upper = new java.util.HashSet<>(result.size());
                    for (String name : result.values()) {
                        upper.add(name);
                    }
                    ALL_UPPER_NAMES = Collections.unmodifiableSet(upper);
                }
            }
        }
        return result;
    }

    @NotNull
    private static Map<String, String> load() {
        Map<String, String> result = new HashMap<>();
        try (InputStream is = FxNamedColorsReverseIndex.class
                .getResourceAsStream("/data/fx-named-colors.json")) {
            if (is == null) {
                LOG.warn("fx-named-colors.json not found");
                return result;
            }
            Gson gson = new Gson();
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            LinkedHashMap<String, String> raw = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), type);
            if (raw == null) {
                return result;
            }
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                String name = entry.getKey();
                if ("transparent".equalsIgnoreCase(name)) {
                    continue;
                }
                String hex = entry.getValue();
                String normalized = normalizeHex(hex);
                if (normalized == null) {
                    continue;
                }
                // Keep the first occurrence — the canonical US spelling
                // (DARKGRAY before DARKGREY, etc.).
                result.putIfAbsent(normalized, name.toUpperCase(java.util.Locale.ROOT));
            }
        } catch (Exception ex) {
            LOG.error("Failed to load fx-named-colors.json for reverse index", ex);
        }
        return result;
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
        return h.toLowerCase(java.util.Locale.ROOT);
    }
}
