package com.itcodebox.fxtools.utils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.itcodebox.fxtools.components.fx.paintpicker.gradientpicker.GradientPickerStop;
import com.sun.javafx.scene.paint.GradientUtils;
import javafx.scene.paint.Paint;
import javafx.scene.paint.*;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static com.itcodebox.fxtools.utils.CustomUtil.roundingDouble;

/**
 * @author LeeWyatt
 */
public class PaintConvertUtil {

    /**
     * 位置的精确度是3
     */
    private static final int OFFSET_SCALE = 3;

    public static String toFXJavaCode(Paint fxPaint,boolean withVarName) {
        if (fxPaint == null) {
            return null;
        }
        if (fxPaint instanceof LinearGradient) {
            String varStr = "";
            if (withVarName) {
                varStr = "LinearGradient paint = ";
            }
            LinearGradient paint = (LinearGradient) fxPaint;
            return varStr+"new LinearGradient(" + System.lineSeparator() +
                    paint.getStartX() + "," + paint.getStartY() + "," +
                    paint.getEndX() + "," + paint.getEndY() + "," +
                    paint.isProportional() + "," +
                    cycleMethodToStr(paint.getCycleMethod(), false) + "," + System.lineSeparator() +
                    stopsToString(paint.getStops(), false) +
                    ");";
        } else if (fxPaint instanceof RadialGradient) {
            String varStr = "";
            if (withVarName) {
                varStr = "RadialGradient paint = ";
            }
            RadialGradient paint = (RadialGradient) fxPaint;
            return varStr+"new RadialGradient(" + System.lineSeparator() +
                    paint.getFocusAngle() + "," + paint.getFocusDistance() + "," + paint.getCenterX() + ","
                    + paint.getCenterY() + "," + paint.getRadius() + "," + paint.isProportional() + ","
                    + cycleMethodToStr(paint.getCycleMethod(), false) + System.lineSeparator() + ","
                    + stopsToString(paint.getStops(), false) + ");";
        } else if (fxPaint instanceof javafx.scene.paint.Color) {
            String varStr = "";
            if (withVarName) {
                varStr = "Color paint =";
            }
            javafx.scene.paint.Color color = (javafx.scene.paint.Color) fxPaint;
            return String.format("%s new Color( %s, %s, %s, %s);", varStr,roundingDouble(color.getRed(), 2), roundingDouble(color.getGreen(), 2), roundingDouble(color.getBlue(), 2), roundingDouble(color.getOpacity(), 2));
        }
        return null;
    }

    public static String toSwingCode(Paint paint, List<GradientPickerStop> gradientStops) {
        String paintText = paint.toString();
        if (paintText == null || paintText.trim().isEmpty()) {
            return null;
        }
        if (paintText.startsWith("linear-gradient")) {
            //顺序,swing必须严格的从小到大排列offset,但是List里面的数据是按照先后顺序加入的 ,所以先排序
            gradientStops.sort(Comparator.comparingDouble(GradientPickerStop::getOffset));
            return parseSwingLinearGradient(paintText, gradientStops);
        } else if (paintText.startsWith("radial-gradient")) {
            gradientStops.sort(Comparator.comparingDouble(GradientPickerStop::getOffset));
            return parseSwingRadialGradient(paintText, gradientStops);
        } else {
            Color color = ColorUtil.fromHex(paintText.replace("0x", "#"));
            return parseSwingColorString(color) + ";";
        }
    }

    /**
     * 测试的时候使用, 获取临时信息
     */
    public static String toStopInfo(List<GradientPickerStop> gradientStops) {
        gradientStops.sort(Comparator.comparingDouble(GradientPickerStop::getOffset));
        int size = gradientStops.size();
        String[] floatStrAry = new String[size];
        String[] colorStrAry = new String[size];
        for (int i = 0; i < size; i++) {
            GradientPickerStop stop = gradientStops.get(i);
            floatStrAry[i] = (float) stop.getOffset() + "F";
            colorStrAry[i] = parseSwingColorString(CustomColorUtil.convertToAwtColor(stop.getColor()));
        }
        String fs = StringUtil.join(floatStrAry, ",");
        String cs = StringUtil.join(colorStrAry, ",");
        return String.format("new LinearGradientInfo(new float[]{%s}, new Color[]{%s})", fs, cs);
    }

    public static String toSwingCode(Paint paint, Stop[] gradientStops) {
        String paintText = paint.toString();
        if (paintText == null || paintText.trim().isEmpty()) {
            return null;
        }
        if (paintText.startsWith("linear-gradient")) {
            return parseSwingLinearGradient(paintText, gradientStops);
        } else if (paintText.startsWith("radial-gradient")) {
            return parseSwingRadialGradient(paintText, gradientStops);
        } else {
            Color color = ColorUtil.fromHex(paintText.replace("0x", "#"));
            return parseSwingColorString(color) + ";";
        }
    }

    private static String parseSwingColorString(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = color.getAlpha();
        if (a == 255) {
            return String.format("new Color(%d,%d,%d)", r, g, b);
        } else {
            return String.format("new Color(%d,%d,%d,%d)", r, g, b, a);
        }
    }

    private static String parseSwingLinearGradient(String paintText, List<GradientPickerStop> gradientStops) {
        int size = gradientStops.size();
        String[] floatStrAry = new String[size];
        String[] colorStrAry = new String[size];
        for (int i = 0; i < size; i++) {
            GradientPickerStop stop = gradientStops.get(i);
            floatStrAry[i] = (float) stop.getOffset() + "F";
            colorStrAry[i] = parseSwingColorString(CustomColorUtil.convertToAwtColor(stop.getColor()));
        }
        String fs = StringUtil.join(floatStrAry, ",");
        String cs = StringUtil.join(colorStrAry, ",");
        String cycleMethod = getAwtLinearGradientCycleMethod(paintText);
        return String.format("new LinearGradientPaint(0.0F,0.0F,100F,0F,%snew float[]{%s},%snew Color[]{%s},%s);", System.lineSeparator(), fs, System.lineSeparator(), cs, System.lineSeparator() + cycleMethod);
    }

    private static String parseSwingLinearGradient(String paintText, Stop[] gradientStops) {
        int size = gradientStops.length;
        String[] floatStrAry = new String[size];
        String[] colorStrAry = new String[size];
        for (int i = 0; i < size; i++) {
            Stop stop = gradientStops[i];
            floatStrAry[i] = (float) stop.getOffset() + "F";
            colorStrAry[i] = parseSwingColorString(CustomColorUtil.convertToAwtColor(stop.getColor()));
        }
        String fs = StringUtil.join(floatStrAry, ",");
        String cs = StringUtil.join(colorStrAry, ",");
        String cycleMethod = getAwtLinearGradientCycleMethod(paintText);
        return String.format("new LinearGradientPaint(0.0F,0.0F,100F,0F,%snew float[]{%s},%snew Color[]{%s},%s);", System.lineSeparator(), fs, System.lineSeparator(), cs, System.lineSeparator() + cycleMethod);
    }

    private static String parseSwingRadialGradient(String style, Stop[] gradientStops) {
        String substring = style.substring(style.indexOf("(")).replaceAll("0x", "").replace(")", "");
        String[] ss = substring.split(",");
        String centerX = "50F";
        String centerY = "50F";
        String radius = "50F";
        //String[] center = ss[2].trim().split(" ");
        //boolean proportional = ss[2].contains("%");
        //String centerX = (float)Double.parseDouble(textToDoubleStr(center[1], proportional))+"F";
        //String centerY = (float)Double.parseDouble(textToDoubleStr(center[2], proportional))+"F";
        //String radius = (float)Double.parseDouble(textToDoubleStr(ss[3].trim().replace("radius ", ""), proportional))+"F";
        String cycleMethod = " MultipleGradientPaint.CycleMethod.NO_CYCLE";
        if (ss[4].contains("reflect")) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REFLECT";
        } else if (ss[4].contains("repeat")) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REPEAT";
        }
        int size = gradientStops.length;
        String[] floatStrAry = new String[size];
        String[] colorStrAry = new String[size];
        for (int i = 0; i < size; i++) {
            Stop stop = gradientStops[i];
            floatStrAry[i] = (float) stop.getOffset() + "F";
            colorStrAry[i] = parseSwingColorString(CustomColorUtil.convertToAwtColor(stop.getColor()));
        }
        String fs = StringUtil.join(floatStrAry, ",");
        String cs = StringUtil.join(colorStrAry, ",");
        return "new RadialGradientPaint(" + System.lineSeparator() +
                centerX + "," + centerY + "," + radius + "," + System.lineSeparator() + "new float[]{" + fs + "}," + System.lineSeparator() + " new Color[]{" + cs + "}," + System.lineSeparator() + cycleMethod + ");";
    }

    private static String parseSwingRadialGradient(String style, List<GradientPickerStop> gradientStops) {
        String substring = style.substring(style.indexOf("(")).replaceAll("0x", "").replace(")", "");
        String[] ss = substring.split(",");
        String centerX = "50F";
        String centerY = "50F";
        String radius = "50F";
        //String[] center = ss[2].trim().split(" ");
        //boolean proportional = ss[2].contains("%");
        //String centerX = (float)Double.parseDouble(textToDoubleStr(center[1], proportional))+"F";
        //String centerY = (float)Double.parseDouble(textToDoubleStr(center[2], proportional))+"F";
        //String radius = (float)Double.parseDouble(textToDoubleStr(ss[3].trim().replace("radius ", ""), proportional))+"F";
        String cycleMethod = " MultipleGradientPaint.CycleMethod.NO_CYCLE";
        if (ss[4].contains("reflect")) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REFLECT";
        } else if (ss[4].contains("repeat")) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REPEAT";
        }
        int size = gradientStops.size();
        String[] floatStrAry = new String[size];
        String[] colorStrAry = new String[size];
        for (int i = 0; i < size; i++) {
            GradientPickerStop stop = gradientStops.get(i);
            floatStrAry[i] = (float) stop.getOffset() + "F";
            colorStrAry[i] = parseSwingColorString(CustomColorUtil.convertToAwtColor(stop.getColor()));
        }
        String fs = StringUtil.join(floatStrAry, ",");
        String cs = StringUtil.join(colorStrAry, ",");
        return "new RadialGradientPaint(" + System.lineSeparator() +
                centerX + "," + centerY + "," + radius + "," + System.lineSeparator() + "new float[]{" + fs + "}," + System.lineSeparator() + " new Color[]{" + cs + "}," + System.lineSeparator() + cycleMethod + ");";
    }

    @NotNull
    private static String cycleMethodToStr(CycleMethod cycleMethod, boolean isCssMode) {
        String cycleMethodStr;
        if (CycleMethod.REFLECT.equals(cycleMethod)) {
            cycleMethodStr = isCssMode ? "reflect," : "CycleMethod.REFLECT";
        } else if (CycleMethod.REPEAT.equals(cycleMethod)) {
            cycleMethodStr = isCssMode ? "repeat," : "CycleMethod.REPEAT";
        } else {
            cycleMethodStr = isCssMode ? "" : "CycleMethod.NO_CYCLE";
        }
        return cycleMethodStr;
    }

    private static String stopsToString(List<Stop> stops, boolean isCssMode) {
        StringBuilder stopsBuilder = new StringBuilder(32);
        if (isCssMode) {
            int len = stops.size();
            for (int i = 0; i < len; i++) {
                Stop stop = stops.get(i);
                String strColor = CustomColorUtil.toHex(stop.getColor());
                double offset = roundingDouble(stop.getOffset(), 3);
                stopsBuilder.append(strColor).append(" ").append(offset).append(")");
                if (i + 1 < len) {
                    stopsBuilder.append(",").append(System.lineSeparator());
                }
            }
        } else {
            int len = stops.size();
            for (int i = 0; i < len; i++) {
                Stop stop = stops.get(i);
                javafx.scene.paint.Color color = stop.getColor();
                double offset = roundingDouble(stop.getOffset(), 3);
                String strColor = String.format("new Color( %s, %s, %s, %s)", roundingDouble(color.getRed(), 2), roundingDouble(color.getGreen(), 2), roundingDouble(color.getBlue(), 2), roundingDouble(color.getOpacity(), 2));
                stopsBuilder.append("new Stop(").append(offset).append(",").append(strColor).append(")");
                if (i + 1 < len) {
                    stopsBuilder.append(",").append(System.lineSeparator());
                }
            }
        }
        return stopsBuilder.toString();
    }



    private static String getAwtLinearGradientCycleMethod(String style) {
        String substring = style.substring(style.indexOf("(")).replaceAll("0x", "").replace(")", "");
        String cycleMethod = substring.split(",")[1].trim();
        if ("reflect".equals(cycleMethod)) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REFLECT";
        } else if ("repeat".equals(cycleMethod)) {
            cycleMethod = "MultipleGradientPaint.CycleMethod.REPEAT";
        } else {
            cycleMethod = "MultipleGradientPaint.CycleMethod.NO_CYCLE";
        }
        return cycleMethod;
    }



    //TODO
    public static String toFXCssCode(Paint fxPaint) {
        if (fxPaint == null) {
            return null;
        }
        if (fxPaint instanceof LinearGradient) {
            LinearGradient paint = (LinearGradient) fxPaint;
            final StringBuilder s = new StringBuilder("linear-gradient(from ")
                    .append(GradientUtils.lengthToString(roundingDouble(paint.getStartX(), 3), paint.isProportional()))
                    .append(" ").append(GradientUtils.lengthToString(roundingDouble(paint.getStartY(), 3), paint.isProportional()))
                    .append(" to ").append(GradientUtils.lengthToString(roundingDouble(paint.getEndX(), 3), paint.isProportional()))
                    .append(" ").append(GradientUtils.lengthToString(roundingDouble(paint.getEndY(), 3), paint.isProportional()))
                    .append(", ");
            switch (paint.getCycleMethod()) {
                case REFLECT:
                    s.append("reflect").append(", ");
                    break;
                case REPEAT:
                    s.append("repeat").append(", ");
                    break;
            }

            for (Stop stop : paint.getStops()) {
                s.append(CustomColorUtil.toHex(stop.getColor())).append(" ").append(roundingDouble(stop.getOffset() * 100.0D, 3)).append("%").append(", ");
            }
            s.delete(s.length() - 2, s.length());
            s.append(")");
            return s.toString();
        } else if (fxPaint instanceof RadialGradient) {
            RadialGradient paint = (RadialGradient) fxPaint;
            final StringBuilder s = new StringBuilder("radial-gradient(focus-angle ").append(paint.getFocusAngle())
                    .append("deg, focus-distance ").append(paint.getFocusDistance() * 100)
                    .append("% , center ").append(GradientUtils.lengthToString(paint.getCenterX(), paint.isProportional()))
                    .append(" ").append(GradientUtils.lengthToString(paint.getCenterY(), paint.isProportional()))
                    .append(", radius ").append(GradientUtils.lengthToString(paint.getRadius(), paint.isProportional()))
                    .append(", ");

            switch (paint.getCycleMethod()) {
                case REFLECT:
                    s.append("reflect").append(", ");
                    break;
                case REPEAT:
                    s.append("repeat").append(", ");
                    break;
            }

            for (Stop stop : paint.getStops()) {
                s.append(CustomColorUtil.toHex(stop.getColor())).append(" ").append(roundingDouble(stop.getOffset() * 100.0D, 3)).append("%").append(", ");
            }
            s.delete(s.length() - 2, s.length());
            s.append(")");
            return s.toString();
        } else if (fxPaint instanceof javafx.scene.paint.Color) {
            return CustomColorUtil.toHex((javafx.scene.paint.Color) fxPaint);
        }
        return null;
    }



    /**
     * ffffffff  格式转换成 new Color(1.0,1.0,1.0,1.0);
     */
    public static String rgbFXColorFormat(String hexColor) {
        String r = hexColorToDoubleColor(hexColor.substring(0, 2));
        String g = hexColorToDoubleColor(hexColor.substring(2, 4));
        String b = hexColorToDoubleColor(hexColor.substring(4, 6));
        String a = hexColorToDoubleColor(hexColor.substring(6, 8));
        return "new Color(" + r + "," + g + "," + b + "," + a + ")";
    }

    /**
     * 把 2位的16进制, 转成 小数
     * 比如FF->1.0 ; 00->0
     */
    private static String hexColorToDoubleColor(String hexStr) {
        String s = String.format("%.2f", Integer.parseInt(hexStr, 16) / 255.0);
        return new BigDecimal(s).stripTrailingZeros().toPlainString();
    }

}

