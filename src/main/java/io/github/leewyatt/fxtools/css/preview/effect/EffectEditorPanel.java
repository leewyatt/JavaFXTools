package io.github.leewyatt.fxtools.css.preview.effect;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.paintpicker.ColorPicker;
import io.github.leewyatt.fxtools.paintpicker.DoubleField;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import com.intellij.openapi.ui.ComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * Effect editor panel for editing DropShadow and InnerShadow parameters.
 * <p>
 * Layout (top to bottom):
 * <ol>
 *   <li>Tab control (DropShadow / InnerShadow)</li>
 *   <li>Preview area</li>
 *   <li>Color picker (SatBright + Hue + Alpha + Hex — no Named Colors, no HSB/RGB fields)</li>
 *   <li>Parameter sliders (BlurType, Width, Height, Radius, Spread/Choke, OffsetX, OffsetY)</li>
 *   <li>Bottom toolbar (format combo + copy button)</li>
 * </ol>
 */
public class EffectEditorPanel extends JPanel {

    public static final String PROP_CONFIG = "effectConfig";

    private static final int LABEL_WIDTH = 60;
    private static final int FIELD_WIDTH = 48;
    private static final int SLIDER_MULTIPLIER = 100;
    private static final int OFFSET_SLIDER_MIN = -50;
    private static final int OFFSET_SLIDER_MAX = 50;
    private static final String FORMAT_CSS = "CSS code";
    private static final String FORMAT_JAVA = "Java code";

    // ==================== UI Components ====================

    private final TabControl tabControl;
    private final PreviewPanel previewPanel;
    private final ColorPicker colorPicker;
    private ComboBox<String> blurTypeCombo;
    private JSlider widthSlider;
    private DoubleField widthField;
    private JPanel widthRow;
    private JSlider heightSlider;
    private DoubleField heightField;
    private JPanel heightRow;
    private JSlider radiusSlider;
    private DoubleField radiusField;
    private JSlider spreadSlider;
    private DoubleField spreadField;
    private JBLabel spreadLabel;
    private JSlider offsetXSlider;
    private DoubleField offsetXField;
    private JSlider offsetYSlider;
    private DoubleField offsetYField;
    private final ComboBox<String> formatCombo;
    private final JButton copyButton;
    private final JPanel toolbarSeparator;
    private final JPanel toolbar;

    // ==================== State ====================

    private final EffectConfig config = new EffectConfig();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean updating;
    private final List<ChangeListener> changeListeners = new ArrayList<>();

    // ==================== Constructor ====================

    public EffectEditorPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());
        setBorder(JBUI.Borders.empty(6));

        // ---- 1. Tab control ----
        tabControl = new TabControl("DropShadow", "InnerShadow");
        tabControl.setAlignmentX(LEFT_ALIGNMENT);
        add(tabControl);
        add(Box.createVerticalStrut(JBUI.scale(6)));

        // ---- 2. Preview area ----
        previewPanel = new PreviewPanel();
        previewPanel.setAlignmentX(LEFT_ALIGNMENT);
        add(previewPanel);
        add(createSeparator());
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- 3. Embedded color picker (compact) ----
        colorPicker = new ColorPicker();
        colorPicker.setNamedColorsPanelVisible(false);
        colorPicker.setHsbRgbPanelVisible(false);
        colorPicker.setAlignmentX(LEFT_ALIGNMENT);
        add(colorPicker);
        add(Box.createVerticalStrut(JBUI.scale(4)));
        add(createSeparator());
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- 4. Parameter sliders ----
        JPanel paramsPanel = buildParamsPanel();
        add(paramsPanel);
        add(Box.createVerticalStrut(JBUI.scale(4)));

        // ---- 5. Bottom toolbar (hidden by default, for ToolWindow use) ----
        toolbarSeparator = createSeparator();
        add(toolbarSeparator);
        add(Box.createVerticalStrut(JBUI.scale(4)));
        formatCombo = new ComboBox<>(new String[]{FORMAT_CSS, FORMAT_JAVA});
        copyButton = new JButton(AllIcons.Actions.Copy);
        toolbar = createToolbar();
        add(toolbar);
        toolbarSeparator.setVisible(false);
        toolbar.setVisible(false);

        // Initial state
        widthRow.setVisible(false);
        heightRow.setVisible(false);

        setupListeners();
        updatePreview();
    }

    // ==================== Build Params Panel ====================

    private JPanel buildParamsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);

        blurTypeCombo = new ComboBox<>(EffectConfig.BLUR_TYPES);
        blurTypeCombo.setSelectedItem(EffectConfig.BLUR_THREE_PASS_BOX);
        p.add(createComboRow("Blur Type", blurTypeCombo));
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        widthSlider = createSlider(0, 255, 21);
        widthField = createDoubleField("21.0");
        widthRow = createSliderRow("Width", widthSlider, widthField);
        p.add(widthRow);
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        heightSlider = createSlider(0, 255, 21);
        heightField = createDoubleField("21.0");
        heightRow = createSliderRow("Height", heightSlider, heightField);
        p.add(heightRow);
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        radiusSlider = createSlider(0, 127, 10);
        radiusField = createDoubleField("10.0");
        p.add(createSliderRow("Radius", radiusSlider, radiusField));
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        spreadSlider = createSlider(0, SLIDER_MULTIPLIER, 0);
        spreadField = createDoubleField("0.0");
        spreadLabel = new JBLabel("Spread");
        p.add(createSliderRow(spreadLabel, spreadSlider, spreadField));
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        offsetXSlider = createSlider(OFFSET_SLIDER_MIN, OFFSET_SLIDER_MAX, 0);
        offsetXField = createDoubleField("0.0");
        p.add(createSliderRow("Offset X", offsetXSlider, offsetXField));
        p.add(Box.createVerticalStrut(JBUI.scale(4)));

        offsetYSlider = createSlider(OFFSET_SLIDER_MIN, OFFSET_SLIDER_MAX, 0);
        offsetYField = createDoubleField("0.0");
        p.add(createSliderRow("Offset Y", offsetYSlider, offsetYField));

        return p;
    }

    // ==================== Public API ====================

    public EffectConfig getConfig() {
        return new EffectConfig(config);
    }

    public void setConfig(EffectConfig c) {
        updating = true;
        try {
            config.setEffectType(c.getEffectType());
            config.setBlurType(c.getBlurType());
            config.setColor(c.getColor());
            config.setWidth(c.getWidth());
            config.setHeight(c.getHeight());
            config.setRadius(c.getRadius());
            config.setSpreadOrChoke(c.getSpreadOrChoke());
            config.setOffsetX(c.getOffsetX());
            config.setOffsetY(c.getOffsetY());

            tabControl.setSelectedIndex(config.getEffectType() == EffectType.DROPSHADOW ? 0 : 1);
            blurTypeCombo.setSelectedItem(config.getBlurType());
            colorPicker.updateUI(config.getColor());
            setSliderAndField(widthSlider, widthField, config.getWidth(), 1);
            setSliderAndField(heightSlider, heightField, config.getHeight(), 1);
            setSliderAndField(radiusSlider, radiusField, config.getRadius(), 1);
            setSliderAndField(spreadSlider, spreadField, config.getSpreadOrChoke(), SLIDER_MULTIPLIER);
            setSliderAndField(offsetXSlider, offsetXField, config.getOffsetX(), 1);
            setSliderAndField(offsetYSlider, offsetYField, config.getOffsetY(), 1);
            updateSpreadLabel();
        } finally {
            updating = false;
        }
        updatePreview();
    }

    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    public void addConfigChangeListener(java.beans.PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(PROP_CONFIG, listener);
    }

    /**
     * Shows or hides the bottom toolbar (format combo + copy button).
     * Hidden by default for gutter popup use; shown for ToolWindow use.
     */
    public void setToolbarVisible(boolean visible) {
        toolbarSeparator.setVisible(visible);
        toolbar.setVisible(visible);
    }

    /**
     * Sets the format mode for the toolbar combo box.
     */
    public void setFormatMode(boolean cssMode) {
        formatCombo.setSelectedItem(cssMode ? FORMAT_CSS : FORMAT_JAVA);
    }

    // ==================== Listener Setup ====================

    private void setupListeners() {
        // Color picker → update config
        colorPicker.addColorChangeListener(evt -> {
            if (updating) {
                return;
            }
            Color newColor = (Color) evt.getNewValue();
            if (newColor != null) {
                config.setColor(newColor);
                fireConfigChanged();
                updatePreview();
            }
        });

        tabControl.setOnSelectionChanged(() -> {
            if (updating) {
                return;
            }
            config.setEffectType(tabControl.getSelectedIndex() == 0 ? EffectType.DROPSHADOW : EffectType.INNERSHADOW);
            updateSpreadLabel();
            fireConfigChanged();
            updatePreview();
        });

        blurTypeCombo.addActionListener(e -> {
            if (updating) {
                return;
            }
            config.setBlurType((String) blurTypeCombo.getSelectedItem());
            fireConfigChanged();
            updatePreview();
        });

        linkSliderAndField(widthSlider, widthField, 1, v -> {
            config.setWidth(v);
            double r = clampRadius((config.getWidth() + config.getHeight() - 2) / 4.0);
            config.setRadius(r);
            updating = true;
            setSliderAndField(radiusSlider, radiusField, r, 1);
            updating = false;
        });

        linkSliderAndField(heightSlider, heightField, 1, v -> {
            config.setHeight(v);
            double r = clampRadius((config.getWidth() + config.getHeight() - 2) / 4.0);
            config.setRadius(r);
            updating = true;
            setSliderAndField(radiusSlider, radiusField, r, 1);
            updating = false;
        });

        linkSliderAndField(radiusSlider, radiusField, 1, v -> {
            config.setRadius(v);
            double wh = Math.min(255, Math.max(0, v * 2 + 1));
            config.setWidth(wh);
            config.setHeight(wh);
            updating = true;
            setSliderAndField(widthSlider, widthField, wh, 1);
            setSliderAndField(heightSlider, heightField, wh, 1);
            updating = false;
        });

        linkSliderAndField(spreadSlider, spreadField, SLIDER_MULTIPLIER, v -> config.setSpreadOrChoke(v));
        linkSliderAndField(offsetXSlider, offsetXField, 1, v -> config.setOffsetX(v));
        linkSliderAndField(offsetYSlider, offsetYField, 1, v -> config.setOffsetY(v));

        formatCombo.addActionListener(e -> {
            boolean java = FORMAT_JAVA.equals(formatCombo.getSelectedItem());
            widthRow.setVisible(java);
            heightRow.setVisible(java);
            revalidate();
            repaint();
        });

        copyButton.addActionListener(e -> {
            String text = FORMAT_JAVA.equals(formatCombo.getSelectedItem())
                    ? config.toJavaCode() : config.toCssText();
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            Icon orig = copyButton.getIcon();
            copyButton.setIcon(AllIcons.Actions.Checked);
            Timer t = new Timer(1000, ev -> copyButton.setIcon(orig));
            t.setRepeats(false);
            t.start();
        });
    }

    // ==================== Slider / Field Linkage ====================

    private interface ValueConsumer {
        void accept(double value);
    }

    private void linkSliderAndField(JSlider slider, DoubleField field, int mul, ValueConsumer consumer) {
        slider.addChangeListener(e -> {
            if (updating) {
                return;
            }
            double v = slider.getValue() / (double) mul;
            updating = true;
            field.setText(EffectConfig.formatNumber(v));
            updating = false;
            consumer.accept(v);
            fireConfigChanged();
            updatePreview();
        });

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { sync(); }
            @Override public void removeUpdate(DocumentEvent e) { sync(); }
            @Override public void changedUpdate(DocumentEvent e) { sync(); }
            private void sync() {
                if (updating) {
                    return;
                }
                try {
                    double v = Double.parseDouble(field.getText());
                    int sv = Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), (int) Math.round(v * mul)));
                    updating = true;
                    slider.setValue(sv);
                    updating = false;
                    consumer.accept(v);
                    fireConfigChanged();
                    updatePreview();
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    private void setSliderAndField(JSlider slider, DoubleField field, double value, int mul) {
        slider.setValue(Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), (int) Math.round(value * mul))));
        field.setText(EffectConfig.formatNumber(value));
    }

    // ==================== UI Builders ====================

    private JPanel createComboRow(String text, ComboBox<?> combo) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(28)));
        JBLabel lbl = new JBLabel(text);
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);
        lbl.setPreferredSize(new Dimension(JBUI.scale(LABEL_WIDTH), 0));
        row.add(lbl, BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        return row;
    }

    private JPanel createSliderRow(String text, JSlider slider, DoubleField field) {
        return createSliderRow(new JBLabel(text), slider, field);
    }

    private JPanel createSliderRow(JBLabel label, JSlider slider, DoubleField field) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(28)));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setPreferredSize(new Dimension(JBUI.scale(LABEL_WIDTH), 0));
        row.add(label, BorderLayout.WEST);
        field.setPreferredSize(new Dimension(JBUI.scale(FIELD_WIDTH), 0));
        row.add(slider, BorderLayout.CENTER);
        row.add(field, BorderLayout.EAST);
        return row;
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        bar.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(28)));
        bar.add(formatCombo, BorderLayout.WEST);
        copyButton.setPreferredSize(new Dimension(JBUI.scale(28), JBUI.scale(28)));
        copyButton.setToolTipText("Copy to clipboard");
        bar.add(copyButton, BorderLayout.EAST);
        return bar;
    }

    private JPanel createSeparator() {
        JPanel sep = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(JBColor.border());
                g.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
            }
        };
        sep.setOpaque(false);
        sep.setPreferredSize(new Dimension(0, JBUI.scale(1)));
        sep.setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(1)));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    private static JSlider createSlider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setOpaque(false);
        return s;
    }

    private static DoubleField createDoubleField(String init) {
        DoubleField f = new DoubleField(5);
        f.setText(init);
        return f;
    }

    // ==================== Helpers ====================

    private void updateSpreadLabel() {
        spreadLabel.setText(config.getEffectType() == EffectType.DROPSHADOW ? "Spread" : "Choke");
    }

    private void fireConfigChanged() {
        pcs.firePropertyChange(PROP_CONFIG, null, new EffectConfig(config));
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : changeListeners) {
            l.stateChanged(ev);
        }
    }

    private void updatePreview() {
        previewPanel.repaint();
    }

    private static double clampRadius(double r) {
        return Math.max(0, Math.min(127, r));
    }

    // ==================== Preview Panel ====================

    private class PreviewPanel extends JPanel {

        private static final Color LIGHT_BG = new Color(0xFFFFFF);
        private static final Color DARK_BG = new Color(0x2B2D30);
        private static final int TOGGLE_SIZE = 20;
        private static final int TOGGLE_MARGIN = 4;

        private boolean darkPreview;
        private boolean hoverToggle;

        PreviewPanel() {
            setOpaque(true);
            setPreferredSize(new Dimension(JBUI.scale(340), JBUI.scale(160)));
            setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(160)));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isInToggleArea(e.getPoint())) {
                        darkPreview = !darkPreview;
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean over = isInToggleArea(e.getPoint());
                    if (over != hoverToggle) {
                        hoverToggle = over;
                        setCursor(over
                                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                : Cursor.getDefaultCursor());
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverToggle) {
                        hoverToggle = false;
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private Rectangle getToggleRect() {
            int s = JBUI.scale(TOGGLE_SIZE);
            int m = JBUI.scale(TOGGLE_MARGIN);
            return new Rectangle(getWidth() - s - m, m, s, s);
        }

        private boolean isInToggleArea(Point p) {
            return getToggleRect().contains(p);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // Background
            g2.setColor(darkPreview ? DARK_BG : LIGHT_BG);
            g2.fillRect(0, 0, w, h);

            // Shadow image
            @SuppressWarnings("UndesirableClassUsage")
            BufferedImage img = config.getEffectType() == EffectType.DROPSHADOW
                    ? ShadowRenderer.renderDropShadow(config, w, h)
                    : ShadowRenderer.renderInnerShadow(config, w, h);
            g2.drawImage(img, 0, 0, null);

            // Toggle button (top-right corner)
            Rectangle tr = getToggleRect();
            g2.setColor(new Color(128, 128, 128, hoverToggle ? 70 : 35));
            g2.fillRoundRect(tr.x, tr.y, tr.width, tr.height,
                    JBUI.scale(4), JBUI.scale(4));
            int iconSize = JBUI.scale(14);
            int ix = tr.x + (tr.width - iconSize) / 2;
            int iy = tr.y + (tr.height - iconSize) / 2;
            Color iconColor = darkPreview ? new Color(0xBBBBBB) : new Color(0x777777);
            if (darkPreview) {
                drawSunIcon(g2, ix, iy, iconSize, iconColor);
            } else {
                drawMoonIcon(g2, ix, iy, iconSize, iconColor);
            }

            g2.dispose();
        }

        /**
         * Draws a sun icon: filled circle + 8 radiating rays.
         * Shown on dark preview — clicking switches to light.
         */
        private static void drawSunIcon(Graphics2D g2, int x, int y, int size, Color color) {
            g2.setColor(color);
            int cx = x + size / 2;
            int cy = y + size / 2;
            int r = size / 4;
            int rayStart = r + 2;
            int rayEnd = size / 2 - 1;

            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int x1 = cx + (int) (rayStart * Math.cos(angle));
                int y1 = cy - (int) (rayStart * Math.sin(angle));
                int x2 = cx + (int) (rayEnd * Math.cos(angle));
                int y2 = cy - (int) (rayEnd * Math.sin(angle));
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        /**
         * Draws a crescent moon icon via Area subtraction.
         * Shown on light preview — clicking switches to dark.
         */
        private static void drawMoonIcon(Graphics2D g2, int x, int y, int size, Color color) {
            g2.setColor(color);
            int r = size * 2 / 5;
            int cx = x + size / 2;
            int cy = y + size / 2;

            Area moon = new Area(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
            int cutOff = r * 3 / 5;
            moon.subtract(new Area(new Ellipse2D.Float(
                    cx - r + cutOff, cy - r - cutOff / 3, r * 2, r * 2)));
            g2.fill(moon);
        }
    }

    // ==================== Tab Control ====================

    private static class TabControl extends JPanel {
        private int selectedIndex;
        private final String[] labels;
        private Runnable onSelectionChanged;

        TabControl(String... labels) {
            this.labels = labels;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(-1, JBUI.scale(30)));
            setMaximumSize(new Dimension(Short.MAX_VALUE, JBUI.scale(30)));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    int segW = getWidth() / labels.length;
                    int idx = Math.min(e.getX() / segW, labels.length - 1);
                    if (idx != selectedIndex) {
                        selectedIndex = idx;
                        repaint();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.run();
                        }
                    }
                }
            });
        }

        void setOnSelectionChanged(Runnable r) { this.onSelectionChanged = r; }
        int getSelectedIndex() { return selectedIndex; }
        void setSelectedIndex(int i) {
            if (i >= 0 && i < labels.length && i != selectedIndex) {
                selectedIndex = i;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int outerR = JBUI.scale(6), innerR = JBUI.scale(4), pad = JBUI.scale(3);
            int n = labels.length, segW = w / n;

            g2.setColor(UIUtil.getTextFieldBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, outerR, outerR));
            g2.setColor(JBColor.border());
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, outerR, outerR));

            Font normal = getFont().deriveFont((float) JBUI.scale(12));
            Font bold = normal.deriveFont(Font.BOLD);
            for (int i = 0; i < n; i++) {
                int x0 = i * segW + pad;
                int sw = (i == n - 1) ? w - x0 - pad : segW - pad * 2;
                FontMetrics fm;
                if (i == selectedIndex) {
                    g2.setColor(UIUtil.getListSelectionBackground(true));
                    g2.fill(new RoundRectangle2D.Float(x0, pad, sw, h - pad * 2, innerR, innerR));
                    g2.setFont(bold);
                    g2.setColor(UIUtil.getListSelectionForeground(true));
                    fm = g2.getFontMetrics(bold);
                } else {
                    g2.setFont(normal);
                    g2.setColor(UIUtil.getLabelDisabledForeground());
                    fm = g2.getFontMetrics(normal);
                }
                int tw = fm.stringWidth(labels[i]);
                g2.drawString(labels[i], x0 + (sw - tw) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }
}
