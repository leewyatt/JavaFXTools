package com.itcodebox.fxtools.utils;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.shape.SVGPath;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author LeeWyatt
 */
public class CustomUtil {

    public static Image loadImage(String fileName, float scale) {
        URL path = null;
        try {
            path = new File(fileName).toURI().toURL();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        Icon icon = IconLoader.findIcon(path);
        if (icon == null) {
            return null;
        }
        if (Float.compare(scale, 1.0F) != 0) {
            icon = IconUtil.scale(icon, null, scale);
        }
        return SwingFXUtils.toFXImage(IconUtil.toBufferedImage(icon, true), null);
    }

    public static Image loadImage(File file, float scale) {
        URL path = null;
        try {
            path = file.toURI().toURL();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        Icon icon = IconLoader.findIcon(path);
        if (icon == null) {
            return null;
        }
        if (Float.compare(scale, 1.0F) != 0) {
            icon = IconUtil.scale(icon, null, scale);
        }
        return SwingFXUtils.toFXImage(IconUtil.toBufferedImage(icon, true), null);
    }

    /**
     * 剪切板的内容
     */
    public static final ClipboardContent clipboardContent = new ClipboardContent();

    public static void copyToClipboard(String info) {
        Platform.runLater(() -> {
            clipboardContent.clear();
            Clipboard fxClipboard = Clipboard.getSystemClipboard();
            fxClipboard.clear();
            clipboardContent.putString(info);
            fxClipboard.setContent(clipboardContent);
        });
    }

    public static void copyToClipboard(Image image) {
        Platform.runLater(() -> {
            clipboardContent.clear();
            Clipboard fxClipboard = Clipboard.getSystemClipboard();
            fxClipboard.clear();
            clipboardContent.putImage(image);
            fxClipboard.setContent(clipboardContent);
        });
    }

    public static void copyToClipboard(File file) {
        Platform.runLater(() -> {
            clipboardContent.clear();
            Clipboard fxClipboard = Clipboard.getSystemClipboard();
            fxClipboard.clear();
            clipboardContent.putFiles(List.of(file));
            fxClipboard.setContent(clipboardContent);
        });
    }

    /**
     * 这里是ICON的保存处理
     */
    public static void copyToClipboard(String pathName, String extensionName, double xmlScale, boolean x2Icon, boolean x3Icon, boolean disabledIcon) {
        File file = new File(pathName);
        String svgFileName = file.getName();
        String fileName = svgFileName.substring(0, svgFileName.length() - 4);
        URL iconUrl = null;
        try {
            iconUrl = file.toURI().toURL();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        List<File> fileList = new ArrayList<>();
        try {
            Path tempDirPath = PluginConstant.TEMP_DIRECTORY_PATH.resolve(StringUtil.random(32, true, true));
            File tempDir = Files.createDirectories(tempDirPath).toFile();
            tempDir.deleteOnExit();

            Icon originIcon = IconLoader.findIcon(iconUrl);
            if (originIcon == null) {
                return;
            }
            createTempImageFile(fileName, extensionName, (float) xmlScale, x2Icon, x3Icon, fileList, tempDirPath, originIcon);
            if (disabledIcon) {
                createTempImageFile(fileName + "_disabled", extensionName, (float) xmlScale, x2Icon, x3Icon, fileList, tempDirPath, IconLoader.getDisabledIcon(originIcon));
            }
            Platform.runLater(() -> {
                clipboardContent.clear();
                Clipboard fxClipboard = Clipboard.getSystemClipboard();
                fxClipboard.clear();
                clipboardContent.putFiles(fileList);
                fxClipboard.setContent(clipboardContent);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 创建保存图片的临时文件,并把图片写入到文件
     *
     * @param xmlFilePath   文件名 无后缀
     * @param extensionName 文件后缀
     * @param xmlScale      初始缩放比例
     * @param x2Icon        是否创建2倍图
     * @param x3Icon        是否创建3倍图
     * @param fileList      剪切板里的文件列表
     * @param tempDirPath   临时文件夹
     * @param originIcon    原始未缩放的图标
     */
    private static void createTempImageFile(String xmlFilePath, String extensionName, float xmlScale, boolean x2Icon, boolean x3Icon, List<File> fileList, Path tempDirPath, Icon originIcon) {
        File x1File = tempDirPath.resolve(xmlFilePath + "." + extensionName).toFile();
        x1File.deleteOnExit();
        BufferedImage image1 = IconUtil.toBufferedImage(IconUtil.scale(originIcon, null, xmlScale), true);
        writeImageToFile(extensionName,  x1File, image1);
        fileList.add(x1File);
        if (x2Icon) {
            File x2File = tempDirPath.resolve(xmlFilePath + "@2x." + extensionName).toFile();
            x2File.deleteOnExit();
            BufferedImage image = IconUtil.toBufferedImage(IconUtil.scale(originIcon, null, xmlScale * 2), true);
            writeImageToFile(extensionName,  x2File, image);
            fileList.add(x2File);
        }
        if (x3Icon) {
            File x3File = tempDirPath.resolve(xmlFilePath + "@3x." + extensionName).toFile();
            x3File.deleteOnExit();
            BufferedImage image = IconUtil.toBufferedImage( IconUtil.scale(originIcon, null, xmlScale * 3), true);
            writeImageToFile(extensionName, x3File, image);
            fileList.add(x3File);
        }
    }

    /**
     * 图片写入到文件
     *
     * @param extensionName 后缀
     * @param imgFile       图片文件
     * @param image         图像
     */
    private static void writeImageToFile(String extensionName, File imgFile, BufferedImage image) {
        if ("png".equalsIgnoreCase(extensionName) || "gif".equalsIgnoreCase(extensionName)) {
            try {
                FileOutputStream out = new FileOutputStream(imgFile);
                ImageIO.write(image, extensionName, out);
                out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            BufferedImage destImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = destImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            //不同插值效果. graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            ImageUtil.applyQualityRenderingHints(graphics);
            graphics.drawImage(image, 0, 0, Color.WHITE, null);
            graphics.dispose();
            try {
                OutputStream out = new FileOutputStream(imgFile);
                ImageIO.write(destImage, extensionName, out);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取快照的参数
     */
    @NotNull
    private static SnapshotParameters getSnapshotParameters(javafx.scene.paint.Color fill) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(fill);
        return parameters;
    }

    public static void copyToClipboard(SVGPath svgPathNode, String xmlFilePath, String extensionName, boolean x2Icon, boolean x3Icon, boolean disabledIcon) {
        File file = new File(xmlFilePath);
        String svgFileName = file.getName();
        String fileName = svgFileName.substring(0, svgFileName.length() - 4);

        Path tempDirPath = PluginConstant.TEMP_DIRECTORY_PATH.resolve(StringUtil.random(32, true, true));
        File tempDir = null;
        try {
            tempDir = Files.createDirectories(tempDirPath).toFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (tempDir == null) {
            return;
        }
        tempDir.deleteOnExit();

        List<File> fileList = new ArrayList<>();

        //在临时文件目录创建
        createTempImageFile(svgPathNode, extensionName, x2Icon, x3Icon, fileName, tempDirPath,fileList);
        if (disabledIcon) {
            javafx.scene.paint.Paint initPaint = svgPathNode.getFill();
            svgPathNode.setFill(new javafx.scene.paint.Color(0.76,0.76,0.76,1.0));
            //在临时文件目录创建
            createTempImageFile(svgPathNode, extensionName, x2Icon, x3Icon, fileName+ "_disabled", tempDirPath,fileList);
            svgPathNode.setFill(initPaint);
        }
        Platform.runLater(() -> {
            clipboardContent.clear();
            Clipboard fxClipboard = Clipboard.getSystemClipboard();
            fxClipboard.clear();
            clipboardContent.putFiles(fileList);
            fxClipboard.setContent(clipboardContent);
        });
    }

    private static void createTempImageFile(SVGPath svgPathNode, String extensionName, boolean x2Icon, boolean x3Icon, String fileName, Path tempDirPath, List<File> fileList) {
        double initScale = svgPathNode.getScaleX();
        File file1x = tempDirPath.resolve(fileName + "." + extensionName).toFile();
        fileList.add(file1x);
        file1x.deleteOnExit();
        WritableImage image1x = svgPathNode.snapshot(getSnapshotParameters(javafx.scene.paint.Color.TRANSPARENT), null);

        writeImageToFile(extensionName,file1x,SwingFXUtils.fromFXImage(image1x, null));
        if (x2Icon) {
            File file2x = tempDirPath.resolve(fileName + "@2x." + extensionName).toFile();
            fileList.add(file2x);
            file2x.deleteOnExit();
            svgPathNode.setScaleX(initScale *2);
            svgPathNode.setScaleY(initScale *2);
            WritableImage image2x = svgPathNode.snapshot(getSnapshotParameters(javafx.scene.paint.Color.TRANSPARENT), null);
            svgPathNode.setScaleX(initScale);
            svgPathNode.setScaleY(initScale);
            writeImageToFile(extensionName,file2x,SwingFXUtils.fromFXImage(image2x, null));
        }
        if (x3Icon) {
            File file3x = tempDirPath.resolve(fileName + "@3x." + extensionName).toFile();
            fileList.add(file3x);
            file3x.deleteOnExit();
            svgPathNode.setScaleX(initScale *3);
            svgPathNode.setScaleY(initScale *3);
            WritableImage image3x = svgPathNode.snapshot(getSnapshotParameters(javafx.scene.paint.Color.TRANSPARENT), null);
            svgPathNode.setScaleX(initScale);
            svgPathNode.setScaleY(initScale);
            writeImageToFile(extensionName,file3x,SwingFXUtils.fromFXImage(image3x, null));
        }

    }

    /**
     * 这里是SVGPath节点进行截图后的保存处理, 所以
     */
    public static void copyToClipboard(Image image, String suffix, String fileName) {
        File file = null;
        try {
            //deleteOnExit 的 删除顺序与注册顺序是相反的 ,所以, 先注册的文件夹会后删除
            Path tempDirPath = PluginConstant.TEMP_DIRECTORY_PATH.resolve(StringUtil.random(32, true, true));
            File tempDir = Files.createDirectories(tempDirPath).toFile();
            tempDir.deleteOnExit();
            file = Files.createFile(tempDirPath.resolve(fileName + "." + suffix)).toFile();
            file.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (file == null) {
            return;
        }
        if ("png".equals(suffix) || "gif".equals(suffix)) {
            try {
                OutputStream out = new FileOutputStream(file);
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), suffix, out);
                out.close();

                List<File> list = List.of(file);
                Platform.runLater(() -> {
                    clipboardContent.clear();
                    Clipboard fxClipboard = Clipboard.getSystemClipboard();
                    fxClipboard.clear();
                    clipboardContent.putFiles(list);
                    fxClipboard.setContent(clipboardContent);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if ("jpg".equals(suffix)) {

            BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
            BufferedImage destImage = ImageUtil.createImage(bImage.getWidth(), bImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = destImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            //不同插值效果. graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(bImage, 0, 0, null);
            graphics.dispose();
            try {
                OutputStream out = new FileOutputStream(file);
                ImageIO.write(destImage, suffix, out);
                out.close();
                List<File> list = List.of(file);
                Platform.runLater(() -> {
                    clipboardContent.clear();
                    Clipboard fxClipboard = Clipboard.getSystemClipboard();
                    fxClipboard.clear();
                    clipboardContent.putFiles(list);
                    fxClipboard.setContent(clipboardContent);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 经过测试不太清晰, 有比较粗糙的毛边
     */
    private static void saveJPGFile1(Image image, File file) {
        BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
        BufferedImage bImage2 = ImageUtil.createImage(bImage.getWidth(), bImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        bImage2.getGraphics().drawImage(bImage, 0, 0, null);
        try {
            OutputStream out = new FileOutputStream(file);
            ImageIO.write(bImage2, "jpg", out);
            out.close();
            clipboardContent.putFiles(List.of(file));
            Clipboard fxClipboard = Clipboard.getSystemClipboard();
            fxClipboard.setContent(clipboardContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static String formatFileSize(long size) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;

        if (size >= gb) {
            return String.format("%.1f GB", (float) size / gb);
        } else if (size >= mb) {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f MB" : "%.1f MB", f);
        } else if (size >= kb) {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f KB" : "%.1f KB", f);
        } else {
            return String.format("%d Byte", size);
        }
    }

    /**
     * 保留指定位数的小数
     */
    public static double roundingDouble(String strDouble, int newScale) {
        return new BigDecimal(strDouble).setScale(newScale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 保留指定位数的小数
     */
    public static double roundingDouble(double value, int newScale) {
        return new BigDecimal(value).setScale(newScale, RoundingMode.HALF_UP).doubleValue();
    }


}
