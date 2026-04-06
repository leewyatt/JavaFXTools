package io.github.leewyatt.fxtools.paintpicker;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.paintpicker.datamodel.PaintMode;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;

/**
 * Main paint editor control that supports Color, LinearGradient, and RadialGradient editing.
 */
public class PaintPicker extends JPanel {

    private final JPanel rootPanel;
    private final ModeSegmentedControl modeControl;
    private final PaintPickerController controller;

    public PaintPicker() {
        setLayout(new BorderLayout());

        modeControl = new ModeSegmentedControl("Color", "Linear", "Radial");

        rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.setOpaque(false);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        modeControl.setAlignmentX(LEFT_ALIGNMENT);
        rootPanel.setAlignmentX(LEFT_ALIGNMENT);
        wrapper.add(modeControl);
        wrapper.add(Box.createVerticalStrut(JBUI.scale(6)));
        wrapper.add(rootPanel);

        add(wrapper, BorderLayout.CENTER);

        controller = new PaintPickerController(rootPanel, modeControl);
    }

    public PaintPicker(Paint paint) {
        this();
        setPaintProperty(paint);
    }

    public static PaintPicker createColorPicker() {
        PaintPicker picker = new PaintPicker();
        picker.controller.setSingleMode(PaintMode.COLOR);
        picker.modeControl.setVisible(false);
        return picker;
    }

    public void setPaintProperty(Paint value) {
        controller.setPaintProperty(value);
        controller.updateUI(value);
    }

    public Paint getPaintProperty() {
        return controller.getPaintProperty();
    }

    public void addPaintChangeListener(PropertyChangeListener listener) {
        controller.addPaintChangeListener(listener);
    }

    public void removePaintChangeListener(PropertyChangeListener listener) {
        controller.removePaintChangeListener(listener);
    }

    public ColorPicker getColorPicker() {
        return controller.getColorPicker();
    }

    public GradientPicker getGradientPicker() {
        return controller.getGradientPicker();
    }

    public PaintMode getMode() {
        return controller.getMode();
    }

    /**
     * N-item segmented toggle control.
     */
    static class ModeSegmentedControl extends JPanel {
        private int selectedIndex;
        private final String[] labels;
        private Runnable onSelectionChanged;

        ModeSegmentedControl(String... labels) {
            this.labels = labels;
            this.selectedIndex = 0;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(-1, JBUI.scale(30)));
            setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(30)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int segW = getWidth() / labels.length;
                    int newIndex = Math.min(e.getX() / segW, labels.length - 1);
                    if (newIndex != selectedIndex) {
                        selectedIndex = newIndex;
                        repaint();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.run();
                        }
                    }
                }
            });
        }

        void setOnSelectionChanged(Runnable r) {
            this.onSelectionChanged = r;
        }

        int getSelectedIndex() {
            return selectedIndex;
        }

        void setSelectedIndex(int index) {
            if (index >= 0 && index < labels.length && index != selectedIndex) {
                selectedIndex = index;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int outerR = JBUI.scale(6);
            int innerR = JBUI.scale(4);
            int pad = JBUI.scale(3);
            int n = labels.length;
            int segW = w / n;

            // Outer container
            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, outerR, outerR));
            g2.setColor(JBColor.border());
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, outerR, outerR));

            Font normalFont = getFont().deriveFont((float) JBUI.scale(12));
            Font boldFont = normalFont.deriveFont(Font.BOLD);

            for (int i = 0; i < n; i++) {
                int x0 = i * segW + pad;
                int sw = segW - pad * 2;
                if (i == n - 1) {
                    sw = w - x0 - pad;
                }

                FontMetrics fm;
                if (i == selectedIndex) {
                    g2.setColor(UIUtil.getListSelectionBackground(true));
                    g2.fill(new RoundRectangle2D.Float(x0, pad, sw, h - pad * 2, innerR, innerR));
                    g2.setFont(boldFont);
                    g2.setColor(UIUtil.getListSelectionForeground(true));
                    fm = g2.getFontMetrics(boldFont);
                } else {
                    g2.setFont(normalFont);
                    g2.setColor(UIUtil.getLabelDisabledForeground());
                    fm = g2.getFontMetrics(normalFont);
                }

                int textW = fm.stringWidth(labels[i]);
                int textX = x0 + (sw - textW) / 2;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(labels[i], textX, textY);
            }

            g2.dispose();
        }
    }
}
