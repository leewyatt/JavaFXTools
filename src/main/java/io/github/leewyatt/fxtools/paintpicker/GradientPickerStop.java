/*
 * Copyright (c) 2016, Gluon and/or its affiliates.
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates.
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
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Swing version of GradientPickerStop adapted for IntelliJ IDEA.
 * Represents a single draggable color stop on the gradient track.
 */
public class GradientPickerStop extends JPanel {

    private static final int STOP_WIDTH = 20;
    private static final int STOP_HEIGHT = 26;
    private static final int CHIP_SIZE = 12;
    private static final int INDICATOR_HEIGHT = 5;

    private static final JBColor STOP_BG = new JBColor(Color.WHITE, new Color(200, 200, 200));
    private static final Color STOP_BORDER = JBColor.border();
    private static final JBColor SELECTION_COLOR = new JBColor(new Color(220, 50, 50), new Color(240, 70, 70));

    private final double min;
    private final double max;
    private double offset;
    private Color color;
    private boolean selected;
    private final GradientPicker gradientPicker;

    private double startDragX;
    private int origX;
    private final double edgeMargin = 2.0;

    public GradientPickerStop(GradientPicker picker, double min, double max, double offset, Color color) {
        this.gradientPicker = picker;
        this.min = min;
        this.max = max;
        this.offset = clamp(min, offset, max);
        this.color = color;

        setPreferredSize(new Dimension(JBUI.scale(STOP_WIDTH), JBUI.scale(STOP_HEIGHT)));
        setSize(JBUI.scale(STOP_WIDTH), JBUI.scale(STOP_HEIGHT));
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onMousePressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                onMouseDragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onMouseReleased(e);
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // Delete key handled by GradientPicker at track level
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double val) {
        offset = clamp(min, val, max);
        valueToPixels();
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color c) {
        this.color = c;
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    /**
     * Position this stop on the track based on its offset value.
     */
    public void valueToPixels() {
        if (getParent() == null) {
            return;
        }
        double trackWidth = getParent().getWidth();
        if (trackWidth <= 0) {
            return;
        }
        double availablePixels = trackWidth - (STOP_WIDTH + edgeMargin);
        double range = max - min;
        double stopValue = clamp(min, offset, max);
        int pixelX = (int) ((availablePixels / range) * stopValue);
        setLocation(pixelX, 0);
    }

    /**
     * Compute offset value from current pixel position.
     */
    private void pixelsToValue() {
        if (getParent() == null) {
            return;
        }
        double trackWidth = getParent().getWidth();
        double availablePixels = trackWidth - (STOP_WIDTH + edgeMargin);
        double range = max - min;
        offset = clamp(min, min + (getX() * (range / availablePixels)), max);
    }

    private void onMousePressed(MouseEvent e) {
        gradientPicker.setSelectedStop(this);
        startDragX = e.getXOnScreen();
        origX = getX();
        // Give focus to track so Delete/Backspace keys are received
        if (getParent() != null) {
            getParent().requestFocusInWindow();
        }

        // Notify gradient picker that this stop was selected -> update color picker
        gradientPicker.onStopSelected(this);
    }

    private void onMouseDragged(MouseEvent e) {
        double dragDelta = e.getXOnScreen() - startDragX;
        double newX = origX + dragDelta;
        double trackWidth = getParent().getWidth();
        newX = clamp(edgeMargin, newX, trackWidth - (STOP_WIDTH + edgeMargin));
        setLocation((int) newX, 0);
        pixelsToValue();
        gradientPicker.onStopChanged();
    }

    private void onMouseReleased(MouseEvent e) {
        pixelsToValue();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();

        // Draw outer rectangle (stop button)
        int rectSize = STOP_WIDTH - 2;
        int rectX = (w - rectSize) / 2;
        int rectY = 0;
        g2.setColor(STOP_BG);
        g2.fillRect(rectX, rectY, rectSize, rectSize);
        g2.setColor(STOP_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(rectX, rectY, rectSize, rectSize);

        // Draw color chip inside
        int chipX = (w - CHIP_SIZE) / 2;
        int chipY = (rectSize - CHIP_SIZE) / 2;
        g2.setColor(color);
        g2.fillRect(chipX, chipY, CHIP_SIZE, CHIP_SIZE);

        // Draw selection indicator (triangle below)
        if (selected) {
            int triCenterX = w / 2;
            int triY = rectSize + 2;
            g2.setColor(SELECTION_COLOR);
            int[] xPoints = {triCenterX - 3, triCenterX + 3, triCenterX};
            int[] yPoints = {triY, triY, triY + INDICATOR_HEIGHT};
            g2.fillPolygon(xPoints, yPoints, 3);
        }

        g2.dispose();
    }

    private static double clamp(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
