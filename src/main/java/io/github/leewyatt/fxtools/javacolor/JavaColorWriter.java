package io.github.leewyatt.fxtools.javacolor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Locale;

/**
 * Renders the replacement Java source text for an edited
 * {@link JavaColorExpression}, following the format-preservation rules in
 * {@code JAVA_COLOR_PREVIEW_RESEARCH.md §4}.
 *
 * <p>Each variant dispatches to a per-shape formatter. Unchanged numeric
 * components are reused verbatim from the original PSI text; only edited
 * components are rewritten in a normalized {@code %.4f}-trimmed form.</p>
 */
public final class JavaColorWriter {

    private JavaColorWriter() {
    }

    /**
     * Produces the replacement text for {@code expression} given the new color.
     */
    @NotNull
    public static String format(@NotNull JavaColorExpression expression, @NotNull Color newColor) {
        if (expression instanceof JavaColorExpression.NamedConstant nc) {
            return formatNamedConstant(nc, newColor);
        }
        if (expression instanceof JavaColorExpression.Constructor c) {
            return formatConstructor(c, newColor);
        }
        if (expression instanceof JavaColorExpression.ColorFactory cf) {
            return formatColorFactory(cf, newColor);
        }
        if (expression instanceof JavaColorExpression.RgbFactory rgbf) {
            return formatRgbFactory(rgbf, newColor);
        }
        if (expression instanceof JavaColorExpression.HsbFactory hsbf) {
            return formatHsbFactory(hsbf, newColor);
        }
        if (expression instanceof JavaColorExpression.GrayFactory gf) {
            return formatGrayFactory(gf, newColor);
        }
        if (expression instanceof JavaColorExpression.GrayRgbFactory grf) {
            return formatGrayRgbFactory(grf, newColor);
        }
        if (expression instanceof JavaColorExpression.WebHex wh) {
            return formatWebHex(wh, newColor);
        }
        if (expression instanceof JavaColorExpression.WebNamed wn) {
            return formatWebNamed(wn, newColor);
        }
        if (expression instanceof JavaColorExpression.WebFunctional wf) {
            return formatWebFunctional(wf, newColor);
        }
        if (expression instanceof JavaColorExpression.WebTwoArg w2) {
            return formatWebTwoArg(w2, newColor);
        }
        throw new IllegalStateException("Unhandled expression type: " + expression.getClass());
    }

    // ==================== Phase 1 formatters ====================

    @NotNull
    private static String formatNamedConstant(@NotNull JavaColorExpression.NamedConstant nc,
                                              @NotNull Color newColor) {
        String matched = FxNamedColorsReverseIndex.findConstantName(newColor);
        if (matched != null) {
            return nc.qualifierText() + "." + matched;
        }
        // Decision point 2: fall back to Color.web("#rrggbbaa")
        return webFallback(nc.qualifierText(), "web", newColor);
    }

    @NotNull
    private static String formatWebHex(@NotNull JavaColorExpression.WebHex wh,
                                       @NotNull Color newColor) {
        String body = hexBodyMatchingOriginal(newColor, wh.hexLength(), wh.upperCase());
        String prefix = switch (wh.prefix()) {
            case HASH -> "#";
            case OX -> wh.upperCase() ? "0X" : "0x";
            case NONE -> "";
        };
        return wh.qualifierText() + "." + wh.methodName() + "(\"" + prefix + body + "\")";
    }

    @NotNull
    private static String formatWebNamed(@NotNull JavaColorExpression.WebNamed wn,
                                         @NotNull Color newColor) {
        String matched = FxNamedColorsReverseIndex.findConstantName(newColor);
        if (matched != null) {
            String cased = applyCaseStyle(wn.originalName(), matched);
            return wn.qualifierText() + "." + wn.methodName() + "(\"" + cased + "\")";
        }
        // Fallback (Section 4.2 B): keep the web wrapper, emit hex.
        return wn.qualifierText() + "." + wn.methodName() + "(\""
                + canonicalHexBody(newColor, "#") + "\")";
    }

    @NotNull
    private static String formatWebTwoArg(@NotNull JavaColorExpression.WebTwoArg w2,
                                          @NotNull Color newColor) {
        // Merge the second-arg opacity into the literal alpha and collapse to
        // a single-arg Color.web("#rrggbb[aa]") form.
        return webFallback(w2.qualifierText(), "web", newColor);
    }

    // ==================== Phase 2 formatters ====================

    @NotNull
    private static String formatConstructor(@NotNull JavaColorExpression.Constructor c,
                                            @NotNull Color newColor) {
        double[] newComponents = colorToDoubles(newColor);
        String[] argText = preserveOrRewriteDoubles(c.argTexts(), c.argValues(), newComponents);
        return "new " + c.classRefText() + "(" + joinComma(argText) + ")";
    }

    @NotNull
    private static String formatColorFactory(@NotNull JavaColorExpression.ColorFactory cf,
                                             @NotNull Color newColor) {
        double[] newComponents = colorToDoubles(newColor);
        int arity = cf.arity();
        boolean upgrade = arity == 3 && newColor.getAlpha() != 255;
        int outArity = upgrade ? 4 : arity;
        String[] out = new String[outArity];
        double[] paddedOld;
        String[] paddedTexts;
        if (upgrade) {
            paddedOld = new double[]{cf.argValues()[0], cf.argValues()[1], cf.argValues()[2], 1.0};
            paddedTexts = new String[]{cf.argTexts()[0], cf.argTexts()[1], cf.argTexts()[2], null};
        } else {
            paddedOld = cf.argValues();
            paddedTexts = cf.argTexts();
        }
        for (int i = 0; i < outArity; i++) {
            double newVal = i < 4 ? newComponents[i] : 1.0;
            if (paddedTexts[i] != null && doublesEqualAsByte(paddedOld[i], newVal)) {
                out[i] = paddedTexts[i];
            } else {
                out[i] = formatDouble(newVal);
            }
        }
        return cf.qualifierText() + ".color(" + joinComma(out) + ")";
    }

    @NotNull
    private static String formatRgbFactory(@NotNull JavaColorExpression.RgbFactory rgbf,
                                           @NotNull Color newColor) {
        int[] oldRgb = rgbf.rgb();
        double oldAlpha = rgbf.alpha();
        int[] newRgb = {newColor.getRed(), newColor.getGreen(), newColor.getBlue()};
        double newAlphaDouble = newColor.getAlpha() / 255.0;
        int arity = rgbf.arity();
        boolean upgrade = arity == 3 && newColor.getAlpha() != 255;
        int outArity = upgrade ? 4 : arity;
        String[] out = new String[outArity];
        for (int i = 0; i < 3; i++) {
            if (oldRgb[i] == newRgb[i]) {
                out[i] = rgbf.argTexts()[i];
            } else {
                out[i] = Integer.toString(newRgb[i]);
            }
        }
        if (outArity == 4) {
            String oldAlphaText = arity == 4 ? rgbf.argTexts()[3] : null;
            if (oldAlphaText != null && doublesEqualAsByte(oldAlpha, newAlphaDouble)) {
                out[3] = oldAlphaText;
            } else {
                out[3] = formatDouble(newAlphaDouble);
            }
        }
        return rgbf.qualifierText() + ".rgb(" + joinComma(out) + ")";
    }

    @NotNull
    private static String formatHsbFactory(@NotNull JavaColorExpression.HsbFactory hsbf,
                                           @NotNull Color newColor) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), hsb);
        double h = hsb[0] * 360.0;
        double s = hsb[1];
        double b = hsb[2];
        double alpha = newColor.getAlpha() / 255.0;
        int arity = hsbf.arity();
        boolean upgrade = arity == 3 && newColor.getAlpha() != 255;
        StringBuilder sb = new StringBuilder();
        sb.append(hsbf.qualifierText()).append(".hsb(")
                .append(formatHue(h)).append(", ")
                .append(formatDouble(s)).append(", ")
                .append(formatDouble(b));
        if (arity == 4 || upgrade) {
            sb.append(", ").append(formatDouble(alpha));
        }
        sb.append(")");
        return sb.toString();
    }

    @NotNull
    private static String formatGrayFactory(@NotNull JavaColorExpression.GrayFactory gf,
                                            @NotNull Color newColor) {
        if (isGray(newColor)) {
            double g = newColor.getRed() / 255.0;
            double a = newColor.getAlpha() / 255.0;
            int arity = gf.arity();
            boolean upgrade = arity == 1 && newColor.getAlpha() != 255;
            int outArity = upgrade ? 2 : arity;
            String[] out = new String[outArity];
            double oldG = gf.argValues()[0];
            out[0] = doublesEqualAsByte(oldG, g) ? gf.argTexts()[0] : formatDouble(g);
            if (outArity == 2) {
                String oldAlphaText = arity == 2 ? gf.argTexts()[1] : null;
                double oldAlpha = arity == 2 ? gf.argValues()[1] : 1.0;
                out[1] = (oldAlphaText != null && doublesEqualAsByte(oldAlpha, a))
                        ? oldAlphaText : formatDouble(a);
            }
            return gf.qualifierText() + ".gray(" + joinComma(out) + ")";
        }
        // Auto-upgrade to Color.color(r, g, b[, a]) to preserve the new non-gray color.
        double[] cmp = colorToDoubles(newColor);
        boolean hasAlpha = newColor.getAlpha() != 255;
        String[] out = new String[hasAlpha ? 4 : 3];
        out[0] = formatDouble(cmp[0]);
        out[1] = formatDouble(cmp[1]);
        out[2] = formatDouble(cmp[2]);
        if (hasAlpha) {
            out[3] = formatDouble(cmp[3]);
        }
        return gf.qualifierText() + ".color(" + joinComma(out) + ")";
    }

    @NotNull
    private static String formatGrayRgbFactory(@NotNull JavaColorExpression.GrayRgbFactory grf,
                                               @NotNull Color newColor) {
        if (isGray(newColor)) {
            int g = newColor.getRed();
            double a = newColor.getAlpha() / 255.0;
            int arity = grf.arity();
            boolean upgrade = arity == 1 && newColor.getAlpha() != 255;
            int outArity = upgrade ? 2 : arity;
            String[] out = new String[outArity];
            out[0] = grf.gray() == g ? grf.argTexts()[0] : Integer.toString(g);
            if (outArity == 2) {
                String oldAlphaText = arity == 2 ? grf.argTexts()[1] : null;
                double oldAlpha = grf.alpha();
                out[1] = (oldAlphaText != null && doublesEqualAsByte(oldAlpha, a))
                        ? oldAlphaText : formatDouble(a);
            }
            return grf.qualifierText() + ".grayRgb(" + joinComma(out) + ")";
        }
        // Auto-upgrade to Color.rgb(r, g, b[, a]).
        boolean hasAlpha = newColor.getAlpha() != 255;
        String[] out = new String[hasAlpha ? 4 : 3];
        out[0] = Integer.toString(newColor.getRed());
        out[1] = Integer.toString(newColor.getGreen());
        out[2] = Integer.toString(newColor.getBlue());
        if (hasAlpha) {
            out[3] = formatDouble(newColor.getAlpha() / 255.0);
        }
        return grf.qualifierText() + ".rgb(" + joinComma(out) + ")";
    }

    // ==================== Phase 3 formatter (web functional) ====================

    @NotNull
    private static String formatWebFunctional(@NotNull JavaColorExpression.WebFunctional wf,
                                              @NotNull Color newColor) {
        JavaFxWebStringParser.Subformat sf = wf.subformat();
        boolean hadAlpha = sf == JavaFxWebStringParser.Subformat.FUNC_RGBA
                || sf == JavaFxWebStringParser.Subformat.FUNC_HSLA;
        boolean newHasAlpha = newColor.getAlpha() != 255;
        boolean isRgb = sf == JavaFxWebStringParser.Subformat.FUNC_RGB
                || sf == JavaFxWebStringParser.Subformat.FUNC_RGBA;

        // alpha-upgrade-but-no-downgrade rule
        boolean emitAlpha = hadAlpha || newHasAlpha;
        String wrapper;
        if (isRgb) {
            wrapper = emitAlpha ? "rgba" : "rgb";
        } else {
            wrapper = emitAlpha ? "hsla" : "hsl";
        }

        String content;
        if (isRgb) {
            JavaFxWebStringParser.ComponentStyle[] styles = wf.rgbStyles();
            if (styles == null) {
                styles = new JavaFxWebStringParser.ComponentStyle[]{
                        JavaFxWebStringParser.ComponentStyle.INTEGER,
                        JavaFxWebStringParser.ComponentStyle.INTEGER,
                        JavaFxWebStringParser.ComponentStyle.INTEGER
                };
            }
            int[] newBytes = {newColor.getRed(), newColor.getGreen(), newColor.getBlue()};
            String[] tokens = new String[3];
            for (int i = 0; i < 3; i++) {
                tokens[i] = reuseOrFormatRgbComponent(wf, i, newBytes[i], styles[i]);
            }
            if (emitAlpha) {
                String a = formatDouble(newColor.getAlpha() / 255.0);
                content = tokens[0] + ", " + tokens[1] + ", " + tokens[2] + ", " + a;
            } else {
                content = tokens[0] + ", " + tokens[1] + ", " + tokens[2];
            }
        } else {
            // hsl/hsla: convert to HSB, emit as hsl wrapper (JavaFX parses hsl as hsb).
            float[] hsb = new float[3];
            Color.RGBtoHSB(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), hsb);
            String h = formatHue(hsb[0] * 360.0);
            String s = formatPercent(hsb[1]);
            String l = formatPercent(hsb[2]);
            if (emitAlpha) {
                String a = formatDouble(newColor.getAlpha() / 255.0);
                content = h + ", " + s + ", " + l + ", " + a;
            } else {
                content = h + ", " + s + ", " + l;
            }
        }
        return wf.qualifierText() + "." + wf.methodName()
                + "(\"" + wrapper + "(" + content + ")\")";
    }

    // ==================== Format helpers ====================

    @NotNull
    private static String webFallback(@NotNull String qualifier, @NotNull String methodName,
                                      @NotNull Color color) {
        return qualifier + "." + methodName + "(\"" + canonicalHexBody(color, "#") + "\")";
    }

    @NotNull
    private static String canonicalHexBody(@NotNull Color color, @NotNull String prefix) {
        if (color.getAlpha() == 255) {
            return String.format(Locale.ROOT, "%s%02x%02x%02x", prefix,
                    color.getRed(), color.getGreen(), color.getBlue());
        }
        return String.format(Locale.ROOT, "%s%02x%02x%02x%02x", prefix,
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /**
     * Returns the hex body (no prefix) using the most compact representation
     * compatible with the original length:
     * <ul>
     *   <li>3 → keep 3 if compressible &amp; alpha is full, else upgrade to 6/8.</li>
     *   <li>4 → keep 4 if all four channels compressible, else upgrade to 8.</li>
     *   <li>6 → 6 if alpha full, otherwise upgrade to 8.</li>
     *   <li>8 → 8 (preserve author intent to keep alpha channel).</li>
     * </ul>
     */
    @NotNull
    private static String hexBodyMatchingOriginal(@NotNull Color color, int originalLength,
                                                  boolean upperCase) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = color.getAlpha();
        boolean fullAlpha = a == 255;

        String body;
        if (originalLength == 3 && fullAlpha && isShortHexCompressible(r, g, b)) {
            body = String.format(Locale.ROOT, "%x%x%x",
                    r & 0x0F, g & 0x0F, b & 0x0F);
        } else if (originalLength == 4 && isShortHexCompressible(r, g, b, a)) {
            body = String.format(Locale.ROOT, "%x%x%x%x",
                    r & 0x0F, g & 0x0F, b & 0x0F, a & 0x0F);
        } else if (originalLength == 4 && !fullAlpha) {
            body = String.format(Locale.ROOT, "%02x%02x%02x%02x", r, g, b, a);
        } else if (originalLength <= 6 && fullAlpha) {
            body = String.format(Locale.ROOT, "%02x%02x%02x", r, g, b);
        } else {
            body = String.format(Locale.ROOT, "%02x%02x%02x%02x", r, g, b, a);
        }
        return upperCase ? body.toUpperCase(Locale.ROOT) : body;
    }

    private static boolean isShortHexCompressible(int... bytes) {
        for (int by : bytes) {
            if (((by >> 4) & 0x0F) != (by & 0x0F)) {
                return false;
            }
        }
        return true;
    }

    /**
     * If {@code original} is recognizably all-lowercase, all-uppercase, or
     * title-case, apply that style to {@code target}; otherwise lowercase.
     */
    @NotNull
    private static String applyCaseStyle(@NotNull String original, @NotNull String target) {
        boolean allUpper = original.equals(original.toUpperCase(Locale.ROOT));
        boolean allLower = original.equals(original.toLowerCase(Locale.ROOT));
        if (allUpper && !allLower) {
            return target.toUpperCase(Locale.ROOT);
        }
        if (allLower && !original.isEmpty() && Character.isLetter(original.charAt(0))) {
            return target.toLowerCase(Locale.ROOT);
        }
        if (!original.isEmpty()
                && Character.isUpperCase(original.charAt(0))
                && original.substring(1).equals(original.substring(1).toLowerCase(Locale.ROOT))) {
            // Title-case
            String lower = target.toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
        return target.toLowerCase(Locale.ROOT);
    }

    @NotNull
    private static double[] colorToDoubles(@NotNull Color color) {
        return new double[]{
                color.getRed() / 255.0,
                color.getGreen() / 255.0,
                color.getBlue() / 255.0,
                color.getAlpha() / 255.0
        };
    }

    private static boolean isGray(@NotNull Color color) {
        return color.getRed() == color.getGreen()
                && color.getGreen() == color.getBlue();
    }

    @NotNull
    private static String[] preserveOrRewriteDoubles(@NotNull String[] originalTexts,
                                                      @NotNull double[] originalValues,
                                                      @NotNull double[] newValues) {
        String[] out = new String[originalTexts.length];
        for (int i = 0; i < originalTexts.length; i++) {
            if (i < newValues.length && doublesEqualAsByte(originalValues[i], newValues[i])) {
                out[i] = originalTexts[i];
            } else {
                out[i] = formatDouble(i < newValues.length ? newValues[i] : 0.0);
            }
        }
        return out;
    }

    /**
     * Equality on the 0-255 byte representation. This is the right granularity
     * for our purposes — java.awt.Color stores 8-bit components internally,
     * and the PaintPicker hands us byte-quantized colors back.
     */
    private static boolean doublesEqualAsByte(double a, double b) {
        return Math.round(a * 255.0) == Math.round(b * 255.0);
    }

    @NotNull
    private static String formatDouble(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        return stripTrailingZeros(s);
    }

    @NotNull
    private static String formatHue(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return stripTrailingZeros(s);
    }

    @NotNull
    private static String formatPercent(double normalized) {
        return formatDouble(normalized * 100.0) + "%";
    }

    @NotNull
    private static String formatRgbComponent(int byteValue,
                                             @NotNull JavaFxWebStringParser.ComponentStyle style) {
        if (style == JavaFxWebStringParser.ComponentStyle.PERCENT) {
            return formatPercent(byteValue / 255.0);
        }
        return Integer.toString(byteValue);
    }

    /**
     * Returns the original token text verbatim if {@code newByte} matches the
     * byte the original token parsed to (research doc §4.2 golden example:
     * editing only alpha must not touch the R/G/B token text). Falls back to
     * style-driven formatting otherwise.
     */
    @NotNull
    private static String reuseOrFormatRgbComponent(@NotNull JavaColorExpression.WebFunctional wf,
                                                    int index, int newByte,
                                                    @NotNull JavaFxWebStringParser.ComponentStyle style) {
        String[] tokens = wf.rgbTokens();
        int[] bytes = wf.rgbBytes();
        if (tokens != null && bytes != null
                && index < tokens.length && index < bytes.length
                && bytes[index] == newByte
                && tokens[index] != null) {
            return tokens[index];
        }
        return formatRgbComponent(newByte, style);
    }

    /**
     * Strips trailing zeros after the decimal point, keeping at least one
     * digit (so {@code "1.0000"} becomes {@code "1.0"}, not {@code "1."}).
     */
    @NotNull
    private static String stripTrailingZeros(@NotNull String s) {
        int dot = s.indexOf('.');
        if (dot < 0) {
            return s;
        }
        int end = s.length();
        while (end > dot + 2 && s.charAt(end - 1) == '0') {
            end--;
        }
        return s.substring(0, end);
    }

    @NotNull
    private static String joinComma(@Nullable String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
