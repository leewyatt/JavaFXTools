package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.itcodebox.fxtools.components.ShowPaintPicker;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.utils.CustomColorUtil;
import com.itcodebox.fxtools.utils.CustomUtil;
import com.itcodebox.fxtools.utils.Pair;
import javafx.application.Platform;
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
public class ColorVBox extends JComponent {
    public static final int CELL_WIDTH = 150;
    public static final int CELL_HEIGHT = 8;
    private static final int SPACE_HEIGHT = 15;
    private static final int SPACE_WIDTH = 50;
    private final List<Color> colorList = new ArrayList<>();
    private static final JBColor TITLE_BACKGROUND = new JBColor(Gray._235, Gray._43);
    private List<Pair<Integer, String>> titleList;

    public ColorVBox(ShowPaintPicker showPaintPicker) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                if (mouseEvent.getButton() == MouseEvent.BUTTON1 && mouseEvent.getClickCount() == 2) {
                    Color color = getColor(mouseEvent);
                    if (color != null) {
                        Platform.runLater(() -> PaintPickerStage.getInstance().getPaintPicker().setPaintProperty(CustomColorUtil.convertToFXColor(color)));
                    }
                }else{
                    if (mouseEvent.getButton() != MouseEvent.BUTTON3) {
                        return;
                    }
                    Color color = getColor(mouseEvent);
                    if (color != null) {
                        JBPopupMenu popupMenu = new JBPopupMenu();
                        JBMenuItem webColorItem = new JBMenuItem("Copy Web Color");
                        webColorItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(ColorUtil.toHtmlColor(color));
                        });
                        popupMenu.add(webColorItem);

                        JBMenuItem rgbItem = new JBMenuItem("Copy RGB");
                        rgbItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(CustomColorUtil.toRgb(color, false, CustomColorUtil.TYPE_INT));
                        });
                        popupMenu.add(rgbItem);

                        JBMenuItem rgbFloatItem = new JBMenuItem("Copy RGB Decimal");
                        rgbFloatItem.addActionListener(ex -> {
                            CustomUtil.copyToClipboard(CustomColorUtil.toRgb(color, false, CustomColorUtil.TYPE_DECIMAL));
                        });
                        popupMenu.add(rgbFloatItem);
                        JBMenuItem editItem = new JBMenuItem("Choose/Edit ...");
                        popupMenu.add(editItem);
                        editItem.addActionListener(ex -> {
                            showPaintPicker.show(CustomColorUtil.convertToFXColor(color));
                        });
                        popupMenu.show(ColorVBox.this, mouseEvent.getX(), mouseEvent.getY());
                    }
                }




            }
        });

    }

    public void setWebColors(@Nullable String[] webColors) {
        if (webColors == null) {
            webColors = new String[0];
        }
        colorList.clear();
        for (String webColor : webColors) {
            colorList.add(webColor == null ? null : CustomColorUtil.fromHex(webColor, true));
        }
        repaint();
    }

    public void setWebColors(@Nullable String[] webColors, @Nullable List<Pair<Integer, String>> titleList) {
        this.titleList = titleList;
        if (webColors == null) {
            webColors = new String[0];
        }
        colorList.clear();
        for (String webColor : webColors) {
            colorList.add(webColor == null ? null : CustomColorUtil.fromHex(webColor, true));
        }
        repaint();
    }

    @Nullable
    private Color getColor(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        Insets insets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        int size = colorList.size();
        int max = (int) (Math.ceil(size * 1.0 / 12) * 12);
        int col = 0;
        int startY = 0;
        for (int i = 0; i < max; i += 6) {

            int row = i / 12;
            if (titleList != null) {
                for (Pair<Integer, String> titleInfo : titleList) {
                    if (i == titleInfo.getKey()) {
                        startY += 30;
                        break;
                    }
                }
            }
            //留出空白
            if (col % 2 == 1) {
                for (int j = 0; j < 6; j++) {
                    if (i + j >= colorList.size()) {
                        break;
                    }
                    if (x >= insets.left + CELL_WIDTH + SPACE_WIDTH
                            && x <= insets.left + CELL_WIDTH + SPACE_WIDTH + CELL_WIDTH
                            && y >= startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j
                            && y <= startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j + CELL_HEIGHT) {
                        return colorList.get(i + j);
                    }
                }
            } else {
                for (int j = 0; j < 6; j++) {
                    if (i + j >= colorList.size()) {
                        break;
                    }
                    if (x >= insets.left
                            && x <= insets.left + CELL_WIDTH
                            && y >= startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j
                            && y <= startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j + CELL_HEIGHT) {
                        return colorList.get(i + j);
                    }

                }
            }
            col++;
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return this.getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {

        Insets borderInsets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        return new Dimension(
                CELL_WIDTH * 2 + SPACE_WIDTH + borderInsets.left + borderInsets.right,
                //8*30 就是 标题占用的空间
                (int) ((CELL_HEIGHT * 6 + SPACE_HEIGHT) * Math.ceil(colorList.size() * 1.0 / 12))
                        + (titleList == null ? 0 : titleList.size() * 30)
                        + borderInsets.top + borderInsets.bottom);
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
        int size = colorList.size();
        Insets insets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int max = (int) (Math.ceil(size * 1.0 / 12) * 12);
        int col = 0;
        int startY = 0;
        for (int i = 0; i < max; i += 6) {

            int row = i / 12;

            if (titleList != null) {
                for (Pair<Integer, String> titleInfo : titleList) {
                    if (i == titleInfo.getKey()) {
                        startY += 30;
                        paintTitle(g2d, insets, startY, row, titleInfo.getValue());
                        break;
                    }
                }
            }

            //留出空白
            if (col % 2 == 1) {
                for (int j = 0; j < 6; j++) {
                    if (i + j >= colorList.size()) {
                        break;
                    }
                    g2d.setColor(colorList.get(i + j));
                    g2d.fillRect(insets.left + CELL_WIDTH + SPACE_WIDTH, startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j, CELL_WIDTH, CELL_HEIGHT + 1);
                }
            } else {
                for (int j = 0; j < 6; j++) {
                    if (i + j >= colorList.size()) {
                        break;
                    }
                    g2d.setColor(colorList.get(i + j));
                    g2d.fillRect(insets.left, startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) + CELL_HEIGHT * j, CELL_WIDTH, CELL_HEIGHT + 1);
                }
            }
            col++;
        }
    }

    private void paintTitle(Graphics2D g2d, Insets insets, int startY, int row, String title) {
        g2d.setColor(TITLE_BACKGROUND);
        g2d.fill3DRect(insets.left, startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) - CELL_HEIGHT * 3 - 10, CELL_WIDTH * 2 + SPACE_WIDTH, CELL_HEIGHT * 3, true);
        g2d.setColor(JBColor.BLACK);
        g2d.drawString(title, insets.left + 15, startY + insets.top + row * (CELL_HEIGHT * 6 + SPACE_HEIGHT) - CELL_HEIGHT * 3 + 7);
    }
}
