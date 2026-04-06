package io.github.leewyatt.fxtools.css;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and caches the JavaFX named color definitions from the bundled JSON resource.
 */
public final class FxNamedColors {

    private static final Logger LOG = Logger.getInstance(FxNamedColors.class);
    private static volatile Map<String, String> HEX_MAP;

    private FxNamedColors() {
    }

    /**
     * Returns the hex color string for a named color (case-insensitive), or null if unrecognized.
     */
    @Nullable
    public static String getHexColor(@NotNull String colorName) {
        return getHexMap().get(colorName.toLowerCase());
    }

    /**
     * Returns the java.awt.Color for a named color, or null if unrecognized.
     */
    @Nullable
    public static Color getColor(@NotNull String colorName) {
        String hex = getHexColor(colorName);
        if (hex == null) {
            return null;
        }
        return parseHexToColor(hex);
    }

    /**
     * Returns all named color names (lowercase).
     */
    @NotNull
    public static Set<String> getAllColorNames() {
        return Collections.unmodifiableSet(getHexMap().keySet());
    }

    /**
     * Checks if the given string is a recognized named color.
     */
    public static boolean isNamedColor(@NotNull String name) {
        return getHexMap().containsKey(name.toLowerCase());
    }

    @NotNull
    private static Map<String, String> getHexMap() {
        Map<String, String> result = HEX_MAP;
        if (result == null) {
            synchronized (FxNamedColors.class) {
                result = HEX_MAP;
                if (result == null) {
                    result = loadColors();
                    HEX_MAP = result;
                }
            }
        }
        return result;
    }

    @NotNull
    private static Map<String, String> loadColors() {
        Map<String, String> result = new LinkedHashMap<>();
        try (InputStream is = FxNamedColors.class.getResourceAsStream("/data/fx-named-colors.json")) {
            if (is == null) {
                LOG.warn("fx-named-colors.json not found");
                return result;
            }
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> raw = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), type);
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    result.put(entry.getKey().toLowerCase(), entry.getValue().toUpperCase());
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to load fx-named-colors.json", ex);
        }
        return result;
    }

    @Nullable
    private static Color parseHexToColor(@NotNull String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 8) {
                int alpha = Integer.parseInt(h.substring(0, 2), 16);
                int rgb = Integer.parseInt(h.substring(2), 16);
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
            } else if (h.length() == 6) {
                return Color.decode("#" + h);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }
}
