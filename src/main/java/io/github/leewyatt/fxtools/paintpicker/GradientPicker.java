/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.github.leewyatt.fxtools.paintpicker;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.paintpicker.datamodel.PaintMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import com.intellij.openapi.ui.ComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing version of GradientPicker adapted for IntelliJ IDEA.
 * Provides gradient stop editing, preview, and linear/radial gradient parameters.
 */
public class GradientPicker extends JPanel {

    private static final JBColor TRACK_BG = new JBColor(new Color(230, 230, 230), new Color(60, 60, 60));
    private static final Color TRACK_BORDER = JBColor.border();
    private static final Color PREVIEW_BORDER = JBColor.border();
    private static final JBColor CHECKER_WHITE = new JBColor(Color.WHITE, new Color(60, 60, 60));
    private static final JBColor CHECKER_GRAY = new JBColor(new Color(204, 204, 204), new Color(70, 70, 70));

    // Track panel where stops are placed (null layout for absolute positioning)
    private final JPanel trackPane;
    // Preview panel that shows the current gradient
    private final PreviewPanel previewPanel;

    // Linear gradient sliders
    private final JSlider startX_slider;
    private final JSlider startY_slider;
    private final JSlider endX_slider;
    private final JSlider endY_slider;

    // Radial gradient sliders
    private final JSlider centerX_slider;
    private final JSlider centerY_slider;

    // Shared controls
    private final JBCheckBox proportionalCheckbox;
    private final ComboBox<CycleMethodItem> cycleMethodCombo;

    // Radial-specific controls
    private final JPanel radialContainer;
    private final SliderControl focusAngleSlider;
    private final SliderControl focusDistanceSlider;
    private final SliderControl radiusSlider;

    private final List<GradientPickerStop> gradientPickerStops = new ArrayList<>();
    private final int maxStops = 12;

    private boolean updating = false;

    // Callback for gradient changes
    private GradientChangeCallback gradientChangeCallback;
    // Callback when a stop is selected (to update color picker)
    private StopSelectedCallback stopSelectedCallback;

    @FunctionalInterface
    public interface GradientChangeCallback {
        void onGradientChanged(Paint gradient);
    }

    @FunctionalInterface
    public interface StopSelectedCallback {
        void onStopSelected(Color stopColor);
    }

    public void setGradientChangeCallback(GradientChangeCallback callback) {
        this.gradientChangeCallback = callback;
    }

    public void setStopSelectedCallback(StopSelectedCallback callback) {
        this.stopSelectedCallback = callback;
    }

    /**
     * Wrapper for CycleMethod to display friendly names in JComboBox.
     */
    private static class CycleMethodItem {
        final MultipleGradientPaint.CycleMethod method;

        CycleMethodItem(MultipleGradientPaint.CycleMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.name();
        }
    }

    public GradientPicker() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // ---- Stop track ----
        JPanel trackContainer = new JPanel(new BorderLayout());
        trackContainer.setAlignmentX(LEFT_ALIGNMENT);
        trackContainer.setPreferredSize(new Dimension(JBUI.scale(300), JBUI.scale(32)));
        trackContainer.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(32)));
        trackContainer.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(TRACK_BORDER),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        trackContainer.setBackground(TRACK_BG);

        trackPane = new JPanel(null); // null layout for absolute positioning
        trackPane.setOpaque(false);
        trackPane.setFocusable(true);
        trackPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                trackPane.requestFocusInWindow();
                onTrackPressed(e);
            }
        });
        trackPane.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedStop();
                    e.consume();
                }
            }
        });
        // Reposition stops when track resizes
        trackPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                repositionAllStops();
            }
        });

        trackContainer.add(trackPane, BorderLayout.CENTER);
        add(trackContainer);
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- Gradient preview + sliders ----
        previewPanel = new PreviewPanel();

        // Linear sliders (0-100 mapped to 0.0-1.0)
        startX_slider = createUnitSlider(0);
        startY_slider = createUnitSlider(0);
        endX_slider = createUnitSlider(100);
        endY_slider = createUnitSlider(100);

        // Radial sliders
        centerX_slider = createUnitSlider(50);
        centerY_slider = createUnitSlider(50);

        // Build the preview grid: [startY] [preview] [endY]
        //                          [      startX/endX     ]
        JPanel previewGrid = new JPanel(new GridBagLayout());
        previewGrid.setOpaque(false);
        previewGrid.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(0);

        // Left slider (startY / centerY stacked)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        JPanel leftSliderPanel = new JPanel(new BorderLayout());
        leftSliderPanel.setOpaque(false);
        startY_slider.setOrientation(JSlider.VERTICAL);
        centerY_slider.setOrientation(JSlider.VERTICAL);
        leftSliderPanel.add(startY_slider, BorderLayout.CENTER);
        leftSliderPanel.setPreferredSize(new Dimension(JBUI.scale(30), JBUI.scale(150)));
        previewGrid.add(leftSliderPanel, gbc);

        // Center preview
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        previewPanel.setPreferredSize(JBUI.size(200, 150));
        previewGrid.add(previewPanel, gbc);

        // Right slider (endY)
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weightx = 0;
        gbc.weighty = 0;
        JPanel rightSliderPanel = new JPanel(new BorderLayout());
        rightSliderPanel.setOpaque(false);
        endY_slider.setOrientation(JSlider.VERTICAL);
        rightSliderPanel.add(endY_slider, BorderLayout.CENTER);
        rightSliderPanel.setPreferredSize(new Dimension(JBUI.scale(30), JBUI.scale(150)));
        previewGrid.add(rightSliderPanel, gbc);

        // Top slider (startX / centerX)
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel topSliderPanel = new JPanel(new BorderLayout());
        topSliderPanel.setOpaque(false);
        topSliderPanel.add(startX_slider, BorderLayout.CENTER);
        topSliderPanel.setPreferredSize(new Dimension(JBUI.scale(200), JBUI.scale(24)));
        previewGrid.add(topSliderPanel, gbc);

        // Bottom slider (endX)
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel bottomSliderPanel = new JPanel(new BorderLayout());
        bottomSliderPanel.setOpaque(false);
        bottomSliderPanel.add(endX_slider, BorderLayout.CENTER);
        bottomSliderPanel.setPreferredSize(new Dimension(JBUI.scale(200), JBUI.scale(24)));
        previewGrid.add(bottomSliderPanel, gbc);

        add(previewGrid);
        // add(Box.createVerticalStrut(JBUI.scale(1)));

        // ---- Unified settings panel (GridBagLayout, 3 columns) ----
        // Col 0: labels (fixed width, right-aligned)
        // Col 1: controls (fill horizontal)
        // Col 2: input fields (fixed width, only for sliders)
        int labelW = JBUI.scale(95);

        JPanel paramsPanel = new JPanel(new GridBagLayout());
        paramsPanel.setOpaque(false);
        paramsPanel.setAlignmentX(LEFT_ALIGNMENT);
        gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(2, 4);

        // Row 0: cycleMethod
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.fill = GridBagConstraints.NONE;
        JBLabel cycleLabel = new JBLabel("cycleMethod");
        cycleLabel.setPreferredSize(new Dimension(labelW, cycleLabel.getPreferredSize().height));
        cycleLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        paramsPanel.add(cycleLabel, gbc);

        cycleMethodCombo = new ComboBox<>(new CycleMethodItem[]{
                new CycleMethodItem(MultipleGradientPaint.CycleMethod.NO_CYCLE),
                new CycleMethodItem(MultipleGradientPaint.CycleMethod.REFLECT),
                new CycleMethodItem(MultipleGradientPaint.CycleMethod.REPEAT)
        });
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        paramsPanel.add(cycleMethodCombo, gbc);
        gbc.gridwidth = 1;

        // Row 1: proportional
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_END;
        JBLabel propLabel = new JBLabel("proportional");
        propLabel.setPreferredSize(new Dimension(labelW, propLabel.getPreferredSize().height));
        propLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        paramsPanel.add(propLabel, gbc);

        proportionalCheckbox = new JBCheckBox();
        proportionalCheckbox.setSelected(true);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        paramsPanel.add(proportionalCheckbox, gbc);
        gbc.gridwidth = 1;

        add(paramsPanel);

        // ---- Radial-specific controls (separator + 3 sliders) ----
        radiusSlider = new SliderControl("radius", 0.0, 1.0, 0.5);
        focusDistanceSlider = new SliderControl("focusDistance", -1.0, 1.0, 0.0);
        focusAngleSlider = new SliderControl("focusAngle", -180.0, 180.0, 0.0);

        radialContainer = new JPanel();
        radialContainer.setOpaque(false);
        radialContainer.setLayout(new BoxLayout(radialContainer, BoxLayout.Y_AXIS));
        radialContainer.setAlignmentX(LEFT_ALIGNMENT);
        radialContainer.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.emptyTop(4)));

        radiusSlider.setAlignmentX(LEFT_ALIGNMENT);
        focusDistanceSlider.setAlignmentX(LEFT_ALIGNMENT);
        focusAngleSlider.setAlignmentX(LEFT_ALIGNMENT);
        radialContainer.add(radiusSlider);
        radialContainer.add(Box.createVerticalStrut(JBUI.scale(2)));
        radialContainer.add(focusDistanceSlider);
        radialContainer.add(Box.createVerticalStrut(JBUI.scale(2)));
        radialContainer.add(focusAngleSlider);
        radialContainer.setVisible(false);
        add(radialContainer);

        // ---- Initialize with two default stops ----
        GradientPickerStop black = addStop(0.0, 1.0, 0.0, Color.BLACK);
        addStop(0.0, 1.0, 1.0, Color.WHITE);
        setSelectedStop(black);

        // ---- Wire up change listeners ----
        initializeListeners();
    }

    private JSlider createUnitSlider(int initValue) {
        JSlider slider = new JSlider(0, 100, initValue);
        slider.setFocusable(false);
        return slider;
    }

    private double getSliderValue(JSlider slider) {
        return slider.getValue() / 100.0;
    }

    private void setSliderValue(JSlider slider, double value) {
        slider.setValue((int) Math.round(value * 100));
    }

    private void initializeListeners() {
        // Slider change listeners
        javax.swing.event.ChangeListener sliderChange = e -> {
            if (updating) {
                return;
            }
            onGradientParamChanged();
        };

        startX_slider.addChangeListener(sliderChange);
        startY_slider.addChangeListener(sliderChange);
        endX_slider.addChangeListener(sliderChange);
        endY_slider.addChangeListener(sliderChange);
        centerX_slider.addChangeListener(sliderChange);
        centerY_slider.addChangeListener(sliderChange);

        radiusSlider.addValueChangeListener(e -> {
            if (!updating) {
                onGradientParamChanged();
            }
        });
        focusDistanceSlider.addValueChangeListener(e -> {
            if (!updating) {
                onGradientParamChanged();
            }
        });
        focusAngleSlider.addValueChangeListener(e -> {
            if (!updating) {
                onGradientParamChanged();
            }
        });

        proportionalCheckbox.addActionListener(e -> {
            if (!updating) {
                onGradientParamChanged();
            }
        });
        cycleMethodCombo.addActionListener(e -> {
            if (!updating) {
                onGradientParamChanged();
            }
        });
    }

    private void onGradientParamChanged() {
        PaintMode mode = getCurrentMode();
        Paint value = getValue(mode);
        updatePreview(value);
        fireGradientChanged(value);
    }

    // ---- Current mode tracking ----
    private PaintMode currentMode = PaintMode.LINEAR;

    private PaintMode getCurrentMode() {
        return currentMode;
    }

    // ---- Public API ----

    /**
     * Returns the current gradient value as an AWT Paint.
     */
    public Paint getValue(PaintMode paintMode) {
        float[] fractions = getStopFractions();
        Color[] colors = getStopColors();
        if (fractions.length == 0) {
            // 0 stops → transparent gradient
            fractions = new float[]{0f, 1f};
            colors = new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0)};
        } else if (fractions.length == 1) {
            // 1 stop → solid color gradient
            fractions = new float[]{0f, 1f};
            colors = new Color[]{colors[0], colors[0]};
        }

        MultipleGradientPaint.CycleMethod cycleMethod = getSelectedCycleMethod();

        if (paintMode == PaintMode.LINEAR) {
            float sx = (float) getSliderValue(startX_slider);
            float sy = (float) getSliderValue(startY_slider);
            float ex = (float) getSliderValue(endX_slider);
            float ey = (float) getSliderValue(endY_slider);
            // Avoid identical start/end points
            if (sx == ex && sy == ey) {
                ex = sx + 0.001f;
            }
            return new LinearGradientPaint(
                    new Point2D.Float(sx, sy),
                    new Point2D.Float(ex, ey),
                    fractions, colors, cycleMethod);
        } else {
            // RADIAL
            float cx = (float) getSliderValue(centerX_slider);
            float cy = (float) getSliderValue(centerY_slider);
            float radius = (float) radiusSlider.getValue();
            if (radius <= 0) {
                radius = 0.001f;
            }
            float focusAngle = (float) focusAngleSlider.getValue();
            float focusDist = (float) focusDistanceSlider.getValue();
            // Compute focus point from angle and distance
            float fx = (float) (cx + focusDist * radius * Math.cos(Math.toRadians(focusAngle)));
            float fy = (float) (cy + focusDist * radius * Math.sin(Math.toRadians(focusAngle)));
            return new RadialGradientPaint(
                    new Point2D.Float(cx, cy), radius,
                    new Point2D.Float(fx, fy),
                    fractions, colors, cycleMethod);
        }
    }

    public GradientPickerStop getSelectedStop() {
        for (GradientPickerStop stop : gradientPickerStops) {
            if (stop.isSelected()) {
                return stop;
            }
        }
        return null;
    }

    /**
     * Updates the UI to reflect a LinearGradientPaint.
     */
    public void updateUI(LinearGradientPaint linear) {
        updating = true;
        Point2D start = linear.getStartPoint();
        Point2D end = linear.getEndPoint();
        setSliderValue(startX_slider, start.getX());
        setSliderValue(startY_slider, start.getY());
        setSliderValue(endX_slider, end.getX());
        setSliderValue(endY_slider, end.getY());
        proportionalCheckbox.setSelected(true); // AWT always uses absolute coords; treat as proportional if 0-1 range
        setCycleMethod(linear.getCycleMethod());

        removeAllStops();
        float[] fractions = linear.getFractions();
        Color[] colors = linear.getColors();
        GradientPickerStop firstStop = null;
        for (int i = 0; i < fractions.length; i++) {
            GradientPickerStop stop = addStop(0.0, 1.0, fractions[i], colors[i]);
            if (i == 0) {
                firstStop = stop;
            }
        }

        setMode(PaintMode.LINEAR);
        updatePreview(linear);
        updating = false;
        if (firstStop != null) {
            onStopSelected(firstStop);
        }
    }

    /**
     * Updates the UI to reflect a RadialGradientPaint.
     */
    public void updateUI(RadialGradientPaint radial) {
        updating = true;
        Point2D center = radial.getCenterPoint();
        setSliderValue(centerX_slider, center.getX());
        setSliderValue(centerY_slider, center.getY());

        radiusSlider.setValue(radial.getRadius());
        proportionalCheckbox.setSelected(true);
        setCycleMethod(radial.getCycleMethod());

        // Compute focus angle and distance from focus point
        Point2D focus = radial.getFocusPoint();
        double dx = focus.getX() - center.getX();
        double dy = focus.getY() - center.getY();
        double focusAngle = Math.toDegrees(Math.atan2(dy, dx));
        double focusDistance = Math.sqrt(dx * dx + dy * dy);
        if (radial.getRadius() > 0) {
            focusDistance /= radial.getRadius();
        }
        focusAngleSlider.setValue(focusAngle);
        focusDistanceSlider.setValue(focusDistance);

        removeAllStops();
        float[] fractions = radial.getFractions();
        Color[] colors = radial.getColors();
        GradientPickerStop firstStop = null;
        for (int i = 0; i < fractions.length; i++) {
            GradientPickerStop stop = addStop(0.0, 1.0, fractions[i], colors[i]);
            if (i == 0) {
                firstStop = stop;
            }
        }

        setMode(PaintMode.RADIAL);
        updatePreview(radial);
        updating = false;
        if (firstStop != null) {
            onStopSelected(firstStop);
        }
    }

    public void updatePreview(Paint paint) {
        previewPanel.setGradientPaint(paint);
    }

    public void setMode(PaintMode mode) {
        this.currentMode = mode;
        boolean isLinear = (mode == PaintMode.LINEAR);

        startX_slider.setVisible(isLinear);
        startY_slider.setVisible(isLinear);
        endX_slider.setVisible(isLinear);
        endY_slider.setVisible(isLinear);

        // For radial, swap sliders in the same positions
        centerX_slider.setVisible(!isLinear);
        centerY_slider.setVisible(!isLinear);
        radialContainer.setVisible(!isLinear);

        // Swap the sliders in the layout
        if (!isLinear) {
            swapSliderInParent(startX_slider, centerX_slider);
            swapSliderInParent(startY_slider, centerY_slider);
        } else {
            swapSliderInParent(centerX_slider, startX_slider);
            swapSliderInParent(centerY_slider, startY_slider);
        }

        revalidate();
        repaint();
    }

    private void swapSliderInParent(JSlider hideSlider, JSlider showSlider) {
        java.awt.Container parent = hideSlider.getParent();
        if (parent != null && parent.isAncestorOf(hideSlider)) {
            Object constraints = null;
            if (parent.getLayout() instanceof BorderLayout bl) {
                constraints = bl.getConstraints(hideSlider);
            }
            parent.remove(hideSlider);
            if (constraints != null) {
                parent.add(showSlider, constraints);
            } else {
                parent.add(showSlider);
            }
            parent.revalidate();
            parent.repaint();
        }
    }

    // ---- Stop management ----

    GradientPickerStop addStop(double min, double max, double offset, Color color) {
        if (gradientPickerStops.size() >= maxStops) {
            return null;
        }
        GradientPickerStop stop = new GradientPickerStop(this, min, max, offset, color);
        trackPane.add(stop);
        gradientPickerStops.add(stop);
        // Position after adding
        SwingUtilities.invokeLater(stop::valueToPixels);
        return stop;
    }

    void removeStop(GradientPickerStop stop) {
        trackPane.remove(stop);
        gradientPickerStops.remove(stop);
        trackPane.revalidate();
        trackPane.repaint();
    }

    private void deleteSelectedStop() {
        GradientPickerStop selected = getSelectedStop();
        if (selected == null) {
            return;
        }
        int idx = gradientPickerStops.indexOf(selected);
        removeStop(selected);
        // Auto-select adjacent stop: prefer right neighbor, fall back to left
        if (!gradientPickerStops.isEmpty()) {
            int selectIdx = Math.min(idx, gradientPickerStops.size() - 1);
            onStopSelected(gradientPickerStops.get(selectIdx));
        }
        onStopChanged();
    }

    void removeAllStops() {
        trackPane.removeAll();
        gradientPickerStops.clear();
        trackPane.revalidate();
        trackPane.repaint();
    }

    public void setSelectedStop(GradientPickerStop target) {
        for (GradientPickerStop stop : gradientPickerStops) {
            stop.setSelected(false);
        }
        if (target != null) {
            target.setSelected(true);
        }
    }

    /**
     * Called by GradientPickerStop when it is selected.
     */
    void onStopSelected(GradientPickerStop stop) {
        setSelectedStop(stop);
        if (stopSelectedCallback != null) {
            stopSelectedCallback.onStopSelected(stop.getColor());
        }
    }

    /**
     * Called by GradientPickerStop when offset or color changes.
     */
    void onStopChanged() {
        PaintMode mode = getCurrentMode();
        Paint value = getValue(mode);
        updatePreview(value);
        fireGradientChanged(value);
    }

    private void onTrackPressed(MouseEvent e) {
        double trackWidth = trackPane.getWidth();
        if (trackWidth <= 0) {
            return;
        }
        double offset = e.getX() / trackWidth;
        offset = Math.max(0, Math.min(1, offset));
        // Use current selected stop's color or black
        GradientPickerStop selected = getSelectedStop();
        Color color = selected != null ? selected.getColor() : Color.BLACK;
        GradientPickerStop newStop = addStop(0.0, 1.0, offset, color);
        if (newStop != null) {
            onStopSelected(newStop);
        }
        onStopChanged();
    }

    private void repositionAllStops() {
        for (GradientPickerStop stop : gradientPickerStops) {
            stop.valueToPixels();
        }
    }

    private void fireGradientChanged(Paint paint) {
        if (gradientChangeCallback != null) {
            gradientChangeCallback.onGradientChanged(paint);
        }
    }

    private float[] getStopFractions() {
        List<GradientPickerStop> sorted = new ArrayList<>(gradientPickerStops);
        sorted.sort((a, b) -> Double.compare(a.getOffset(), b.getOffset()));
        float[] fractions = new float[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            fractions[i] = (float) sorted.get(i).getOffset();
            // Ensure strictly increasing
            if (i > 0 && fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + 0.001f;
            }
        }
        return fractions;
    }

    private Color[] getStopColors() {
        List<GradientPickerStop> sorted = new ArrayList<>(gradientPickerStops);
        sorted.sort((a, b) -> Double.compare(a.getOffset(), b.getOffset()));
        Color[] colors = new Color[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            colors[i] = sorted.get(i).getColor();
        }
        return colors;
    }

    private MultipleGradientPaint.CycleMethod getSelectedCycleMethod() {
        CycleMethodItem item = (CycleMethodItem) cycleMethodCombo.getSelectedItem();
        return item != null ? item.method : MultipleGradientPaint.CycleMethod.NO_CYCLE;
    }

    private void setCycleMethod(MultipleGradientPaint.CycleMethod method) {
        for (int i = 0; i < cycleMethodCombo.getItemCount(); i++) {
            if (cycleMethodCombo.getItemAt(i).method == method) {
                cycleMethodCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    // =====================================================
    // Inner class: Preview panel that renders the gradient
    // =====================================================
    private static class PreviewPanel extends JPanel {
        private Paint gradientPaint;

        PreviewPanel() {
            setBorder(JBUI.Borders.customLine(PREVIEW_BORDER));
        }

        void setGradientPaint(Paint paint) {
            this.gradientPaint = paint;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }

            // Draw checkerboard background for transparency
            drawCheckerboard(g2, w, h);

            if (gradientPaint != null) {
                // Scale proportional gradient paint to actual panel size
                Paint scaledPaint = scaleToPanel(gradientPaint, w, h);
                g2.setPaint(scaledPaint);
                g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 5, 5));
            }

            g2.dispose();
        }

        private Paint scaleToPanel(Paint paint, int w, int h) {
            if (paint instanceof LinearGradientPaint lg) {
                // Scale from 0-1 proportional coordinates to pixel coordinates
                Point2D start = new Point2D.Float(
                        (float) (lg.getStartPoint().getX() * w),
                        (float) (lg.getStartPoint().getY() * h));
                Point2D end = new Point2D.Float(
                        (float) (lg.getEndPoint().getX() * w),
                        (float) (lg.getEndPoint().getY() * h));
                // Avoid zero-length gradient
                if (start.equals(end)) {
                    end = new Point2D.Float((float) (start.getX() + 1), (float) start.getY());
                }
                return new LinearGradientPaint(start, end,
                        lg.getFractions(), lg.getColors(), lg.getCycleMethod());
            } else if (paint instanceof RadialGradientPaint rg) {
                float scale = Math.min(w, h);
                Point2D center = new Point2D.Float(
                        (float) (rg.getCenterPoint().getX() * w),
                        (float) (rg.getCenterPoint().getY() * h));
                float radius = rg.getRadius() * scale;
                if (radius <= 0) {
                    radius = 1;
                }
                Point2D focus = new Point2D.Float(
                        (float) (rg.getFocusPoint().getX() * w),
                        (float) (rg.getFocusPoint().getY() * h));
                return new RadialGradientPaint(center, radius, focus,
                        rg.getFractions(), rg.getColors(), rg.getCycleMethod());
            }
            return paint;
        }

        private void drawCheckerboard(Graphics2D g, int width, int height) {
            int cellSize = 6;
            for (int y = 0; y < height; y += cellSize) {
                for (int x = 0; x < width; x += cellSize) {
                    boolean isWhite = ((x / cellSize) + (y / cellSize)) % 2 == 0;
                    g.setColor(isWhite ? CHECKER_WHITE : CHECKER_GRAY);
                    g.fillRect(x, y, cellSize, cellSize);
                }
            }
        }
    }
}
