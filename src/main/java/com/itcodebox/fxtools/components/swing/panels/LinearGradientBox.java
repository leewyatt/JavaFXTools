package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.JBColor;
import com.itcodebox.fxtools.components.ShowPaintPicker;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.components.swing.entites.AwtLinearGradientInfo;
import com.itcodebox.fxtools.utils.CustomColorUtil;
import com.itcodebox.fxtools.utils.CustomUIUtil;
import com.itcodebox.fxtools.utils.CustomUtil;
import com.itcodebox.fxtools.utils.PaintConvertUtil;
import javafx.application.Platform;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author LeeWyatt
 */
public class LinearGradientBox extends JComponent {
    public static final int CELL_W = 350;
    public static final int CELL_H = 80;

    private static final int SPACE_HEIGHT = 15;
    private static final int SPACE_WIDTH = 50;
    private final BasicStroke basicStroke = new BasicStroke(3);
    private int currentIndex = -1;

    private final List<LinearGradientPaint> paintList = new ArrayList<>();

    public LinearGradientBox(ShowPaintPicker showPaintPicker) {
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int tempIndex = getPositionColorIndex(e);
                if (tempIndex != currentIndex) {
                    currentIndex = tempIndex;
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                if (mouseEvent.getButton() == MouseEvent.BUTTON1 && mouseEvent.getClickCount() == 2) {
                    LinearGradientPaint paint = getPaint(mouseEvent);
                    if (paint != null) {
                        LinearGradient fxPaint = convertToFxPaint(paint);
                        Platform.runLater(()->{
                            PaintPickerStage.getInstance().getPaintPicker().setPaintProperty(fxPaint);
                            PaintPickerStage.getInstance().getPaintPicker().setPaintProperty(fxPaint);
                        });
                    }
                }else{
                    if (mouseEvent.getButton() != MouseEvent.BUTTON3) {
                        return;
                    }
                    LinearGradientPaint paint = getPaint(mouseEvent);
                    if (paint != null) {
                        JBPopupMenu popupMenu = new JBPopupMenu();
                        JBMenuItem fxCodeItem = new JBMenuItem("Copy JavaFX Code");
                        fxCodeItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(PaintConvertUtil.toFXJavaCode(convertToFxPaint(paint),false));
                        });
                        popupMenu.add(fxCodeItem);
                        JBMenuItem fxCssItem = new JBMenuItem("Copy JavaFX Css");
                        fxCssItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(PaintConvertUtil.toFXCssCode(convertToFxPaint(paint)));
                        });
                        popupMenu.add(fxCssItem);
                        JBMenuItem swingItem = new JBMenuItem("Copy Swing Code");
                        swingItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(PaintConvertUtil.toSwingCode(convertToFxPaint(paint), getGradientStops(paint)));
                        });
                        popupMenu.add(swingItem);

                        JBMenuItem editItem = new JBMenuItem("Choose/Edit ...");
                        popupMenu.add(editItem);
                        editItem.addActionListener(ex -> {
                            //需要把awt的渐变色转成javaFX的渐变色
                            //showPaintPicker.show(paint);
                            LinearGradient fxPaint = convertToFxPaint(paint);
                            showPaintPicker.show(fxPaint);
                        });
                        popupMenu.show(LinearGradientBox.this, mouseEvent.getX(), mouseEvent.getY());
                    }
                }

            }
        });

    }

    private Stop[] getGradientStops(LinearGradientPaint paint) {
        Stop[] stops = new Stop[paint.getFractions().length];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = new Stop(paint.getFractions()[i], CustomColorUtil.convertToFXColor(paint.getColors()[i]));
        }
        return stops;
    }

    @NotNull
    private LinearGradient convertToFxPaint(LinearGradientPaint paint) {
        Stop[] stops = new Stop[paint.getFractions().length];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = new Stop(paint.getFractions()[i], CustomColorUtil.convertToFXColor(paint.getColors()[i]));
        }
        LinearGradient fxPaint = new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops
        );
        return fxPaint;
    }

    public void setLinearGradientInfos(@Nullable List<AwtLinearGradientInfo> infoList) {
        currentIndex = -1;
        this.paintList.clear();
        if (infoList != null) {
            Insets insets = computeBorder();
            int size = infoList.size();
            for (int i = 0; i < size; i++) {
                AwtLinearGradientInfo info = infoList.get(i);
                paintList.add(new LinearGradientPaint(
                        insets.left,
                        insets.top + i * (CELL_H + SPACE_HEIGHT),
                        insets.left + CELL_W,
                        insets.top + i * (CELL_H + SPACE_HEIGHT),
                        info.getFractions(), info.getColors()));
            }
        }
        repaint();
    }

    private int getPositionColorIndex(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        Insets insets = computeBorder();
        for (int i = 0; i < paintList.size(); i++) {
            if (x >= insets.left && x <= insets.left + CELL_W
                    && y >= insets.top + i * (CELL_H + SPACE_HEIGHT)
                    && y <= insets.top + i * (CELL_H + SPACE_HEIGHT) + CELL_H) {
                return i;
            }
        }
        return -1;
    }

    private LinearGradientPaint getPaint(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        Insets insets = computeBorder();
        for (int i = 0; i < paintList.size(); i++) {
            if (x >= insets.left && x <= insets.left + CELL_W
                    && y >= insets.top + i * (CELL_H + SPACE_HEIGHT)
                    && y <= insets.top + i * (CELL_H + SPACE_HEIGHT) + CELL_H) {
                return paintList.get(i);
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return this.getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
        Insets borderInsets = computeBorder();
        return new Dimension(CELL_W + borderInsets.left + borderInsets.right, (CELL_H + SPACE_HEIGHT) * paintList.size() + borderInsets.top + borderInsets.bottom);
    }

    @Override
    public Dimension getMaximumSize() {
        return this.getMinimumSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        //清除
        g2d.setColor(JBColor.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        Insets insets = computeBorder();
        //绘制
        for (int i = 0; i < paintList.size(); i++) {
            g2d.setPaint(paintList.get(i));
            g2d.fillRect(insets.left, insets.top + i * (CELL_H + SPACE_HEIGHT), CELL_W, CELL_H);
            if (i == currentIndex) {
                g2d.setColor(JBColor.WHITE);
                g2d.setStroke(basicStroke);
                g2d.drawRect(insets.left + 6, insets.top + i * (CELL_H + SPACE_HEIGHT) + 6, CELL_W - 12, CELL_H - 12);
            }
        }

    }

    private Insets computeBorder() {
        return getBorder() == null ? CustomUIUtil.EMPTY_INSETS : getBorder().getBorderInsets(this);
    }
}
