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

    public static String convertPaintToJavaCode(Paint paint) {
        return convertPaintToJavaCode(paint, true);
    }

    public static String convertPaintToJavaCode(Paint paint, boolean withDeclaration) {
        StringBuilder sb = new StringBuilder();
        if (withDeclaration) {
            if (paint instanceof LinearGradientPaint) {
                sb.append("LinearGradientPaint paint = ");
            } else if (paint instanceof RadialGradientPaint) {
                sb.append("RadialGradientPaint paint = ");
            } else if (paint instanceof Color) {
                sb.append("Color paint = ");
            }
        }
        if (paint instanceof LinearGradientPaint lg) {
            float[] fractions = lg.getFractions();
            Color[] colors = lg.getColors();
            sb.append("new LinearGradientPaint(").append(System.lineSeparator())
                    .append("new Point2D.Float(").append(round(lg.getStartPoint().getX())).append("f, ")
                    .append(round(lg.getStartPoint().getY())).append("f), ")
                    .append("new Point2D.Float(").append(round(lg.getEndPoint().getX())).append("f, ")
                    .append(round(lg.getEndPoint().getY())).append("f),").append(System.lineSeparator())
                    .append(fractionsToString(fractions)).append(",").append(System.lineSeparator())
                    .append(colorsToString(colors)).append(",").append(System.lineSeparator())
                    .append(cycleMethodToStr(lg.getCycleMethod())).append(")");
        } else if (paint instanceof RadialGradientPaint rg) {
            float[] fractions = rg.getFractions();
            Color[] colors = rg.getColors();
            sb.append("new RadialGradientPaint(").append(System.lineSeparator())
                    .append("new Point2D.Float(").append(round(rg.getCenterPoint().getX())).append("f, ")
                    .append(round(rg.getCenterPoint().getY())).append("f), ")
                    .append(round(rg.getRadius())).append("f,").append(System.lineSeparator())
                    .append(fractionsToString(fractions)).append(",").append(System.lineSeparator())
                    .append(colorsToString(colors)).append(",").append(System.lineSeparator())
                    .append(cycleMethodToStr(rg.getCycleMethod())).append(")");
        } else if (paint instanceof Color color) {
            sb.append(colorToJavaStr(color));
        }
        if (withDeclaration) {
            sb.append(";");
        }
        return sb.toString();
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

    private static String cycleMethodToStr(MultipleGradientPaint.CycleMethod cm) {
        return switch (cm) {
            case REFLECT -> "MultipleGradientPaint.CycleMethod.REFLECT";
            case REPEAT -> "MultipleGradientPaint.CycleMethod.REPEAT";
            default -> "MultipleGradientPaint.CycleMethod.NO_CYCLE";
        };
    }

    private static String fractionsToString(float[] fractions) {
        StringBuilder sb = new StringBuilder("new float[]{");
        for (int i = 0; i < fractions.length; i++) {
            sb.append(round(fractions[i])).append("f");
            if (i < fractions.length - 1) { sb.append(", "); }
        }
        return sb.append("}").toString();
    }

    private static String colorsToString(Color[] colors) {
        StringBuilder sb = new StringBuilder("new Color[]{");
        for (int i = 0; i < colors.length; i++) {
            sb.append(colorToJavaStr(colors[i]));
            if (i < colors.length - 1) { sb.append(", "); }
        }
        return sb.append("}").toString();
    }

    private static String colorToJavaStr(Color c) {
        return String.format("new Color(%d, %d, %d, %d)", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
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
