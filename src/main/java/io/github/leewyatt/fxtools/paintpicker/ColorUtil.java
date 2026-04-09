package io.github.leewyatt.fxtools.paintpicker;

import java.awt.Color;

/**
 * Utility class for color conversions.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /**
     * Converts a hex string to Color.
     *
     * @param hexColor hex string like "#RRGGBB", "#RRGGBBAA", or "#RGB"
     * @return the parsed color, or {@link Color#BLACK} if invalid
     */
    public static Color webHexToColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return Color.BLACK;
        }
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        int len = hexColor.length();
        int r, g, b, a = 255;
        if (len == 6) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
        } else if (len == 8) {
            r = Integer.parseInt(hexColor.substring(0, 2), 16);
            g = Integer.parseInt(hexColor.substring(2, 4), 16);
            b = Integer.parseInt(hexColor.substring(4, 6), 16);
            a = Integer.parseInt(hexColor.substring(6, 8), 16);
        } else if (len == 3) {
            r = Integer.parseInt(hexColor.substring(0, 1).repeat(2), 16);
            g = Integer.parseInt(hexColor.substring(1, 2).repeat(2), 16);
            b = Integer.parseInt(hexColor.substring(2, 3).repeat(2), 16);
        } else {
            return Color.BLACK;
        }
        return new Color(r, g, b, a);
    }
}
