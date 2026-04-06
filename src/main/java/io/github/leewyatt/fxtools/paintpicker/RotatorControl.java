package io.github.leewyatt.fxtools.paintpicker;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * A label + text field + rotation dial combination for angle input.
 */
public class RotatorControl extends JPanel {

    private static final String PROP_ROTATION = "rotation";
    private static final Color DIAL_BG = new JBColor(new Color(210, 210, 210), new Color(80, 80, 80));
    private static final Color DIAL_BORDER = new JBColor(new Color(150, 150, 150), new Color(110, 110, 110));
    private static final Color HANDLE_COLOR = new JBColor(new Color(220, 50, 50), new Color(240, 70, 70));

    private final DoubleField textField;
    private final DialPanel dialPanel;

    private double rotation = 0.0;
    private final int roundingFactor = 100;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public RotatorControl(String text) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JBLabel label = new JBLabel(text);
        textField = new DoubleField(6);
        dialPanel = new DialPanel();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1, 2);
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0.4;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.fill = GridBagConstraints.NONE;
        add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        add(textField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = JBUI.insets(1, 6, 1, 2);
        add(dialPanel, gbc);

        textField.setText("0.0");

        textField.addActionListener(e -> {
            try {
                setRotation(round(Double.parseDouble(textField.getText())));
                textField.selectAll();
            } catch (NumberFormatException ex) {
                // ignore
            }
        });
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double value) {
        double old = this.rotation;
        this.rotation = round(value);
        textField.setText(Double.toString(this.rotation));
        dialPanel.repaint();
        pcs.firePropertyChange(PROP_ROTATION, old, this.rotation);
    }

    public void addRotationChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(PROP_ROTATION, listener);
    }

    public void removeRotationChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(PROP_ROTATION, listener);
    }

    private double round(double value) {
        return Math.round(value * roundingFactor) / (double) roundingFactor;
    }

    private class DialPanel extends JPanel {
        private static final int DIAL_SIZE = 28;

        DialPanel() {
            int size = JBUI.scale(DIAL_SIZE);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setOpaque(false);

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleMouse(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMouse(e);
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private void handleMouse(MouseEvent e) {
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            setRotation(Math.toDegrees(Math.atan2(e.getY() - cy, e.getX() - cx)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            double radius = Math.min(cx, cy) - 2;

            g2.setColor(DIAL_BG);
            g2.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
            g2.setColor(DIAL_BORDER);
            g2.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

            double rad = Math.toRadians(rotation);
            double hx = cx + radius * 0.8 * Math.cos(rad);
            double hy = cy + radius * 0.8 * Math.sin(rad);
            g2.setColor(HANDLE_COLOR);
            g2.draw(new Line2D.Double(cx, cy, hx, hy));
            g2.fill(new Ellipse2D.Double(hx - 3, hy - 3, 6, 6));

            g2.dispose();
        }
    }
}
