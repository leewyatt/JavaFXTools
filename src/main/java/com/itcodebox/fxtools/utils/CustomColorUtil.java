package com.itcodebox.fxtools.utils;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.itcodebox.fxtools.utils.convert.HSL;
import com.itcodebox.fxtools.utils.convert.RGB;
import com.itcodebox.fxtools.utils.convert.RgbHslConverter;

import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

/**
 * @author LeeWyatt
 */
public class CustomColorUtil {
    /**
     * 整数形式
     */
    public static final int TYPE_INT = 0;
    /**
     * 小数形式
     */
    public static final int TYPE_DECIMAL = 1;
    /**
     * 百分比的形式
     */
    public static final int TYPE_PERCENTAGE = 2;

    public static Color convertToAwtColor(javafx.scene.paint.Color color) {
        return new Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
    }

    public static javafx.scene.paint.Color convertToFXColor(Color color) {
        return javafx.scene.paint.Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }

    public static Color getLastColor() {
        Color lastColor = null;
        String value = PropertiesComponent.getInstance().getValue("ColorChooser.RecentColors");
        if (value != null) {
            java.util.List<String> colors = StringUtil.split(value, ",,,");
            if (colors.size() != 0) {
                String colorStr = colors.get(colors.size() - 1);
                if (colorStr.contains("-")) {
                    List<String> components = StringUtil.split(colorStr, "-");
                    if (components.size() == 4) {
                        lastColor = new Color(Integer.parseInt((String) components.get(0)), Integer.parseInt((String) components.get(1)), Integer.parseInt((String) components.get(2)), Integer.parseInt((String) components.get(3)));
                    }
                } else {
                    lastColor = new Color(Integer.parseInt(colorStr));
                }
            }
        }
        return lastColor == null ? Color.WHITE : lastColor;
    }

    public static String toHex(Color color, boolean withAlpha, boolean withSharp) {
        String s = ColorUtil.toHex(color, withAlpha).toUpperCase();
        return withSharp ? "#" + s : s;
    }

    public static String toHex(javafx.scene.paint.Color color) {
        int r = (int) Math.round(color.getRed() * 255.0D);
        int g = (int) Math.round(color.getGreen() * 255.0D);
        int b = (int) Math.round(color.getBlue() * 255.0D);
        int a = (int) Math.round(color.getOpacity() * 255.0D);
        return String.format("#%02x%02x%02x%02x", r, g, b, a);
    }

    public static String toHsl(Color color, boolean withAlpha, int type) {
        HSL hsl = RgbHslConverter.RGB2HSL(new RGB(color.getRed(), color.getGreen(), color.getBlue()));
        int hue = Math.round(hsl.getH());
        double saturation = hsl.getS() / 255.0;
        double lightness = hsl.getL() / 255.0;
        String s;
        String l;
        if (type == TYPE_INT || type == TYPE_DECIMAL) {
            s = roundNoZero(saturation, 2);
            l = roundNoZero(lightness, 2);
        } else {
            s = convertToPerInt(saturation);
            l = convertToPerInt(lightness);
        }
        String opacity = roundNoZero(color.getAlpha() / 255.0, 2);
        opacity = "1".equals(opacity) ? "1.0" : opacity;
        String result = hue + "," + s + "," + l;
        return withAlpha ? result + "," + opacity : result;
    }

    public static String toHsb(Color color, boolean withAlpha, int type) {
        //javafx.scene.paint.Color fxColor = javafx.scene.paint.Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
        javafx.scene.paint.Color fxColor = convertToFXColor(color);
        int hue = (int) Math.round(fxColor.getHue());
        double saturation = fxColor.getSaturation();
        double brightness = fxColor.getBrightness();
        String s;
        String b;
        if (type == TYPE_INT || type == TYPE_DECIMAL) {
            s = roundNoZero(saturation, 2);
            b = roundNoZero(brightness, 2);
        } else {
            s = convertToPerInt(saturation);
            b = convertToPerInt(brightness);
        }
        String opacity = roundNoZero(fxColor.getOpacity(), 2);
        opacity = "1".equals(opacity) ? "1.0" : opacity;
        String result = hue + "," + s + "," + b;
        return withAlpha ? result + "," + opacity : result;
    }

    public static String toRgb(Color color, boolean withAlpha, int type) {
        String result;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        if (type == TYPE_INT) {
            result = r + "," + g + "," + b;
        } else if (type == TYPE_DECIMAL) {
            result = roundNoZero(r / 255.0, 2) + ","
                    + roundNoZero(g / 255.0, 2) + ","
                    + roundNoZero(b / 255.0, 2);
        } else {
            result = convertToPerInt(roundNoZero(r / 255.0, 2)) + ","
                    + convertToPerInt(roundNoZero(g / 255.0, 2)) + ","
                    + convertToPerInt(roundNoZero(b / 255.0, 2));
        }
        String opacity = roundNoZero(color.getAlpha() / 255.0, 2);
        opacity = "1".equals(opacity) ? "1.0" : opacity;
        return withAlpha ? result + "," + opacity : result;
    }

    public static String toFXRgb(javafx.scene.paint.Color color, boolean withAlpha, int type) {
        String result;
        double r = color.getRed();
        double g = color.getGreen();
        double b = color.getBlue();

        if (type == TYPE_INT) {
            result = (int)Math.round(r * 255) + "," + (int) Math.round(g * 255) + "," + (int) Math.round(b * 255);
        } else if (type == TYPE_DECIMAL) {
            result = roundNoZero(r, 2) + ","
                    + roundNoZero(g, 2) + ","
                    + roundNoZero(b, 2);
        } else {
            result = convertToPerInt(roundNoZero(r, 2)) + ","
                    + convertToPerInt(roundNoZero(g, 2)) + ","
                    + convertToPerInt(roundNoZero(b, 2));
        }
        String opacity = roundNoZero(color.getOpacity(), 2);
        opacity = "1".equals(opacity) ? "1.0" : opacity;
        return withAlpha ? result + "," + opacity : result;
    }

    private static String convertToPerInt(double value) {
        return Integer.parseInt(new DecimalFormat("0").format(value * 100)) + "%";
    }

    private static String convertToPerInt(String value) {
        return convertToPerInt(Double.parseDouble(value));
    }

    private static String roundNoZero(double value, int decimalPlaces) {
        BigDecimal decimal = new BigDecimal(String.format("%." + decimalPlaces + "f", value));
        BigDecimal noZeros = decimal.stripTrailingZeros();
        return noZeros.toPlainString();
    }


    private static HashMap<String, Color> ColorMap = new HashMap<>(200);
    static {
        ColorMap.put("#8B0016",ColorUtil.fromHex("#8B0016"));
        ColorMap.put("#B2001F",ColorUtil.fromHex("#B2001F"));
        ColorMap.put("#C50023",ColorUtil.fromHex("#C50023"));
        ColorMap.put("#DF0029",ColorUtil.fromHex("#DF0029"));
        ColorMap.put("#E54646",ColorUtil.fromHex("#E54646"));
        ColorMap.put("#EE7C6B",ColorUtil.fromHex("#EE7C6B"));
        ColorMap.put("#F5A89A",ColorUtil.fromHex("#F5A89A"));
        ColorMap.put("#FCDAD5",ColorUtil.fromHex("#FCDAD5"));
        ColorMap.put("#8E1E20",ColorUtil.fromHex("#8E1E20"));
        ColorMap.put("#B6292B",ColorUtil.fromHex("#B6292B"));
        ColorMap.put("#C82E31",ColorUtil.fromHex("#C82E31"));
        ColorMap.put("#E33539",ColorUtil.fromHex("#E33539"));
        ColorMap.put("#EB7153",ColorUtil.fromHex("#EB7153"));
        ColorMap.put("#F19373",ColorUtil.fromHex("#F19373"));
        ColorMap.put("#F6B297",ColorUtil.fromHex("#F6B297"));
        ColorMap.put("#FCD9C4",ColorUtil.fromHex("#FCD9C4"));
        ColorMap.put("#945305",ColorUtil.fromHex("#945305"));
        ColorMap.put("#BD6B09",ColorUtil.fromHex("#BD6B09"));
        ColorMap.put("#D0770B",ColorUtil.fromHex("#D0770B"));
        ColorMap.put("#EC870E",ColorUtil.fromHex("#EC870E"));
        ColorMap.put("#F09C42",ColorUtil.fromHex("#F09C42"));
        ColorMap.put("#F5B16D",ColorUtil.fromHex("#F5B16D"));
        ColorMap.put("#FACE9C",ColorUtil.fromHex("#FACE9C"));
        ColorMap.put("#FDE2CA",ColorUtil.fromHex("#FDE2CA"));
        ColorMap.put("#976D00",ColorUtil.fromHex("#976D00"));
        ColorMap.put("#C18C00",ColorUtil.fromHex("#C18C00"));
        ColorMap.put("#D59B00",ColorUtil.fromHex("#D59B00"));
        ColorMap.put("#F1AF00",ColorUtil.fromHex("#F1AF00"));
        ColorMap.put("#F3C246",ColorUtil.fromHex("#F3C246"));
        ColorMap.put("#F9CC76",ColorUtil.fromHex("#F9CC76"));
        ColorMap.put("#FCE0A6",ColorUtil.fromHex("#FCE0A6"));
        ColorMap.put("#FEEBD0",ColorUtil.fromHex("#FEEBD0"));
        ColorMap.put("#9C9900",ColorUtil.fromHex("#9C9900"));
        ColorMap.put("#C7C300",ColorUtil.fromHex("#C7C300"));
        ColorMap.put("#DCD800",ColorUtil.fromHex("#DCD800"));
        ColorMap.put("#F9F400",ColorUtil.fromHex("#F9F400"));
        ColorMap.put("#FCF54C",ColorUtil.fromHex("#FCF54C"));
        ColorMap.put("#FEF889",ColorUtil.fromHex("#FEF889"));
        ColorMap.put("#FFFAB3",ColorUtil.fromHex("#FFFAB3"));
        ColorMap.put("#FFFBD1",ColorUtil.fromHex("#FFFBD1"));
        ColorMap.put("#367517",ColorUtil.fromHex("#367517"));
        ColorMap.put("#489620",ColorUtil.fromHex("#489620"));
        ColorMap.put("#50A625",ColorUtil.fromHex("#50A625"));
        ColorMap.put("#5BBD2B",ColorUtil.fromHex("#5BBD2B"));
        ColorMap.put("#83C75D",ColorUtil.fromHex("#83C75D"));
        ColorMap.put("#AFD788",ColorUtil.fromHex("#AFD788"));
        ColorMap.put("#C8E2B1",ColorUtil.fromHex("#C8E2B1"));
        ColorMap.put("#E6F1D8",ColorUtil.fromHex("#E6F1D8"));
        ColorMap.put("#006241",ColorUtil.fromHex("#006241"));
        ColorMap.put("#007F54",ColorUtil.fromHex("#007F54"));
        ColorMap.put("#008C5E",ColorUtil.fromHex("#008C5E"));
        ColorMap.put("#00A06B",ColorUtil.fromHex("#00A06B"));
        ColorMap.put("#00AE72",ColorUtil.fromHex("#00AE72"));
        ColorMap.put("#67BF7F",ColorUtil.fromHex("#67BF7F"));
        ColorMap.put("#98D0B9",ColorUtil.fromHex("#98D0B9"));
        ColorMap.put("#C9E4D6",ColorUtil.fromHex("#C9E4D6"));
        ColorMap.put("#00676B",ColorUtil.fromHex("#00676B"));
        ColorMap.put("#008489",ColorUtil.fromHex("#008489"));
        ColorMap.put("#009298",ColorUtil.fromHex("#009298"));
        ColorMap.put("#00A6AD",ColorUtil.fromHex("#00A6AD"));
        ColorMap.put("#00B2BF",ColorUtil.fromHex("#00B2BF"));
        ColorMap.put("#6EC3C9",ColorUtil.fromHex("#6EC3C9"));
        ColorMap.put("#99D1D3",ColorUtil.fromHex("#99D1D3"));
        ColorMap.put("#CAE5E8",ColorUtil.fromHex("#CAE5E8"));
        ColorMap.put("#103667",ColorUtil.fromHex("#103667"));
        ColorMap.put("#184785",ColorUtil.fromHex("#184785"));
        ColorMap.put("#1B4F93",ColorUtil.fromHex("#1B4F93"));
        ColorMap.put("#205AA7",ColorUtil.fromHex("#205AA7"));
        ColorMap.put("#426EB4",ColorUtil.fromHex("#426EB4"));
        ColorMap.put("#7388C1",ColorUtil.fromHex("#7388C1"));
        ColorMap.put("#94AAD6",ColorUtil.fromHex("#94AAD6"));
        ColorMap.put("#BFCAE6",ColorUtil.fromHex("#BFCAE6"));
        ColorMap.put("#211551",ColorUtil.fromHex("#211551"));
        ColorMap.put("#2D1E69",ColorUtil.fromHex("#2D1E69"));
        ColorMap.put("#322275",ColorUtil.fromHex("#322275"));
        ColorMap.put("#3A2885",ColorUtil.fromHex("#3A2885"));
        ColorMap.put("#511F90",ColorUtil.fromHex("#511F90"));
        ColorMap.put("#635BA2",ColorUtil.fromHex("#635BA2"));
        ColorMap.put("#8273B0",ColorUtil.fromHex("#8273B0"));
        ColorMap.put("#A095C4",ColorUtil.fromHex("#A095C4"));
        ColorMap.put("#38044B",ColorUtil.fromHex("#38044B"));
        ColorMap.put("#490761",ColorUtil.fromHex("#490761"));
        ColorMap.put("#52096C",ColorUtil.fromHex("#52096C"));
        ColorMap.put("#5D0C7B",ColorUtil.fromHex("#5D0C7B"));
        ColorMap.put("#79378B",ColorUtil.fromHex("#79378B"));
        ColorMap.put("#8C63A4",ColorUtil.fromHex("#8C63A4"));
        ColorMap.put("#AA87B8",ColorUtil.fromHex("#AA87B8"));
        ColorMap.put("#C9B5D4",ColorUtil.fromHex("#C9B5D4"));
        ColorMap.put("#64004B",ColorUtil.fromHex("#64004B"));
        ColorMap.put("#780062",ColorUtil.fromHex("#780062"));
        ColorMap.put("#8F006D",ColorUtil.fromHex("#8F006D"));
        ColorMap.put("#A2007C",ColorUtil.fromHex("#A2007C"));
        ColorMap.put("#AF4A92",ColorUtil.fromHex("#AF4A92"));
        ColorMap.put("#C57CAC",ColorUtil.fromHex("#C57CAC"));
        ColorMap.put("#D2A6C7",ColorUtil.fromHex("#D2A6C7"));
        ColorMap.put("#E8D3E3",ColorUtil.fromHex("#E8D3E3"));
        ColorMap.put("#ECECEC",ColorUtil.fromHex("#ECECEC"));
        ColorMap.put("#D7D7D7",ColorUtil.fromHex("#D7D7D7"));
        ColorMap.put("#C2C2C2",ColorUtil.fromHex("#C2C2C2"));
        ColorMap.put("#B7B7B7",ColorUtil.fromHex("#B7B7B7"));
        ColorMap.put("#A0A0A0",ColorUtil.fromHex("#A0A0A0"));
        ColorMap.put("#898989",ColorUtil.fromHex("#898989"));
        ColorMap.put("#707070",ColorUtil.fromHex("#707070"));
        ColorMap.put("#555555",ColorUtil.fromHex("#555555"));
        ColorMap.put("#363636",ColorUtil.fromHex("#363636"));
        ColorMap.put("#000000",ColorUtil.fromHex("#000000"));
    }
    public static Color fromHex(String webColor) {
        Color color = ColorMap.get(webColor);
        if (color == null) {
            return ColorUtil.fromHex(webColor);
        }
        return color;
    }
    public static Color fromHex(String webColor,boolean doesNotExistSave) {
        Color color = ColorMap.get(webColor);
        if (color == null) {
            Color c = ColorUtil.fromHex(webColor);
            if (doesNotExistSave) {
                ColorMap.put(webColor, c);
            }
            return c;
        }
        return color;
    }
}
