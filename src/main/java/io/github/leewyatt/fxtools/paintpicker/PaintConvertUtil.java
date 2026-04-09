package io.github.leewyatt.fxtools.paintpicker;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;

/**
 * Converts Paint objects to CSS and Java code representations.
 */
public final class PaintConvertUtil {

    private static final int ROUNDING_FACTOR = 10000;

    private PaintConvertUtil() {
    }

    public static String convertPaintToCss(Paint paint) {
        if (paint instanceof LinearGradientPaint lg) {
            float[] fractions = lg.getFractions();
            Color[] colors = lg.getColors();
            double startX = lg.getStartPoint().getX();
            double startY = lg.getStartPoint().getY();
            double endX = lg.getEndPoint().getX();
            double endY = lg.getEndPoint().getY();
            StringBuilder sb = new StringBuilder("linear-gradient(from ")
                    .append(lenToStr(startX, true)).append(" ").append(lenToStr(startY, true))
                    .append(" to ").append(lenToStr(endX, true)).append(" ").append(lenToStr(endY, true))
                    .append(", ");
            appendCycleMethodAndStops(sb, lg.getCycleMethod(), fractions, colors);
            return sb.toString();
        } else if (paint instanceof RadialGradientPaint rg) {
            float[] fractions = rg.getFractions();
            Color[] colors = rg.getColors();
            StringBuilder sb = new StringBuilder("radial-gradient(focus-angle ")
                    .append(round(rg.getFocusPoint().getX()))
                    .append("deg, focus-distance ").append(round(0))
                    .append("% , center ").append(lenToStr(rg.getCenterPoint().getX(), true))
                    .append(" ").append(lenToStr(rg.getCenterPoint().getY(), true))
                    .append(", radius ").append(lenToStr(rg.getRadius(), true))
                    .append(", ");
            appendCycleMethodAndStops(sb, rg.getCycleMethod(), fractions, colors);
            return sb.toString();
        } else if (paint instanceof Color color) {
            return toHex(color);
        }
        return "";
    }

    private static void appendCycleMethodAndStops(StringBuilder sb,
                                                   MultipleGradientPaint.CycleMethod cycleMethod,
                                                   float[] fractions, Color[] colors) {
        switch (cycleMethod) {
            case REFLECT -> sb.append("reflect, ");
            case REPEAT -> sb.append("repeat, ");
            default -> {}
        }
        for (int i = 0; i < fractions.length; i++) {
            sb.append(toHex(colors[i])).append(" ").append(round(fractions[i] * 100.0)).append("%");
            if (i < fractions.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
    }

    private static String lenToStr(double num, boolean proportional) {
        return proportional ? round(num * 100.0) + "%" : num + "px";
    }

    private static double round(double num) {
        return Math.round(num * ROUNDING_FACTOR) / (double) ROUNDING_FACTOR;
    }

    private static String toHex(Color color) {
        if (color.getAlpha() == 255) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
        return String.format("#%02x%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
}
