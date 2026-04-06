package io.github.leewyatt.fxtools.paintpicker;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * A label + text field + slider combination for double value input.
 */
public class SliderControl extends JPanel {

    private static final String PROP_VALUE = "sliderValue";

    private final DoubleField textField;
    private final JSlider slider;

    private final int roundingFactor = 100;
    private final double min;
    private final double max;

    private boolean updating = false;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public SliderControl(String text, double min, double max, double initVal) {
        this.min = min;
        this.max = max;

        setLayout(new GridBagLayout());
        setOpaque(false);

        JBLabel label = new JBLabel(text);
        textField = new DoubleField(6);
        slider = new JSlider(toSliderInt(min), toSliderInt(max), toSliderInt(initVal));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1, 2);
        gbc.gridy = 0;

        // Col 0: label (right-aligned, fixed width)
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.fill = GridBagConstraints.NONE;
        label.setPreferredSize(new Dimension(JBUI.scale(95), label.getPreferredSize().height));
        label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        add(label, gbc);

        // Col 1: slider (fill horizontal)
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(slider, gbc);

        // Col 2: text field (fixed width)
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        add(textField, gbc);

        textField.setText(Double.toString(initVal));

        slider.addChangeListener(e -> {
            if (updating) { return; }
            updating = true;
            double val = round(fromSliderInt(slider.getValue()));
            textField.setText(Double.toString(val));
            pcs.firePropertyChange(PROP_VALUE, null, val);
            updating = false;
        });

        textField.addActionListener(e -> {
            if (updating) { return; }
            updating = true;
            try {
                double value = Double.parseDouble(textField.getText());
                double rounded = Math.max(min, Math.min(max, round(value)));
                textField.setText(Double.toString(rounded));
                slider.setValue(toSliderInt(rounded));
                pcs.firePropertyChange(PROP_VALUE, null, rounded);
                textField.selectAll();
            } catch (NumberFormatException ex) {
                // ignore
            }
            updating = false;
        });

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    incOrDecFieldValue(0.1);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    incOrDecFieldValue(-0.1);
                }
            }
        });
    }

    public JSlider getSlider() { return slider; }

    public double getValue() {
        return fromSliderInt(slider.getValue());
    }

    public void setValue(double value) {
        updating = true;
        double rounded = round(value);
        slider.setValue(toSliderInt(rounded));
        textField.setText(Double.toString(rounded));
        updating = false;
    }

    public void addValueChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(PROP_VALUE, listener);
    }

    public void removeValueChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(PROP_VALUE, listener);
    }

    public void addSliderChangeListener(ChangeListener listener) {
        slider.addChangeListener(listener);
    }

    private void incOrDecFieldValue(double delta) {
        if (updating) { return; }
        updating = true;
        try {
            double value = round(Double.parseDouble(textField.getText()) + delta);
            slider.setValue(toSliderInt(value));
            textField.setText(Double.toString(value));
            pcs.firePropertyChange(PROP_VALUE, null, value);
        } catch (NumberFormatException ex) {
            // ignore
        }
        updating = false;
    }

    private int toSliderInt(double value) {
        return (int) Math.round(value * roundingFactor);
    }

    private double fromSliderInt(int value) {
        return (double) value / roundingFactor;
    }

    private double round(double value) {
        return Math.round(value * roundingFactor) / (double) roundingFactor;
    }
}
