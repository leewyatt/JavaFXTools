package io.github.leewyatt.fxtools.javacolor;

import io.github.leewyatt.fxtools.css.FxNamedColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Locale;

/**
 * Parses the {@code String} payload of {@code Color.web("...")} or
 * {@code Color.valueOf("...")}, mirroring the JavaFX runtime behavior in
 * {@code javafx.scene.paint.Color#web(String, double)}.
 *
 * <p>Returns a structured {@link Result} that records the parsed color together
 * with metadata about the original sub-format (hex flavor, named, rgb/hsl
 * functional, per-component style) so that write-back can preserve the
 * author's original syntax.</p>
 *
 * <p>This parser intentionally does <b>not</b> delegate to
 * {@code ColorUtil.webHexToColor} or {@code FxColorParser}: the JavaFX runtime
 * accepts {@code 0x}/bare hex prefixes, 4-digit RGBA short codes, and a
 * {@code hsl()} function that is semantically {@code hsb()} — none of which
 * those CSS-oriented helpers handle.</p>
 */
public final class JavaFxWebStringParser {

    /** Subformat of a {@code Color.web} string. */
    public enum Subformat {
        HEX_HASH, HEX_0X, HEX_BARE, NAMED,
        FUNC_RGB, FUNC_RGBA, FUNC_HSL, FUNC_HSLA
    }

    /** Original prefix of a hex value. */
    public enum HexPrefix { HASH, OX, NONE }

    /** Style of a single component inside a functional form (RGB/RGBA only). */
    public enum ComponentStyle { INTEGER, PERCENT }

    /**
     * Parsed result. {@code color} is always non-null. The remaining fields
     * carry sub-format information used by the writer:
     *
     * <ul>
     *   <li>For hex inputs — {@code hexPrefix}, {@code hexLength} (3/4/6/8),
     *       {@code hexUpperCase}.</li>
     *   <li>For named — {@code namedOriginal} (raw author text, preserving case).</li>
     *   <li>For functional rgb/rgba — {@code rgbStyles} of length 3,
     *       {@code rgbTokens} of length 3 (trimmed original token text), and
     *       {@code rgbBytes} of length 3 (the 0-255 value each token parsed to,
     *       used to detect unchanged components on write-back).</li>
     * </ul>
     */
    public static final class Result {
        public final Color color;
        public final Subformat subformat;
        public final HexPrefix hexPrefix;
        public final int hexLength;
        public final boolean hexUpperCase;
        public final String namedOriginal;
        public final ComponentStyle[] rgbStyles;
        public final String[] rgbTokens;
        public final int[] rgbBytes;

        private Result(Color color, Subformat subformat, HexPrefix hexPrefix,
                       int hexLength, boolean hexUpperCase, String namedOriginal,
                       ComponentStyle[] rgbStyles, String[] rgbTokens, int[] rgbBytes) {
            this.color = color;
            this.subformat = subformat;
            this.hexPrefix = hexPrefix;
            this.hexLength = hexLength;
            this.hexUpperCase = hexUpperCase;
            this.namedOriginal = namedOriginal;
            this.rgbStyles = rgbStyles;
            this.rgbTokens = rgbTokens;
            this.rgbBytes = rgbBytes;
        }
    }

    private JavaFxWebStringParser() {
    }

    /**
     * Parses with implicit {@code opacity = 1.0}.
     */
    @Nullable
    public static Result parse(@NotNull String s) {
        return parse(s, 1.0);
    }

    /**
     * Parses with the second-argument {@code opacity} multiplier applied to
     * the resulting alpha channel, mirroring {@code Color.web(s, opacity)}.
     */
    @Nullable
    public static Result parse(@NotNull String raw, double opacity) {
        if (raw.isEmpty()) {
            return null;
        }
        // Mirror the upstream Color.web behavior: no overall trim; just lowercase.
        // Whitespace inside functional component tokens is handled per-component
        // in parseComponent.
        String input = raw;
        String lower = input.toLowerCase(Locale.ROOT);
        HexPrefix prefix;
        int hexStart;
        if (lower.startsWith("#")) {
            prefix = HexPrefix.HASH;
            hexStart = 1;
        } else if (lower.startsWith("0x")) {
            prefix = HexPrefix.OX;
            hexStart = 2;
        } else if (lower.startsWith("rgb")) {
            if (lower.startsWith("(", 3)) {
                return parseFunctional(lower, 4, false, true, opacity);
            } else if (lower.startsWith("a(", 3)) {
                return parseFunctional(lower, 5, true, true, opacity);
            }
            return null;
        } else if (lower.startsWith("hsl")) {
            if (lower.startsWith("(", 3)) {
                return parseFunctional(lower, 4, false, false, opacity);
            } else if (lower.startsWith("a(", 3)) {
                return parseFunctional(lower, 5, true, false, opacity);
            }
            return null;
        } else {
            // Named OR bare hex. JavaFX checks NamedColors first then falls
            // through to bare-hex parsing.
            String named = FxNamedColors.getHexColor(lower);
            if (named != null) {
                Color nc = hexToColor(named.substring(1), 1.0);
                if (nc == null) {
                    return null;
                }
                Color withOpacity = withAlphaMultiplier(nc, opacity);
                return new Result(withOpacity, Subformat.NAMED, null, 0, false,
                        raw, null, null, null);
            }
            prefix = HexPrefix.NONE;
            hexStart = 0;
        }

        // Hex body
        String body = input.substring(hexStart);
        int len = body.length();
        if (len != 3 && len != 4 && len != 6 && len != 8) {
            return null;
        }
        Color base = hexToColor(body, opacity);
        if (base == null) {
            return null;
        }
        boolean upper = isHexUpperCase(body);
        Subformat sf = switch (prefix) {
            case HASH -> Subformat.HEX_HASH;
            case OX -> Subformat.HEX_0X;
            case NONE -> Subformat.HEX_BARE;
        };
        return new Result(base, sf, prefix, len, upper, null, null, null, null);
    }

    /** Parses a hex body (no prefix) of length 3/4/6/8 with optional opacity multiplier. */
    @Nullable
    private static Color hexToColor(@NotNull String body, double opacity) {
        try {
            int len = body.length();
            int r, g, b, a;
            if (len == 3) {
                r = expand4(Integer.parseInt(body.substring(0, 1), 16));
                g = expand4(Integer.parseInt(body.substring(1, 2), 16));
                b = expand4(Integer.parseInt(body.substring(2, 3), 16));
                a = clampByte(Math.round(opacity * 255.0));
            } else if (len == 4) {
                r = expand4(Integer.parseInt(body.substring(0, 1), 16));
                g = expand4(Integer.parseInt(body.substring(1, 2), 16));
                b = expand4(Integer.parseInt(body.substring(2, 3), 16));
                int rawA = expand4(Integer.parseInt(body.substring(3, 4), 16));
                a = clampByte(Math.round(opacity * rawA));
            } else if (len == 6) {
                r = Integer.parseInt(body.substring(0, 2), 16);
                g = Integer.parseInt(body.substring(2, 4), 16);
                b = Integer.parseInt(body.substring(4, 6), 16);
                a = clampByte(Math.round(opacity * 255.0));
            } else if (len == 8) {
                r = Integer.parseInt(body.substring(0, 2), 16);
                g = Integer.parseInt(body.substring(2, 4), 16);
                b = Integer.parseInt(body.substring(4, 6), 16);
                int rawA = Integer.parseInt(body.substring(6, 8), 16);
                a = clampByte(Math.round(opacity * rawA));
            } else {
                return null;
            }
            return new Color(r, g, b, a);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static int expand4(int half) {
        return (half << 4) | half;
    }

    private static int clampByte(long v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static boolean isHexUpperCase(@NotNull String body) {
        boolean sawLetter = false;
        boolean allUpper = true;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                sawLetter = true;
                if (Character.isLowerCase(c)) {
                    allUpper = false;
                    break;
                }
            }
        }
        return sawLetter && allUpper;
    }

    @NotNull
    private static Color withAlphaMultiplier(@NotNull Color base, double opacity) {
        if (opacity >= 1.0 && base.getAlpha() == 255) {
            return base;
        }
        int newA = clampByte(Math.round(opacity * base.getAlpha()));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), newA);
    }

    // ==================== Functional rgb/rgba/hsl/hsla ====================

    @Nullable
    private static Result parseFunctional(@NotNull String lower, int firstOff,
                                          boolean hasAlpha, boolean isRgb,
                                          double opacity) {
        try {
            int p0End = lower.indexOf(',', firstOff);
            int p1End = p0End < 0 ? -1 : lower.indexOf(',', p0End + 1);
            int p2End = p1End < 0 ? -1 :
                    lower.indexOf(hasAlpha ? ',' : ')', p1End + 1);
            int aEnd = hasAlpha ? (p2End < 0 ? -1 : lower.indexOf(')', p2End + 1))
                    : p2End;
            if (aEnd < 0) {
                return null;
            }

            if (isRgb) {
                ComponentInfo r = parseComponent(lower, firstOff, p0End, false, false);
                ComponentInfo g = parseComponent(lower, p0End + 1, p1End, false, false);
                ComponentInfo b = parseComponent(lower, p1End + 1, p2End, false, false);
                if (r == null || g == null || b == null) {
                    return null;
                }
                double a = opacity;
                if (hasAlpha) {
                    ComponentInfo aInfo = parseComponent(lower, p2End + 1, aEnd, true, false);
                    if (aInfo == null) {
                        return null;
                    }
                    a *= clampAlpha(aInfo.value);
                }
                int rb = clampByte(Math.round(r.value * 255.0));
                int gb = clampByte(Math.round(g.value * 255.0));
                int bb = clampByte(Math.round(b.value * 255.0));
                int ab = clampByte(Math.round(a * 255.0));
                Color color = new Color(rb, gb, bb, ab);
                ComponentStyle[] styles = new ComponentStyle[]{
                        r.percent ? ComponentStyle.PERCENT : ComponentStyle.INTEGER,
                        g.percent ? ComponentStyle.PERCENT : ComponentStyle.INTEGER,
                        b.percent ? ComponentStyle.PERCENT : ComponentStyle.INTEGER
                };
                String[] tokens = new String[]{r.originalText, g.originalText, b.originalText};
                int[] bytes = new int[]{rb, gb, bb};
                Subformat sf = hasAlpha ? Subformat.FUNC_RGBA : Subformat.FUNC_RGB;
                return new Result(color, sf, null, 0, false, null, styles, tokens, bytes);
            } else {
                // hsl(h, s%, l%) — JavaFX treats this as hsb(h, s, l, a).
                ComponentInfo h = parseComponent(lower, firstOff, p0End, false, true);
                ComponentInfo s = parseComponent(lower, p0End + 1, p1End, false, false);
                ComponentInfo l = parseComponent(lower, p1End + 1, p2End, false, false);
                if (h == null || s == null || l == null) {
                    return null;
                }
                if (!s.percent || !l.percent) {
                    // JavaFX requires S/L as percentages.
                    return null;
                }
                double a = opacity;
                if (hasAlpha) {
                    ComponentInfo aInfo = parseComponent(lower, p2End + 1, aEnd, true, false);
                    if (aInfo == null) {
                        return null;
                    }
                    a *= clampAlpha(aInfo.value);
                }
                double hue = normalizeAngle(h.value);
                double sat = Math.max(0.0, Math.min(1.0, s.value));
                double bri = Math.max(0.0, Math.min(1.0, l.value));
                Color color = hsbToColor(hue, sat, bri, clampAlpha(a));
                Subformat sf = hasAlpha ? Subformat.FUNC_HSLA : Subformat.FUNC_HSL;
                return new Result(color, sf, null, 0, false, null, null, null, null);
            }
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Parses a single component token. {@code isAlpha} disallows percent.
     * {@code isAngle} keeps the raw degree value (no percent allowed); other
     * non-alpha values are normalized to {@code [0,1]} (percent → /100, int → /255).
     */
    @Nullable
    private static ComponentInfo parseComponent(@NotNull String src, int from, int to,
                                                boolean isAlpha, boolean isAngle) {
        String original = src.substring(from, to).trim();
        if (original.isEmpty()) {
            return null;
        }
        String token = original;
        boolean percent = token.endsWith("%");
        if (percent) {
            if (isAlpha || isAngle) {
                return null;
            }
            token = token.substring(0, token.length() - 1).trim();
        }
        try {
            if (isAngle) {
                double d = Double.parseDouble(token);
                return new ComponentInfo(d, false, original);
            }
            if (percent) {
                double d = Double.parseDouble(token);
                double clamped = d <= 0.0 ? 0.0 : (d >= 100.0 ? 1.0 : d / 100.0);
                return new ComponentInfo(clamped, true, original);
            }
            if (isAlpha) {
                double d = Double.parseDouble(token);
                return new ComponentInfo(d, false, original);
            }
            // RGB int form
            int i = Integer.parseInt(token);
            double v = i <= 0 ? 0.0 : (i >= 255 ? 1.0 : i / 255.0);
            return new ComponentInfo(v, false, original);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static double clampAlpha(double a) {
        return a < 0.0 ? 0.0 : (a > 1.0 ? 1.0 : a);
    }

    /** Mirror of {@code Color.java#parseComponent PARSE_ANGLE} behavior. */
    private static double normalizeAngle(double c) {
        if (c < 0.0) {
            return (c % 360.0) + 360.0;
        }
        if (c > 360.0) {
            return c % 360.0;
        }
        return c;
    }

    @NotNull
    private static Color hsbToColor(double hue, double saturation, double brightness, double alpha) {
        int rgbInt = Color.HSBtoRGB((float) (hue / 360.0),
                (float) saturation, (float) brightness);
        int a = clampByte(Math.round(alpha * 255.0));
        return new Color((rgbInt >> 16) & 0xFF, (rgbInt >> 8) & 0xFF, rgbInt & 0xFF, a);
    }

    private static final class ComponentInfo {
        final double value;
        final boolean percent;
        final String originalText;
        ComponentInfo(double value, boolean percent, String originalText) {
            this.value = value;
            this.percent = percent;
            this.originalText = originalText;
        }
    }
}
