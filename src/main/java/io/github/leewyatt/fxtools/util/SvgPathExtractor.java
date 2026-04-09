package io.github.leewyatt.fxtools.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and merges SVG path data from SVG files.
 *
 * <p>Supports two modes via the {@code convertShapes} flag:</p>
 * <ul>
 *   <li>{@code false} — only extracts {@code <path>} elements</li>
 *   <li>{@code true} — also converts basic shapes ({@code rect}, {@code circle},
 *       {@code ellipse}, {@code line}, {@code polyline}, {@code polygon}) to path data</li>
 * </ul>
 *
 * <p>Multiple paths are merged by space-concatenating their {@code d} values.
 * Each sub-path's own {@code Z} commands are preserved as-is; no extra {@code Z}
 * is appended.</p>
 */
public final class SvgPathExtractor {

    private static final Pattern NUM_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    /** SVG element types that can be extracted. */
    public enum ShapeType {
        PATH, RECT, CIRCLE, ELLIPSE, LINE, POLYLINE, POLYGON
    }

    /**
     * Result of analyzing an SVG document.
     *
     * @param counts                per-type element counts
     * @param hasStrokeOnlyElements {@code true} if any element has fill=none with a stroke
     */
    public record SvgAnalysis(@NotNull Map<ShapeType, Integer> counts,
                              boolean hasStrokeOnlyElements) {

        /**
         * Returns the total number of detected elements across all types.
         */
        public int total() {
            int sum = 0;
            for (int v : counts.values()) {
                sum += v;
            }
            return sum;
        }

        /**
         * Returns the count of non-path shape elements (rect, circle, etc.).
         */
        public int shapeCount() {
            int sum = 0;
            for (var entry : counts.entrySet()) {
                if (entry.getKey() != ShapeType.PATH) {
                    sum += entry.getValue();
                }
            }
            return sum;
        }

        /**
         * Returns the count of path elements.
         */
        public int pathCount() {
            return counts.getOrDefault(ShapeType.PATH, 0);
        }
    }

    private SvgPathExtractor() {
    }

    /**
     * Extracts merged path data from an SVG file.
     *
     * @param file          the SVG file
     * @param convertShapes if {@code true}, basic shapes are converted to path data
     * @return merged path data string, or {@code null} if no paths found or parsing fails
     */
    @Nullable
    public static String extract(@NotNull File file, boolean convertShapes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            return extractFromDocument(doc, convertShapes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts merged path data from SVG content string.
     *
     * @param svgContent    the SVG XML content
     * @param convertShapes if {@code true}, basic shapes are converted to path data
     * @return merged path data string, or {@code null} if no paths found or parsing fails
     */
    @Nullable
    public static String extract(@NotNull String svgContent, boolean convertShapes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                    new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));
            return extractFromDocument(doc, convertShapes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Analyzes an SVG content string and returns counts of each element type.
     *
     * @param svgContent the SVG XML content
     * @return analysis result, or {@code null} if parsing fails
     */
    @Nullable
    public static SvgAnalysis analyze(@NotNull String svgContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                    new ByteArrayInputStream(svgContent.getBytes(StandardCharsets.UTF_8)));
            Map<ShapeType, Integer> counts = new EnumMap<>(ShapeType.class);
            boolean[] strokeOnly = {false};
            countElements(doc.getDocumentElement(), counts, strokeOnly);
            return new SvgAnalysis(counts, strokeOnly[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static void countElements(@NotNull Element element,
                                      @NotNull Map<ShapeType, Integer> counts,
                                      boolean @NotNull [] strokeOnly) {
        String tag = element.getTagName().toLowerCase();
        int colonIdx = tag.indexOf(':');
        if (colonIdx >= 0) {
            tag = tag.substring(colonIdx + 1);
        }

        ShapeType type = switch (tag) {
            case "path" -> {
                String d = element.getAttribute("d");
                yield (d != null && !d.isBlank()) ? ShapeType.PATH : null;
            }
            case "rect" -> ShapeType.RECT;
            case "circle" -> ShapeType.CIRCLE;
            case "ellipse" -> ShapeType.ELLIPSE;
            case "line" -> ShapeType.LINE;
            case "polyline" -> ShapeType.POLYLINE;
            case "polygon" -> ShapeType.POLYGON;
            default -> null;
        };
        if (type != null) {
            counts.merge(type, 1, Integer::sum);
            // Detect stroke-only elements: fill="none" with a stroke attribute
            if (!strokeOnly[0]) {
                String fill = element.getAttribute("fill");
                String stroke = element.getAttribute("stroke");
                if ("none".equalsIgnoreCase(fill) && stroke != null && !stroke.isBlank()
                        && !"none".equalsIgnoreCase(stroke)) {
                    strokeOnly[0] = true;
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                countElements(childElement, counts, strokeOnly);
            }
        }
    }

    @Nullable
    private static String extractFromDocument(@NotNull Document doc, boolean convertShapes) {
        List<String> paths = new ArrayList<>();
        collectPaths(doc.getDocumentElement(), convertShapes, paths);
        if (paths.isEmpty()) {
            return null;
        }
        return String.join(" ", paths);
    }

    private static void collectPaths(@NotNull Element element, boolean convertShapes,
                                     @NotNull List<String> paths) {
        String tag = element.getTagName().toLowerCase();
        // Strip namespace prefix (e.g. "svg:path" → "path")
        int colonIdx = tag.indexOf(':');
        if (colonIdx >= 0) {
            tag = tag.substring(colonIdx + 1);
        }

        switch (tag) {
            case "path" -> {
                String d = element.getAttribute("d");
                if (d != null && !d.isBlank()) {
                    paths.add(d.trim());
                }
            }
            case "rect" -> {
                if (convertShapes) {
                    String pathData = rectToPath(element);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            case "circle" -> {
                if (convertShapes) {
                    String pathData = circleToPath(element);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            case "ellipse" -> {
                if (convertShapes) {
                    String pathData = ellipseToPath(element);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            case "line" -> {
                if (convertShapes) {
                    String pathData = lineToPath(element);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            case "polyline" -> {
                if (convertShapes) {
                    String pathData = polyToPath(element, false);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            case "polygon" -> {
                if (convertShapes) {
                    String pathData = polyToPath(element, true);
                    if (pathData != null) {
                        paths.add(pathData);
                    }
                }
            }
            default -> {
                // Skip non-graphic elements
            }
        }

        // Recurse into child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                collectPaths(childElement, convertShapes, paths);
            }
        }
    }

    // ==================== Shape to Path Conversions ====================

    /**
     * Converts {@code <rect x y width height rx ry>} to path data.
     * Supports rounded corners via {@code rx}/{@code ry} attributes.
     */
    @Nullable
    private static String rectToPath(@NotNull Element el) {
        double x = getAttr(el, "x", 0);
        double y = getAttr(el, "y", 0);
        double w = getAttr(el, "width", -1);
        double h = getAttr(el, "height", -1);
        if (w <= 0 || h <= 0) {
            return null;
        }

        double rx = getAttr(el, "rx", 0);
        double ry = getAttr(el, "ry", 0);
        // SVG spec: if only one is specified, the other defaults to the same value
        if (rx > 0 && ry == 0) {
            ry = rx;
        }
        if (ry > 0 && rx == 0) {
            rx = ry;
        }
        // Clamp to half of width/height
        rx = Math.min(rx, w / 2);
        ry = Math.min(ry, h / 2);

        if (rx == 0 && ry == 0) {
            // Simple rectangle
            return fmt("M%s,%s H%s V%s H%s Z",
                    x, y, x + w, y + h, x);
        }

        // Rounded rectangle using arc commands
        return fmt("M%s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s Z",
                x + rx, y,
                x + w - rx,
                rx, ry, x + w, y + ry,
                y + h - ry,
                rx, ry, x + w - rx, y + h,
                x + rx,
                rx, ry, x, y + h - ry,
                y + ry,
                rx, ry, x + rx, y);
    }

    /**
     * Converts {@code <circle cx cy r>} to path data using two arc commands.
     */
    @Nullable
    private static String circleToPath(@NotNull Element el) {
        double cx = getAttr(el, "cx", 0);
        double cy = getAttr(el, "cy", 0);
        double r = getAttr(el, "r", -1);
        if (r <= 0) {
            return null;
        }
        return fmt("M%s,%s A%s,%s 0 1 0 %s,%s A%s,%s 0 1 0 %s,%s Z",
                cx - r, cy,
                r, r, cx + r, cy,
                r, r, cx - r, cy);
    }

    /**
     * Converts {@code <ellipse cx cy rx ry>} to path data using two arc commands.
     */
    @Nullable
    private static String ellipseToPath(@NotNull Element el) {
        double cx = getAttr(el, "cx", 0);
        double cy = getAttr(el, "cy", 0);
        double rx = getAttr(el, "rx", -1);
        double ry = getAttr(el, "ry", -1);
        if (rx <= 0 || ry <= 0) {
            return null;
        }
        return fmt("M%s,%s A%s,%s 0 1 0 %s,%s A%s,%s 0 1 0 %s,%s Z",
                cx - rx, cy,
                rx, ry, cx + rx, cy,
                rx, ry, cx - rx, cy);
    }

    /**
     * Converts {@code <line x1 y1 x2 y2>} to path data.
     */
    @Nullable
    private static String lineToPath(@NotNull Element el) {
        double x1 = getAttr(el, "x1", 0);
        double y1 = getAttr(el, "y1", 0);
        double x2 = getAttr(el, "x2", 0);
        double y2 = getAttr(el, "y2", 0);
        if (x1 == x2 && y1 == y2) {
            return null;
        }
        return fmt("M%s,%s L%s,%s", x1, y1, x2, y2);
    }

    /**
     * Converts {@code <polyline>} or {@code <polygon>} to path data.
     *
     * @param close {@code true} for polygon (appends Z), {@code false} for polyline
     */
    @Nullable
    private static String polyToPath(@NotNull Element el, boolean close) {
        String points = el.getAttribute("points");
        if (points == null || points.isBlank()) {
            return null;
        }
        List<Double> nums = parseNumbers(points);
        if (nums.size() < 4) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(fmt("M%s,%s", nums.get(0), nums.get(1)));
        for (int i = 2; i + 1 < nums.size(); i += 2) {
            sb.append(fmt(" L%s,%s", nums.get(i), nums.get(i + 1)));
        }
        if (close) {
            sb.append(" Z");
        }
        return sb.toString();
    }

    // ==================== Helpers ====================

    private static double getAttr(@NotNull Element el, @NotNull String name, double defaultValue) {
        String value = el.getAttribute(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        // Strip common SVG units (px, pt, em, etc.) — use numeric part only
        value = value.trim().replaceAll("[a-zA-Z%]+$", "");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @NotNull
    private static List<Double> parseNumbers(@NotNull String text) {
        List<Double> numbers = new ArrayList<>();
        Matcher m = NUM_PATTERN.matcher(text);
        while (m.find()) {
            try {
                numbers.add(Double.parseDouble(m.group()));
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return numbers;
    }

    /**
     * Formats doubles without unnecessary trailing zeros.
     * {@code String.format} would produce locale-dependent output; this avoids that.
     */
    @NotNull
    private static String fmt(@NotNull String template, Object... args) {
        Object[] formatted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Double d) {
                formatted[i] = formatNumber(d);
            } else {
                formatted[i] = args[i];
            }
        }
        return String.format(template, formatted);
    }

    @NotNull
    private static String formatNumber(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        // Up to 4 decimal places, strip trailing zeros
        String s = String.format(Locale.US, "%.4f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }
}
