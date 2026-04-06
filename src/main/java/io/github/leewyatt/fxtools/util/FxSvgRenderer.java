package io.github.leewyatt.fxtools.util;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SVG path data and renders it to icons.
 */
public final class FxSvgRenderer {

    private static final Pattern SVG_PATH_CHARS =
            Pattern.compile("[MmLlHhVvCcSsQqTtAaZz0-9\\s,\\.\\-+eE]+");

    /**
     * Checks if a string looks like valid SVG path data.
     */
    public static boolean isSvgPath(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String trimmed = s.trim();
        char first = trimmed.charAt(0);
        if (first != 'M' && first != 'm') {
            return false;
        }
        return SVG_PATH_CHARS.matcher(trimmed).matches();
    }

    private static final Pattern CMD_PATTERN =
            Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)");
    private static final Pattern NUM_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private FxSvgRenderer() {
    }

    /**
     * Parses SVG path data and renders it to an Icon.
     */
    @Nullable
    public static Icon renderSvgPathIcon(@NotNull String pathData, int size, @NotNull Color color) {
        GeneralPath path = parseSvgPath(pathData);
        if (path == null) {
            return null;
        }

        Rectangle2D bounds = path.getBounds2D();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return null;
        }

        double scale = Math.min((size - 2) / bounds.getWidth(), (size - 2) / bounds.getHeight());
        double tx = (size - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
        double ty = (size - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;

        AffineTransform transform = new AffineTransform();
        transform.translate(tx, ty);
        transform.scale(scale, scale);

        @SuppressWarnings("UndesirableClassUsage") // Wrapped in ImageIcon — must match logical pixel size
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fill(transform.createTransformedShape(path));
        g2.dispose();

        return new ImageIcon(img);
    }

    /**
     * Parses SVG path data string to GeneralPath.
     */
    @Nullable
    public static GeneralPath parseSvgPath(@NotNull String pathData) {
        GeneralPath path = new GeneralPath();
        float cx = 0, cy = 0;
        float lastCx2 = 0, lastCy2 = 0;
        char lastCmd = 0;

        Matcher cmdMatcher = CMD_PATTERN.matcher(pathData);
        while (cmdMatcher.find()) {
            char cmd = cmdMatcher.group(1).charAt(0);
            List<Float> nums = parseNumbers(cmdMatcher.group(2));
            boolean relative = Character.isLowerCase(cmd);
            char upper = Character.toUpperCase(cmd);

            try {
                switch (upper) {
                    case 'M': {
                        for (int i = 0; i + 1 < nums.size(); i += 2) {
                            float x = nums.get(i) + (relative && i > 0 ? cx : (relative ? cx : 0));
                            float y = nums.get(i + 1) + (relative && i > 0 ? cy : (relative ? cy : 0));
                            if (i == 0 && !relative) {
                                x = nums.get(0);
                                y = nums.get(1);
                            } else if (i == 0) {
                                x = cx + nums.get(0);
                                y = cy + nums.get(1);
                            }
                            if (i == 0) {
                                path.moveTo(x, y);
                            } else {
                                path.lineTo(x, y);
                            }
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'L': {
                        for (int i = 0; i + 1 < nums.size(); i += 2) {
                            float x = relative ? cx + nums.get(i) : nums.get(i);
                            float y = relative ? cy + nums.get(i + 1) : nums.get(i + 1);
                            path.lineTo(x, y);
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'H': {
                        for (Float n : nums) {
                            float x = relative ? cx + n : n;
                            path.lineTo(x, cy);
                            cx = x;
                        }
                        break;
                    }
                    case 'V': {
                        for (Float n : nums) {
                            float y = relative ? cy + n : n;
                            path.lineTo(cx, y);
                            cy = y;
                        }
                        break;
                    }
                    case 'C': {
                        for (int i = 0; i + 5 < nums.size(); i += 6) {
                            float x1 = relative ? cx + nums.get(i) : nums.get(i);
                            float y1 = relative ? cy + nums.get(i + 1) : nums.get(i + 1);
                            float x2 = relative ? cx + nums.get(i + 2) : nums.get(i + 2);
                            float y2 = relative ? cy + nums.get(i + 3) : nums.get(i + 3);
                            float x = relative ? cx + nums.get(i + 4) : nums.get(i + 4);
                            float y = relative ? cy + nums.get(i + 5) : nums.get(i + 5);
                            path.curveTo(x1, y1, x2, y2, x, y);
                            lastCx2 = x2;
                            lastCy2 = y2;
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'S': {
                        for (int i = 0; i + 3 < nums.size(); i += 4) {
                            float x1 = (lastCmd == 'C' || lastCmd == 'S') ? 2 * cx - lastCx2 : cx;
                            float y1 = (lastCmd == 'C' || lastCmd == 'S') ? 2 * cy - lastCy2 : cy;
                            float x2 = relative ? cx + nums.get(i) : nums.get(i);
                            float y2 = relative ? cy + nums.get(i + 1) : nums.get(i + 1);
                            float x = relative ? cx + nums.get(i + 2) : nums.get(i + 2);
                            float y = relative ? cy + nums.get(i + 3) : nums.get(i + 3);
                            path.curveTo(x1, y1, x2, y2, x, y);
                            lastCx2 = x2;
                            lastCy2 = y2;
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'Q': {
                        for (int i = 0; i + 3 < nums.size(); i += 4) {
                            float x1 = relative ? cx + nums.get(i) : nums.get(i);
                            float y1 = relative ? cy + nums.get(i + 1) : nums.get(i + 1);
                            float x = relative ? cx + nums.get(i + 2) : nums.get(i + 2);
                            float y = relative ? cy + nums.get(i + 3) : nums.get(i + 3);
                            path.quadTo(x1, y1, x, y);
                            lastCx2 = x1;
                            lastCy2 = y1;
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'T': {
                        for (int i = 0; i + 1 < nums.size(); i += 2) {
                            float x1 = (lastCmd == 'Q' || lastCmd == 'T') ? 2 * cx - lastCx2 : cx;
                            float y1 = (lastCmd == 'Q' || lastCmd == 'T') ? 2 * cy - lastCy2 : cy;
                            float x = relative ? cx + nums.get(i) : nums.get(i);
                            float y = relative ? cy + nums.get(i + 1) : nums.get(i + 1);
                            path.quadTo(x1, y1, x, y);
                            lastCx2 = x1;
                            lastCy2 = y1;
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'A': {
                        // Arc command — skip segments (complex arc-to-bezier conversion)
                        for (int i = 0; i + 6 < nums.size(); i += 7) {
                            float x = relative ? cx + nums.get(i + 5) : nums.get(i + 5);
                            float y = relative ? cy + nums.get(i + 6) : nums.get(i + 6);
                            path.lineTo(x, y);
                            cx = x;
                            cy = y;
                        }
                        break;
                    }
                    case 'Z': {
                        path.closePath();
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                return null;
            }
            lastCmd = upper;
        }

        return path;
    }

    @NotNull
    private static List<Float> parseNumbers(@NotNull String text) {
        List<Float> numbers = new ArrayList<>();
        Matcher m = NUM_PATTERN.matcher(text);
        while (m.find()) {
            try {
                numbers.add(Float.parseFloat(m.group()));
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return numbers;
    }
}
