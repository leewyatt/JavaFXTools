package io.github.leewyatt.fxtools.paintpicker;

import io.github.leewyatt.fxtools.paintpicker.datamodel.PaintMode;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Manages the state and wiring between ColorPicker, GradientPicker, and mode control.
 */
public class PaintPickerController {

    public static final String PROP_PAINT = "paint";

    public static final Color DEFAULT_COLOR = Color.BLACK;
    public static final LinearGradientPaint DEFAULT_LINEAR = new LinearGradientPaint(
            new Point2D.Float(0, 0), new Point2D.Float(1, 1),
            new float[]{0f, 1f}, new Color[]{Color.BLACK, Color.WHITE},
            MultipleGradientPaint.CycleMethod.NO_CYCLE);
    public static final RadialGradientPaint DEFAULT_RADIAL = new RadialGradientPaint(
            new Point2D.Float(0.5f, 0.5f), 0.5f,
            new float[]{0f, 1f}, new Color[]{Color.BLACK, Color.WHITE},
            MultipleGradientPaint.CycleMethod.NO_CYCLE);

    private final JPanel rootPanel;
    private final PaintPicker.ModeSegmentedControl modeControl;

    private final ColorPicker colorPicker;
    private final GradientPicker gradientPicker;

    private Paint paint;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public PaintPickerController(JPanel rootPanel, PaintPicker.ModeSegmentedControl modeControl) {
        this.rootPanel = rootPanel;
        this.modeControl = modeControl;

        this.colorPicker = new ColorPicker();
        this.gradientPicker = new GradientPicker();
        this.paint = DEFAULT_COLOR;

        colorPicker.setColorChangeCallback(color -> {
            PaintMode mode = getMode();
            switch (mode) {
                case COLOR -> setPaintProperty(color);
                case LINEAR, RADIAL -> {
                    GradientPickerStop selectedStop = gradientPicker.getSelectedStop();
                    if (selectedStop != null) {
                        selectedStop.setColor(color);
                    }
                    Paint gradient = gradientPicker.getValue(mode);
                    gradientPicker.updatePreview(gradient);
                    setPaintProperty(gradient);
                }
            }
        });

        gradientPicker.setGradientChangeCallback(this::setPaintProperty);
        gradientPicker.setStopSelectedCallback(colorPicker::updateUI);

        modeControl.setOnSelectionChanged(() -> {
            int idx = modeControl.getSelectedIndex();
            switch (idx) {
                case 0 -> onColorMode();
                case 1 -> onLinearMode();
                case 2 -> onRadialMode();
            }
        });

        rootPanel.add(colorPicker);
    }

    public Paint getPaintProperty() {
        return paint;
    }

    public void setPaintProperty(Paint value) {
        Paint old = this.paint;
        this.paint = value;
        pcs.firePropertyChange(PROP_PAINT, old, value);
    }

    public void addPaintChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(PROP_PAINT, listener);
    }

    public void removePaintChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(PROP_PAINT, listener);
    }

    public ColorPicker getColorPicker() {
        return colorPicker;
    }

    public GradientPicker getGradientPicker() {
        return gradientPicker;
    }

    public PaintMode getMode() {
        return switch (modeControl.getSelectedIndex()) {
            case 1 -> PaintMode.LINEAR;
            case 2 -> PaintMode.RADIAL;
            default -> PaintMode.COLOR;
        };
    }

    public void updateUI(Paint value) {
        if (value == null) {
            return;
        }
        setModeUI(value);
        if (value instanceof Color color) {
            colorPicker.updateUI(color);
        } else if (value instanceof LinearGradientPaint lg) {
            gradientPicker.updateUI(lg);
        } else if (value instanceof RadialGradientPaint rg) {
            gradientPicker.updateUI(rg);
        }
    }

    void setSingleMode(PaintMode paintMode) {
        Paint value = switch (paintMode) {
            case COLOR -> DEFAULT_COLOR;
            case LINEAR -> DEFAULT_LINEAR;
            case RADIAL -> DEFAULT_RADIAL;
        };
        setPaintProperty(value);
        updateUI(value);
    }

    private void setModeUI(Paint value) {
        boolean isColor = value instanceof Color;
        if (isColor) {
            modeControl.setSelectedIndex(0);
            rootPanel.remove(gradientPicker);
        } else if (value instanceof LinearGradientPaint) {
            modeControl.setSelectedIndex(1);
            if (!isChildOf(rootPanel, gradientPicker)) {
                rootPanel.add(gradientPicker);
            }
        } else if (value instanceof RadialGradientPaint) {
            modeControl.setSelectedIndex(2);
            if (!isChildOf(rootPanel, gradientPicker)) {
                rootPanel.add(gradientPicker);
            }
        }
        colorPicker.setNamedColorsPanelVisible(isColor);
        rootPanel.revalidate();
        rootPanel.repaint();
    }

    private void onColorMode() {
        Color value = colorPicker.getValue();
        rootPanel.remove(gradientPicker);
        colorPicker.setNamedColorsPanelVisible(true);
        rootPanel.revalidate();
        rootPanel.repaint();
        setPaintProperty(value);
    }

    private void onLinearMode() {
        Paint value = gradientPicker.getValue(PaintMode.LINEAR);
        if (!isChildOf(rootPanel, gradientPicker)) {
            rootPanel.add(gradientPicker);
        }
        colorPicker.setNamedColorsPanelVisible(false);
        gradientPicker.setMode(PaintMode.LINEAR);
        gradientPicker.updatePreview(value);
        rootPanel.revalidate();
        rootPanel.repaint();
        setPaintProperty(value);
    }

    private void onRadialMode() {
        Paint value = gradientPicker.getValue(PaintMode.RADIAL);
        if (!isChildOf(rootPanel, gradientPicker)) {
            rootPanel.add(gradientPicker);
        }
        colorPicker.setNamedColorsPanelVisible(false);
        gradientPicker.setMode(PaintMode.RADIAL);
        gradientPicker.updatePreview(value);
        rootPanel.revalidate();
        rootPanel.repaint();
        setPaintProperty(value);
    }

    private static boolean isChildOf(JPanel parent, JPanel child) {
        for (java.awt.Component c : parent.getComponents()) {
            if (c == child) {
                return true;
            }
        }
        return false;
    }
}
