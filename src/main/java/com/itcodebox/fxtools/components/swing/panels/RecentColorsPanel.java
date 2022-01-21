package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.picker.ColorListener;
import icons.PluginIcons;
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
public class RecentColorsPanel extends JComponent {
    private static final int WIDTH = 313;
    private static final int HEIGHT = 65;
    private List<Color> myRecentColors = new ArrayList<Color>();

    class ColorPopupMenu extends JBPopupMenu {
        public ColorPopupMenu(Color color) {
            JBMenuItem menuItemDelete = new JBMenuItem("删除颜色", PluginIcons.Transparent);
            JPanel colorPanel = new JPanel();
            colorPanel.setPreferredSize(new Dimension(16,16));
            //colorPanel.setMinimumSize(new Dimension(10,10));
            //colorPanel.setMaximumSize(new Dimension(10,10));
            colorPanel.setBackground(color);
            JPanel out = new JPanel(null);
            out.setBackground(new Color(0,0,0,0));
            out.add(colorPanel);
            colorPanel.setBounds(5,1,16,16);
            menuItemDelete.add(out,0);

            menuItemDelete.addActionListener(e->{
                if (myRecentColors.contains(color)) {
                    myRecentColors.remove(color);
                    saveColors();
                    refreshPaint();
                }
            });
            add(menuItemDelete);


        }
    }

    public RecentColorsPanel(final ColorListener listener, boolean restoreColors) {
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {

                Color color = getColor(e);
                if (color == null) {
                    return;
                }

                if (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1) {
                    new  ColorPopupMenu(color).show(RecentColorsPanel.this,e.getX(),e.getY());
                }

                if (e.getButton() == MouseEvent.BUTTON1) {
                        listener.colorChanged(color, this);
                }
            }
        });
        if (restoreColors) {
            this.restoreColors();
        }

    }

    @Nullable
    public Color getMostRecentColor() {
        return this.myRecentColors.isEmpty() ? null : (Color)this.myRecentColors.get(this.myRecentColors.size() - 1);
    }

    private void restoreColors() {
        String value = PropertiesComponent.getInstance().getValue("ColorChooser.RecentColors");
        if (value != null) {
            List<String> colors = StringUtil.split(value, ",,,");

            for (String color : colors) {
                if (color.contains("-")) {
                    List<String> components = StringUtil.split(color, "-");
                    if (components.size() == 4) {
                        this.myRecentColors.add(new Color(Integer.parseInt((String) components.get(0)), Integer.parseInt((String) components.get(1)), Integer.parseInt((String) components.get(2)), Integer.parseInt((String) components.get(3))));
                    }
                } else {
                    this.myRecentColors.add(new Color(Integer.parseInt(color)));
                }
            }
        }

    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Color color = this.getColor(event);
        return color != null ? IdeBundle.message("colorpicker.recent.color.tooltip", new Object[]{color.getRed(), color.getGreen(), color.getBlue(), String.format("%.2f", (float)((double)color.getAlpha() / 255.0D))}) : super.getToolTipText(event);
    }

    @Nullable
    private Color getColor(MouseEvent event) {
        Couple<Integer> pair = this.pointToCellCoords(event.getPoint());
        if (pair != null) {
            int ndx = (Integer)pair.second + (Integer)pair.first * 10;
            if (this.myRecentColors.size() > ndx) {
                return (Color)this.myRecentColors.get(ndx);
            }
        }

        return null;
    }

    public void saveColors() {
        List<String> values = new ArrayList<String>();

        for (Color recentColor : this.myRecentColors) {
            if (recentColor == null) {
                break;
            }

            values.add(String.format("%d-%d-%d-%d", recentColor.getRed(), recentColor.getGreen(), recentColor.getBlue(), recentColor.getAlpha()));
        }

        PropertiesComponent.getInstance().setValue("ColorChooser.RecentColors", values.isEmpty() ? null : StringUtil.join(values, ",,,"), (String)null);
    }

    public void appendColor(Color c) {
        if (!this.myRecentColors.contains(c)) {
            this.myRecentColors.add(c);
        }

        if (this.myRecentColors.size() > 20) {
            this.myRecentColors = new ArrayList<Color>(this.myRecentColors.subList(this.myRecentColors.size() - 20, this.myRecentColors.size()));
        }
    }


    public void clearColors() {
        this.myRecentColors.clear();
        saveColors();
        refreshPaint();
    }

    @Nullable
    private Couple<Integer> pointToCellCoords(Point p) {
        int x = p.x;
        int y = p.y;
        Insets i = this.getInsets();
        Dimension d = this.getSize();
        int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
        int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;
        int col = (x - left - 2) / 31;
        col = Math.min(col, 9);
        int row = (y - top - 2) / 31;
        row = Math.min(row, 1);
        return row >= 0 && col >= 0 ? Couple.of(row, col) : null;
    }

    @Override
    public Dimension getPreferredSize() {
        return this.getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    public void refreshPaint() {
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Insets i = this.getInsets();
        Dimension d = this.getSize();
        int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
        int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;
        g.setColor(Color.WHITE);
        g.fillRect(left, top, WIDTH, HEIGHT);
        g.setColor(Color.GRAY);
        g.drawLine(left + 1, i.top + 32, left + WIDTH - 3, i.top + 32);
        g.drawRect(left + 1, top + 1, 310, 62);

        int r;
        for(r = 1; r < 10; ++r) {
            g.drawLine(left + 1 + r * 31, top + 1, left + 1 + r * 31, top + HEIGHT - 3);
        }

        for(r = 0; r < this.myRecentColors.size(); ++r) {
            int row = r / 10;
            int col = r % 10;
            Color color = (Color)this.myRecentColors.get(r);
            g.setColor(color);
            g.fillRect(left + 2 + col * 30 + col + 1, top + 2 + row * 30 + row + 1, 28, 28);
        }

    }
}
