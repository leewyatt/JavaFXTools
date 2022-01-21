package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.itcodebox.fxtools.components.ShowPaintPicker;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.utils.CustomColorUtil;
import com.itcodebox.fxtools.utils.CustomUtil;
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
public class ColorHBox extends JComponent {
    public static final int CELL_SIZE = 50;
    private static final int SPACE_HEIGHT = 15;
    private final List<Color> colorList = new ArrayList<>();
    private final Font planFont = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    private final Font boldFont = new Font(Font.MONOSPACED, Font.BOLD, 10);
    private String[] webColors;
    private int currentIndex = -1;

    public ColorHBox(ShowPaintPicker showPaintPicker) {
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
                    Color color = getPositionColor(mouseEvent);
                    if (color != null) {
                        Platform.runLater(() -> PaintPickerStage.getInstance().getPaintPicker().setPaintProperty(CustomColorUtil.convertToFXColor(color)));
                    }
                } else {
                    if (mouseEvent.getButton() != MouseEvent.BUTTON3) {
                        return;
                    }
                    Color color = getPositionColor(mouseEvent);
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
                        popupMenu.show(ColorHBox.this, mouseEvent.getX(), mouseEvent.getY());
                    }
                }

            }
        });

    }

    private boolean containsNullValue;

    public void setWebColors(@Nullable String[] webColors) {
        containsNullValue = false;
        currentIndex = -1;
        if (webColors == null) {
            webColors = new String[0];
        }
        this.webColors = webColors;
        colorList.clear();
        for (String webColor : webColors) {
            if (webColor == null && !containsNullValue) {
                containsNullValue = true;
            }
            colorList.add(webColor == null ? null : CustomColorUtil.fromHex(webColor, true));
        }

        repaint();
    }

    private int getPositionColorIndex(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        Insets insets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        int size = colorList.size();
        int whiteCell = 0;
        int max = (int) (Math.ceil(size * 1.0 / 6) * 7);
        for (int i = 0; i < max; i++) {
            int row = i / 7;
            int col = i % 7;
            if (col == 3) {
                whiteCell++;
            } else {
                int index = i - whiteCell;
                if (index >= size) {
                    break;
                }
                if (x >= insets.left + col * CELL_SIZE && x <= insets.left + (col + 1) * CELL_SIZE && y >= insets.top + row * (CELL_SIZE + SPACE_HEIGHT) && y <= insets.top + row * (CELL_SIZE + SPACE_HEIGHT) + CELL_SIZE) {
                    return index;
                }
            }
        }
        return -1;
    }

    @Nullable
    private Color getPositionColor(MouseEvent event) {
        int x = event.getX();
        int y = event.getY();
        Insets insets = getBorder().getBorderInsets(this);
        int size = colorList.size();
        int whiteCell = 0;
        int max = (int) (Math.ceil(size * 1.0 / 6) * 7);
        for (int i = 0; i < max; i++) {
            int row = i / 7;
            int col = i % 7;
            if (col == 3) {
                whiteCell++;
            } else {
                int index = i - whiteCell;
                if (index >= size) {
                    break;
                }
                if (x >= insets.left + col * CELL_SIZE && x <= insets.left + (col + 1) * CELL_SIZE && y >= insets.top + row * (CELL_SIZE + SPACE_HEIGHT) && y <= insets.top + row * (CELL_SIZE + SPACE_HEIGHT) + CELL_SIZE) {
                    return colorList.get(index);
                }
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
        Insets borderInsets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        return new Dimension(CELL_SIZE * 7 + borderInsets.left + borderInsets.right, (int) ((CELL_SIZE + SPACE_HEIGHT) * Math.ceil(colorList.size() * 1.0 / 6)) + borderInsets.top + borderInsets.bottom);
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
        int whiteCell = 0;
        Insets insets = getBorder() == null ? JBUI.emptyInsets() : getBorder().getBorderInsets(this);
        int max = (int) (Math.ceil(size * 1.0 / 6) * 7);
        for (int i = 0; i < max; i++) {
            int row = i / 7;
            int col = i % 7;
            if (col == 3) {
                whiteCell++;
            } else {
                int index = i - whiteCell;
                if (index >= size) {
                    break;
                }

                Color c = colorList.get(index);
                if (c == null) {
                    continue;
                }
                g2d.setColor(c);
                if (containsNullValue) {
                    g2d.fillRect(insets.left + col * CELL_SIZE, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), CELL_SIZE, CELL_SIZE);
                } else {
                    if (col == 0 || col == 4) {
                        g2d.fillRect(insets.left + col * CELL_SIZE + CELL_SIZE / 4, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), (int) (Math.ceil(CELL_SIZE / 4.0 * 3)), CELL_SIZE);
                        g2d.fillRoundRect(insets.left + col * CELL_SIZE, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), CELL_SIZE / 2, CELL_SIZE, 20, 20);
                    } else if (col == 1 || col == 5) {
                        g2d.fillRect(insets.left + col * CELL_SIZE, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), CELL_SIZE, CELL_SIZE);
                    } else {
                        g2d.fillRoundRect(insets.left + col * CELL_SIZE + CELL_SIZE / 2, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), CELL_SIZE / 2, CELL_SIZE, 20, 20);
                        g2d.fillRect(insets.left + col * CELL_SIZE, insets.top + row * (CELL_SIZE + SPACE_HEIGHT), (int) (Math.ceil(CELL_SIZE / 4.0 * 3)), CELL_SIZE);
                    }
                }
                g2d.setColor(currentIndex == index ? JBColor.BLACK : JBColor.DARK_GRAY);
                g2d.setFont(currentIndex == index ? boldFont : planFont);
                g2d.drawString(webColors[i - whiteCell], insets.left + col * CELL_SIZE + 5, insets.top + (row + 1) * CELL_SIZE + SPACE_HEIGHT * row + 10);
            }
        }
    }
}
