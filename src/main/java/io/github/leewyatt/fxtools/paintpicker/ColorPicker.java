/*
 * Copyright (c) 2022, Gluon and/or its affiliates.
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
 *  - Neither the name of Oracle Corporation and Gluon nor the names of its
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
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.paintpicker.datamodel.NamedColor;
import io.github.leewyatt.fxtools.paintpicker.datamodel.NamedColors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing version of the ColorPicker adapted for IntelliJ IDEA.
 * Provides a saturation/brightness panel, hue slider, alpha slider,
 * and HSB/RGB/Hex text fields for editing a color.
 */
public class ColorPicker extends JPanel {

    public static final String PROP_COLOR = "pickerColor";

    private static final JBColor CHECKER_WHITE = new JBColor(Color.WHITE, new Color(60, 60, 60));
    private static final JBColor CHECKER_GRAY = new JBColor(Color.LIGHT_GRAY, new Color(70, 70, 70));
    private static final JBColor INDICATOR_OUTER = new JBColor(Color.BLACK, new Color(220, 220, 220));
    private static final JBColor INDICATOR_INNER = new JBColor(Color.WHITE, new Color(40, 40, 40));
    private static final Color CHIP_BORDER = JBColor.border();

    private final SatBrightPanel satBrightPanel;
    private final HueSliderPanel hueSliderPanel;
    private final AlphaSliderPanel alphaSliderPanel;
    private final JPanel chipPanel;
    private final JTextField hexaTextfield;

    private final DoubleField alpha_textfield;
    private final DoubleField hue_textfield;
    private final DoubleField saturation_textfield;
    private final DoubleField brightness_textfield;
    private final DoubleField red_textfield;
    private final DoubleField green_textfield;
    private final DoubleField blue_textfield;

    private JPanel namedColorsPanel;
    private JPanel hsbRgbPanel;
    private boolean updating = false;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Callback interface for color changes (used by PaintPickerController)
    private ColorChangeCallback colorChangeCallback;

    @FunctionalInterface
    public interface ColorChangeCallback {
        void onColorChanged(Color color);
    }

    public void setColorChangeCallback(ColorChangeCallback callback) {
        this.colorChangeCallback = callback;
    }

    public ColorPicker() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // ---- Top row: SatBright picker + color chip + hex field ----
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setOpaque(false);
        topRow.setAlignmentX(LEFT_ALIGNMENT);

        satBrightPanel = new SatBrightPanel();
        topRow.add(satBrightPanel, BorderLayout.CENTER);

        // Right side: chip + hex
        JPanel chipAndHex = new JPanel();
        chipAndHex.setOpaque(false);
        chipAndHex.setLayout(new BoxLayout(chipAndHex, BoxLayout.Y_AXIS));
        chipAndHex.setPreferredSize(JBUI.size(70, 80));

        chipPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw checkerboard for transparency
                drawCheckerboard(g, getWidth(), getHeight());
                // Draw color overlay
                Color c = getValue();
                g.setColor(c);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        chipPanel.setPreferredSize(JBUI.size(60, 40));
        chipPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(40)));
        chipPanel.setBorder(JBUI.Borders.customLine(CHIP_BORDER));
        chipPanel.setAlignmentX(LEFT_ALIGNMENT);

        hexaTextfield = new JTextField("#000000", 7);
        hexaTextfield.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(24)));
        hexaTextfield.setAlignmentX(LEFT_ALIGNMENT);

        chipAndHex.add(chipPanel);
        chipAndHex.add(Box.createVerticalStrut(JBUI.scale(2)));
        chipAndHex.add(hexaTextfield);
        chipAndHex.add(Box.createVerticalGlue());

        topRow.add(chipAndHex, BorderLayout.EAST);
        topRow.setPreferredSize(new Dimension(0, JBUI.scale(80)));
        topRow.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(80)));
        add(topRow);
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- Controls area (hue + alpha + HSB/RGB) — fixed height, no stretching ----
        JPanel controlsArea = new JPanel();
        controlsArea.setLayout(new BoxLayout(controlsArea, BoxLayout.Y_AXIS));
        controlsArea.setOpaque(false);
        controlsArea.setAlignmentX(LEFT_ALIGNMENT);

        // Hue slider
        hueSliderPanel = new HueSliderPanel();
        hueSliderPanel.setAlignmentX(LEFT_ALIGNMENT);
        controlsArea.add(hueSliderPanel);
        controlsArea.add(Box.createVerticalStrut(JBUI.scale(3)));

        // Alpha slider + "A:" + text field
        alphaSliderPanel = new AlphaSliderPanel();

        alpha_textfield = new DoubleField(4);
        hue_textfield = new DoubleField(4);
        saturation_textfield = new DoubleField(4);
        brightness_textfield = new DoubleField(4);
        red_textfield = new DoubleField(4);
        green_textfield = new DoubleField(4);
        blue_textfield = new DoubleField(4);

        int fixedH = JBUI.scale(20);
        JPanel sliderWrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(super.getMaximumSize().width, fixedH);
            }
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, fixedH);
            }
        };
        sliderWrapper.setOpaque(false);
        sliderWrapper.add(alphaSliderPanel, BorderLayout.CENTER);

        JPanel alphaRow = new JPanel(new GridBagLayout());
        alphaRow.setOpaque(false);
        alphaRow.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints ac = new GridBagConstraints();
        ac.gridy = 0;
        ac.anchor = GridBagConstraints.CENTER;
        ac.gridx = 0;
        ac.weightx = 1.0;
        ac.fill = GridBagConstraints.HORIZONTAL;
        alphaRow.add(sliderWrapper, ac);
        ac.gridx = 1;
        ac.weightx = 0;
        ac.fill = GridBagConstraints.NONE;
        ac.insets = JBUI.insets(0, 4, 0, 0);
        alphaRow.add(new JBLabel("A:"), ac);
        ac.gridx = 2;
        ac.insets = JBUI.insets(0, 2, 0, 0);
        alphaRow.add(alpha_textfield, ac);
        controlsArea.add(alphaRow);
        controlsArea.add(Box.createVerticalStrut(JBUI.scale(4)));

        // HSB fields | separator | RGB fields
        hsbRgbPanel = new JPanel(new GridBagLayout());
        hsbRgbPanel.setOpaque(false);
        hsbRgbPanel.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(1, 2, 1, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        gbc.gridx = 0;
        hsbRgbPanel.add(hue_textfield, gbc);
        gbc.gridx = 1;
        hsbRgbPanel.add(saturation_textfield, gbc);
        gbc.gridx = 2;
        hsbRgbPanel.add(brightness_textfield, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        gbc.ipadx = 8;
        hsbRgbPanel.add(new JBLabel(""), gbc);
        gbc.ipadx = 0;

        gbc.weightx = 1.0;
        gbc.gridx = 4;
        hsbRgbPanel.add(red_textfield, gbc);
        gbc.gridx = 5;
        hsbRgbPanel.add(green_textfield, gbc);
        gbc.gridx = 6;
        hsbRgbPanel.add(blue_textfield, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        hsbRgbPanel.add(createCenteredLabel("H \u00B0"), gbc);
        gbc.gridx = 1;
        hsbRgbPanel.add(createCenteredLabel("S %"), gbc);
        gbc.gridx = 2;
        hsbRgbPanel.add(createCenteredLabel("B %"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 0;
        hsbRgbPanel.add(new JBLabel(""), gbc);
        gbc.weightx = 1.0;
        gbc.gridx = 4;
        hsbRgbPanel.add(createCenteredLabel("R"), gbc);
        gbc.gridx = 5;
        hsbRgbPanel.add(createCenteredLabel("G"), gbc);
        gbc.gridx = 6;
        hsbRgbPanel.add(createCenteredLabel("B"), gbc);

        controlsArea.add(hsbRgbPanel);

        // Cap the entire controls area height so BoxLayout doesn't stretch it
        Dimension ctrlPref = controlsArea.getPreferredSize();
        controlsArea.setMaximumSize(new Dimension(Short.MAX_VALUE, ctrlPref.height));

        add(controlsArea);
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- Named Colors panel ----
        namedColorsPanel = createNamedColorsPanel();
        namedColorsPanel.setAlignmentX(LEFT_ALIGNMENT);
        add(namedColorsPanel);

        // ---- Initialize values ----
        alpha_textfield.setText("1.0");
        hue_textfield.setText("0");
        saturation_textfield.setText("0");
        brightness_textfield.setText("0");
        red_textfield.setText("0");
        green_textfield.setText("0");
        blue_textfield.setText("0");

        // ---- Wire up events ----
        initializeEvents();
    }

    private JBLabel createCenteredLabel(String text) {
        JBLabel lbl = new JBLabel(text, JBLabel.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(10f));
        return lbl;
    }

    private JPanel createNamedColorsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.emptyTop(6)));

        // Search field
        SearchTextField searchField = new SearchTextField();
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(new JBLabel("Named Colors"), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        panel.add(searchRow, BorderLayout.NORTH);

        List<NamedColor> namedColors = NamedColors.ALL;

        // Color tiles panel — use a FlowLayout wrapper that respects viewport width
        JPanel tilesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2)) {
            @Override
            public Dimension getPreferredSize() {
                // Let FlowLayout wrap by constraining width to parent viewport
                if (getParent() != null) {
                    int parentWidth = getParent().getWidth();
                    if (parentWidth > 0) {
                        // Compute height needed for this width
                        FlowLayout fl = (FlowLayout) getLayout();
                        int hgap = fl.getHgap();
                        int vgap = fl.getVgap();
                        int x = hgap;
                        int y = vgap;
                        int rowHeight = 0;
                        for (java.awt.Component comp : getComponents()) {
                            if (!comp.isVisible()) {
                                continue;
                            }
                            Dimension d = comp.getPreferredSize();
                            if (x + d.width + hgap > parentWidth && x > hgap) {
                                x = hgap;
                                y += rowHeight + vgap;
                                rowHeight = 0;
                            }
                            x += d.width + hgap;
                            rowHeight = Math.max(rowHeight, d.height);
                        }
                        y += rowHeight + vgap;
                        return new Dimension(parentWidth, y);
                    }
                }
                return super.getPreferredSize();
            }
        };
        tilesPanel.setOpaque(false);
        List<ColorTile> tiles = new ArrayList<>();
        for (NamedColor nc : namedColors) {
            Color color = ColorUtil.webHexToColor(nc.hex());
            ColorTile tile = new ColorTile(nc, color);
            tile.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    updateUI(color);
                    fireColorChanged(color);
                }
            });
            tiles.add(tile);
            tilesPanel.add(tile);
        }

        JBScrollPane scrollPane = new JBScrollPane(tilesPanel);
        scrollPane.setPreferredSize(JBUI.size(0, 120));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        // Revalidate tiles when viewport resizes so FlowLayout wraps correctly
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tilesPanel.revalidate();
            }
        });
        panel.add(scrollPane, BorderLayout.CENTER);

        // Search filter
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            private void filter() {
                String query = searchField.getText().trim().toLowerCase();
                for (ColorTile tile : tiles) {
                    if (query.isEmpty()) {
                        tile.setVisible(true);
                    } else if (query.startsWith("#")) {
                        tile.setVisible(tile.namedColor.hex().toLowerCase().contains(query));
                    } else {
                        tile.setVisible(tile.namedColor.name().toLowerCase().contains(query));
                    }
                }
                tilesPanel.revalidate();
                tilesPanel.repaint();
            }
        });

        return panel;
    }

    /**
     * A small colored square representing a named color, with tooltip.
     */
    private static class ColorTile extends JPanel {
        final NamedColor namedColor;

        ColorTile(NamedColor nc, Color color) {
            this.namedColor = nc;
            setPreferredSize(JBUI.size(23, 23));
            setBackground(color);
            setBorder(JBUI.Borders.customLine(JBColor.border()));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(nc.name() + " (" + nc.hex() + ")");
        }
    }

    private void initializeEvents() {
        // Sat/Bright panel mouse events
        satBrightPanel.setPickerCallback((saturation, brightness) -> {
            double hue = parseDoubleOrZero(hue_textfield.getText());
            double alpha = getAlphaFieldValue();
            Color color = updateUI(hue, saturation, brightness, alpha);
            fireColorChanged(color);
        });

        // Hue slider events
        hueSliderPanel.setHueCallback(hue -> {
            if (updating) {
                return;
            }
            double saturation = parseDoubleOrZero(saturation_textfield.getText()) / 100.0;
            double brightness = parseDoubleOrZero(brightness_textfield.getText()) / 100.0;
            double alpha = getAlphaFieldValue();
            Color color = updateUI(hue, saturation, brightness, alpha);
            fireColorChanged(color);
        });

        // Alpha slider events
        alphaSliderPanel.setAlphaCallback(alpha -> {
            if (updating) {
                return;
            }
            double hue = parseDoubleOrZero(hue_textfield.getText());
            double saturation = parseDoubleOrZero(saturation_textfield.getText()) / 100.0;
            double brightness = parseDoubleOrZero(brightness_textfield.getText()) / 100.0;
            Color color = updateUI(hue, saturation, brightness, alpha);
            fireColorChanged(color);
        });

        // HSB text field focus lost + action
        FocusAdapter hsbFocusLost = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                Color color = updateUI_OnHSBChange();
                fireColorChanged(color);
            }
        };
        hue_textfield.addFocusListener(hsbFocusLost);
        saturation_textfield.addFocusListener(hsbFocusLost);
        brightness_textfield.addFocusListener(hsbFocusLost);
        alpha_textfield.addFocusListener(hsbFocusLost);

        hue_textfield.addActionListener(e -> {
            Color c = updateUI_OnHSBChange();
            fireColorChanged(c);
            hue_textfield.selectAll();
        });
        saturation_textfield.addActionListener(e -> {
            Color c = updateUI_OnHSBChange();
            fireColorChanged(c);
            saturation_textfield.selectAll();
        });
        brightness_textfield.addActionListener(e -> {
            Color c = updateUI_OnHSBChange();
            fireColorChanged(c);
            brightness_textfield.selectAll();
        });
        alpha_textfield.addActionListener(e -> {
            Color c = updateUI_OnHSBChange();
            fireColorChanged(c);
            alpha_textfield.selectAll();
        });

        // RGB text field focus lost + action
        FocusAdapter rgbFocusLost = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                Color color = updateUI_OnRGBChange();
                fireColorChanged(color);
            }
        };
        red_textfield.addFocusListener(rgbFocusLost);
        green_textfield.addFocusListener(rgbFocusLost);
        blue_textfield.addFocusListener(rgbFocusLost);

        red_textfield.addActionListener(e -> {
            Color c = updateUI_OnRGBChange();
            fireColorChanged(c);
            red_textfield.selectAll();
        });
        green_textfield.addActionListener(e -> {
            Color c = updateUI_OnRGBChange();
            fireColorChanged(c);
            green_textfield.selectAll();
        });
        blue_textfield.addActionListener(e -> {
            Color c = updateUI_OnRGBChange();
            fireColorChanged(c);
            blue_textfield.selectAll();
        });

        // Hex text field
        hexaTextfield.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    Color color = updateUI_OnHexaChange();
                    fireColorChanged(color);
                } catch (Exception ex) {
                    handleHexaException();
                }
            }
        });
        hexaTextfield.addActionListener(e -> {
            try {
                Color color = updateUI_OnHexaChange();
                fireColorChanged(color);
                hexaTextfield.selectAll();
            } catch (Exception ex) {
                handleHexaException();
            }
        });
    }

    private void fireColorChanged(Color color) {
        if (color != null && colorChangeCallback != null) {
            colorChangeCallback.onColorChanged(color);
        }
        pcs.firePropertyChange(PROP_COLOR, null, color);
    }

    public void addColorChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(PROP_COLOR, listener);
    }

    public void removeColorChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(PROP_COLOR, listener);
    }

    // ---- Public API ----

    /**
     * Returns the current color value from the HSB + alpha fields.
     */
    public Color getValue() {
        double hue = getHSBFieldValue(hue_textfield, 360);
        double saturation = getHSBFieldValue(saturation_textfield, 100) / 100.0;
        double brightness = getHSBFieldValue(brightness_textfield, 100) / 100.0;
        double alpha = getAlphaFieldValue();
        int rgb = Color.HSBtoRGB((float) (hue / 360.0), (float) saturation, (float) brightness);
        int alphaInt = (int) Math.round(alpha * 255);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alphaInt);
    }

    /**
     * Updates the UI to reflect the given color.
     */
    public void updateUI(final Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double hue = hsb[0] * 360;
        double saturation = hsb[1];
        double brightness = hsb[2];
        double alpha = color.getAlpha() / 255.0;
        updateUI(hue, saturation, brightness, alpha);
    }

    public String getRGBValue(boolean withAlpha) {
        int r = getRGBFieldValue(red_textfield);
        int g = getRGBFieldValue(green_textfield);
        int b = getRGBFieldValue(blue_textfield);
        String a = "";
        if (withAlpha) {
            a = "," + alpha_textfield.getText();
        }
        return r + "," + g + "," + b + a;
    }

    public String getHexValue(boolean withAlpha, boolean withSharp) {
        String text = hexaTextfield.getText().toUpperCase();
        if (!withSharp) {
            text = text.replace("#", "");
        }
        if (!withAlpha) {
            return text;
        }
        String alphaText = alpha_textfield.getText().isEmpty() ? "1" : alpha_textfield.getText();
        int alphaValue;
        try {
            alphaValue = (int) Math.round(Double.parseDouble(alphaText) * 255);
        } catch (NumberFormatException e) {
            alphaValue = 255;
        }
        String s = Integer.toHexString(alphaValue).toUpperCase();
        return text + (s.length() == 1 ? "0" + s : s);
    }

    public JTextField getHexaTextfield() {
        return hexaTextfield;
    }

    /**
     * Shows or hides the named colors panel.
     * Should be hidden when in gradient mode.
     */
    public void setNamedColorsPanelVisible(boolean visible) {
        if (namedColorsPanel != null) {
            namedColorsPanel.setVisible(visible);
        }
    }

    /**
     * Shows or hides the HSB/RGB text fields panel.
     */
    public void setHsbRgbPanelVisible(boolean visible) {
        if (hsbRgbPanel != null) {
            hsbRgbPanel.setVisible(visible);
        }
    }

    // ---- Private helpers ----

    private int getRGBFieldValue(DoubleField textField) {
        String text = textField.getText().trim();
        if (text.isEmpty()) {
            textField.setText("0");
            return 0;
        }
        int value;
        try {
            value = (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (value > 255) {
            textField.setText("255");
            return 255;
        }
        if (value < 0) {
            textField.setText("0");
            return 0;
        }
        return value;
    }

    private double getHSBFieldValue(DoubleField textField, double max) {
        String text = textField.getText().trim();
        if (text.isEmpty()) {
            textField.setText("0");
            return 0;
        }
        int value;
        try {
            value = (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (value > max) {
            textField.setText(String.valueOf((int) max));
            return max;
        }
        if (value < 0) {
            textField.setText("0");
            return 0;
        }
        return value;
    }

    private double getAlphaFieldValue() {
        String text = alpha_textfield.getText().trim();
        if (text.isEmpty()) {
            alpha_textfield.setText("0");
            return 0;
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (value > 1.0) {
            alpha_textfield.setText("1.0");
            return 1.0;
        }
        if (value < 0) {
            alpha_textfield.setText("0");
            return 0;
        }
        return value;
    }

    private Color updateUI_OnHSBChange() {
        double hue = parseDoubleOrZero(hue_textfield.getText());
        double saturation = parseDoubleOrZero(saturation_textfield.getText()) / 100.0;
        double brightness = parseDoubleOrZero(brightness_textfield.getText()) / 100.0;
        double alpha = parseDoubleOrZero(alpha_textfield.getText());
        return updateUI(hue, saturation, brightness, alpha);
    }

    private Color updateUI_OnRGBChange() {
        int red = getRGBFieldValue(red_textfield);
        int green = getRGBFieldValue(green_textfield);
        int blue = getRGBFieldValue(blue_textfield);
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        double hue = hsb[0] * 360;
        double saturation = hsb[1];
        double brightness = hsb[2];
        double alpha = getAlphaFieldValue();
        return updateUI(hue, saturation, brightness, alpha);
    }

    private Color updateUI_OnHexaChange() {
        String hexa = hexaTextfield.getText().trim();
        Color color = Color.decode(hexa);
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        double hue = hsb[0] * 360;
        double saturation = hsb[1];
        double brightness = hsb[2];
        double alpha = getAlphaFieldValue();
        return updateUI(hue, saturation, brightness, alpha);
    }

    private static final DecimalFormat ALPHA_FORMAT = new DecimalFormat("0.##");

    private Color updateUI(double hue, double saturation, double brightness, double alpha) {
        updating = true;

        // Clamp values
        hue = clamp(0, hue, 360);
        saturation = clamp(0, saturation, 1);
        brightness = clamp(0, brightness, 1);
        alpha = BigDecimal.valueOf(clamp(0, alpha, 1)).setScale(2, RoundingMode.HALF_UP).doubleValue();

        // Make AWT color from HSB
        int rgb = Color.HSBtoRGB((float) (hue / 360.0), (float) saturation, (float) brightness);
        int alphaInt = (int) Math.round(alpha * 255);
        Color color = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alphaInt);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        String hexa = String.format("#%02x%02x%02x", red, green, blue);

        // Set text field values
        hue_textfield.setText(String.valueOf((int) hue));
        saturation_textfield.setText(ALPHA_FORMAT.format(saturation * 100));
        brightness_textfield.setText(ALPHA_FORMAT.format(brightness * 100));

        double alpha_rounded = round(alpha, 100);
        alpha_textfield.setText(Double.toString(alpha_rounded));
        red_textfield.setText(Integer.toString(red));
        green_textfield.setText(Integer.toString(green));
        blue_textfield.setText(Integer.toString(blue));
        hexaTextfield.setText(hexa);

        // Update visual components
        satBrightPanel.setHue((float) hue);
        satBrightPanel.setIndicator(saturation, 1.0 - brightness);
        satBrightPanel.setIndicatorColor(new Color(red, green, blue));

        hueSliderPanel.setHue((float) hue);

        alphaSliderPanel.setBaseColor(new Color(red, green, blue));
        alphaSliderPanel.setAlpha((float) alpha);

        chipPanel.repaint();

        updating = false;
        return color;
    }

    private void handleHexaException() {
        updateUI(Color.BLACK);
        hexaTextfield.selectAll();
    }

    private static double clamp(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value, int roundingFactor) {
        return Math.round(value * roundingFactor) / (double) roundingFactor;
    }

    private static double parseDoubleOrZero(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void drawCheckerboard(Graphics g, int width, int height) {
        drawCheckerboard(g, 0, 0, width, height);
    }

    static void drawCheckerboard(Graphics g, int ox, int oy, int width, int height) {
        int cellSize = 4;
        for (int y = 0; y < height; y += cellSize) {
            for (int x = 0; x < width; x += cellSize) {
                boolean isWhite = ((x / cellSize) + (y / cellSize)) % 2 == 0;
                g.setColor(isWhite ? CHECKER_WHITE : CHECKER_GRAY);
                g.fillRect(ox + x, oy + y, Math.min(cellSize, width - x), Math.min(cellSize, height - y));
            }
        }
    }

    // =====================================================
    // Inner class: Saturation/Brightness picker panel
    // =====================================================
    private static class SatBrightPanel extends JPanel {
        private float hue = 0;
        private double indicatorX = 0; // 0..1
        private double indicatorY = 0; // 0..1
        private Color indicatorColor = Color.BLACK;
        private BufferedImage cachedImage;
        private float cachedHue = -1;
        private PickerCallback callback;

        @FunctionalInterface
        interface PickerCallback {
            void onPick(double saturation, double brightness);
        }

        SatBrightPanel() {
            setPreferredSize(JBUI.size(200, 100));
            setMinimumSize(JBUI.size(100, 60));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleMouse(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMouse(e);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setPickerCallback(PickerCallback cb) {
            this.callback = cb;
        }

        void setHue(float hue) {
            if (this.hue != hue) {
                this.hue = hue;
                cachedImage = null;
                repaint();
            }
        }

        void setIndicator(double x, double y) {
            this.indicatorX = x;
            this.indicatorY = y;
            repaint();
        }

        void setIndicatorColor(Color c) {
            this.indicatorColor = c;
        }

        private void handleMouse(MouseEvent e) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            double sat = clamp(0, (double) e.getX() / w, 1);
            double bri = 1.0 - clamp(0, (double) e.getY() / h, 1);
            if (callback != null) {
                callback.onPick(sat, bri);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            // Rebuild cached image if hue changed or size changed
            if (cachedImage == null || cachedImage.getWidth() != w || cachedImage.getHeight() != h || cachedHue != hue) {
                @SuppressWarnings("UndesirableClassUsage") // Pixel-level setRGB — must not use HiDPI scaling
                BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                cachedImage = tmp;
                for (int y = 0; y < h; y++) {
                    float brightness = 1.0f - (float) y / (h - 1);
                    for (int x = 0; x < w; x++) {
                        float saturation = (float) x / (w - 1);
                        int rgb = Color.HSBtoRGB(hue / 360f, saturation, brightness);
                        cachedImage.setRGB(x, y, rgb | 0xFF000000);
                    }
                }
                cachedHue = hue;
            }

            g.drawImage(cachedImage, 0, 0, null);

            // Draw indicator circle
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = indicatorX * w;
            double cy = indicatorY * h;
            double r = 7;

            g2.setColor(INDICATOR_OUTER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

            g2.setColor(INDICATOR_INNER);
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new Ellipse2D.Double(cx - r + 1.5, cy - r + 1.5, (r - 1.5) * 2, (r - 1.5) * 2));

            // Fill indicator with current color
            g2.setColor(indicatorColor);
            g2.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));

            g2.dispose();
        }
    }

    // =====================================================
    // Inner class: Hue slider panel (rainbow bar)
    // =====================================================
    private static class HueSliderPanel extends JPanel {
        private static final int PAD = 8; // left/right padding for thumb
        private float hue = 0;
        private HueCallback callback;
        private BufferedImage hueBarImage;

        @FunctionalInterface
        interface HueCallback {
            void onHueChanged(double hue);
        }

        HueSliderPanel() {
            setPreferredSize(JBUI.size(200, 20));
            setMinimumSize(JBUI.size(100, 20));
            setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(20)));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { handleMouse(e); }
                @Override
                public void mouseDragged(MouseEvent e) { handleMouse(e); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setHueCallback(HueCallback cb) { this.callback = cb; }
        void setHue(float hue) { this.hue = hue; repaint(); }

        private void handleMouse(MouseEvent e) {
            int trackW = getWidth() - PAD * 2;
            if (trackW <= 0) { return; }
            double h = clamp(0, (double) (e.getX() - PAD) / trackW, 1) * 360;
            if (callback != null) { callback.onHueChanged(h); }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) { return; }

            int trackW = w - PAD * 2;
            if (hueBarImage == null || hueBarImage.getWidth() != trackW) {
                @SuppressWarnings("UndesirableClassUsage") // Pixel-level setRGB — must not use HiDPI scaling
                BufferedImage tmp = new BufferedImage(Math.max(1, trackW), 1, BufferedImage.TYPE_INT_RGB);
                hueBarImage = tmp;
                for (int x = 0; x < trackW; x++) {
                    float hueVal = (float) x / Math.max(1, trackW - 1);
                    hueBarImage.setRGB(x, 0, Color.HSBtoRGB(hueVal, 1.0f, 1.0f));
                }
            }
            g.drawImage(hueBarImage, PAD, 0, trackW, h, null);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ix = PAD + (int) (hue / 360.0 * trackW);
            double cy = h / 2.0;
            double r = Math.min(h / 2.0 - 1, 7);
            g2.setColor(INDICATOR_INNER);
            g2.fill(new Ellipse2D.Double(ix - r, cy - r, r * 2, r * 2));
            g2.setColor(INDICATOR_OUTER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(ix - r, cy - r, r * 2, r * 2));
            g2.dispose();
        }
    }

    // =====================================================
    // Inner class: Alpha slider panel
    // =====================================================
    private static class AlphaSliderPanel extends JPanel {
        private static final int PAD = 8;
        private float alpha = 1.0f;
        private Color baseColor = Color.BLACK;
        private AlphaCallback callback;

        @FunctionalInterface
        interface AlphaCallback {
            void onAlphaChanged(double alpha);
        }

        AlphaSliderPanel() {
            setPreferredSize(JBUI.size(200, 20));
            setMinimumSize(JBUI.size(100, 20));
            setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(20)));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { handleMouse(e); }
                @Override
                public void mouseDragged(MouseEvent e) { handleMouse(e); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setAlphaCallback(AlphaCallback cb) { this.callback = cb; }
        void setAlpha(float alpha) { this.alpha = alpha; repaint(); }

        void setBaseColor(Color c) {
            this.baseColor = new Color(c.getRed(), c.getGreen(), c.getBlue());
            repaint();
        }

        private void handleMouse(MouseEvent e) {
            int trackW = getWidth() - PAD * 2;
            if (trackW <= 0) { return; }
            double a = clamp(0, (double) (e.getX() - PAD) / trackW, 1);
            if (callback != null) { callback.onAlphaChanged(a); }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) { return; }

            int trackW = w - PAD * 2;
            drawCheckerboard(g, PAD, 0, trackW, h);

            Graphics2D g2 = (Graphics2D) g.create();
            GradientPaint gp = new GradientPaint(PAD, 0,
                    new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0),
                    PAD + trackW, 0, baseColor);
            g2.setPaint(gp);
            g2.fillRect(PAD, 0, trackW, h);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ix = PAD + (int) (alpha * trackW);
            double cy = h / 2.0;
            double r = Math.min(h / 2.0 - 1, 7);
            g2.setColor(INDICATOR_INNER);
            g2.fill(new Ellipse2D.Double(ix - r, cy - r, r * 2, r * 2));
            g2.setColor(INDICATOR_OUTER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(ix - r, cy - r, r * 2, r * 2));
            g2.dispose();
        }
    }
}
