package io.github.leewyatt.fxtools.toolwindow.svgtool;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.OnOffButton;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import io.github.leewyatt.fxtools.util.SvgPathExtractor;
import io.github.leewyatt.fxtools.util.SvgPathExtractor.ShapeType;
import io.github.leewyatt.fxtools.util.SvgPathExtractor.SvgAnalysis;
import io.github.leewyatt.fxtools.util.SvgPathTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * ToolWindow panel for extracting and simplifying SVG path data.
 */
public class SvgPathToolPanel extends JPanel {

    // ==================== Sizing ====================
    private static final int PREVIEW_SIZE = 100;
    private static final int PREVIEW_PADDING = 12;
    private static final int CARD_ARC = 8;
    private static final int CHECKER_SIZE = 6;
    private static final int SETTING_ROW_HEIGHT = 36;

    // ==================== Colors ====================
    private static final JBColor CARD_BG =
            new JBColor(new Color(0xF5F5F5), new Color(0x2D2F33));
    private static final JBColor CARD_BORDER =
            new JBColor(new Color(0xDCDCDC), new Color(0x43454A));
    private static final JBColor ICON_COLOR =
            new JBColor(new Color(0x3C3C3C), new Color(0xBBBBBB));
    private static final JBColor HINT_FG =
            new JBColor(new Color(0x888888), new Color(0x808080));
    private static final JBColor CHECKER_A =
            new JBColor(new Color(0xFFFFFF), new Color(0x3C3F41));
    private static final JBColor CHECKER_B =
            new JBColor(new Color(0xE8E8E8), new Color(0x333538));
    private static final JBColor BADGE_PATH_BG =
            new JBColor(new Color(0x3574F0), new Color(0x3574F0));
    private static final JBColor BADGE_SHAPE_BG =
            new JBColor(new Color(0xE8A317), new Color(0xC89616));
    private static final JBColor BADGE_FG =
            new JBColor(Color.WHITE, Color.WHITE);
    private static final JBColor DROP_ZONE_BG =
            new JBColor(Color.WHITE, new Color(0x2B2D30));
    private static final JBColor DROP_BORDER =
            new JBColor(new Color(0xC0C0C0), new Color(0x505357));

    // ==================== Options ====================
    private static final int[] SIZE_OPTIONS = {16, 24, 32, 48, 64, 128, 256, 512, 1024};
    private static final int DEFAULT_SIZE_INDEX = 1;
    private static final int[] PRECISION_OPTIONS = {0, 1, 2, 3, 4};
    private static final int DEFAULT_PRECISION_INDEX = 4; // precision = 4

    // ==================== Components ====================
    private final Project project;
    private final OriginalPreview originalPreview;
    private final PathPreview extractedPreview;
    private final OnOffButton convertToggle;
    private final ComboBox<String> sizeCombo;
    private final ComboBox<String> precisionCombo;
    private final JPanel statusBar;
    private final JBTextArea outputArea;
    private final JButton copyButton;

    // Sections shown only when file is loaded
    private final JComponent previewCard;
    private final JComponent settingsCard;
    private final JComponent statusSection;
    private final JComponent outputSection;

    // ==================== State ====================
    @Nullable
    private String currentSvgContent;
    @Nullable
    private SvgAnalysis currentAnalysis;

    public SvgPathToolPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(JBUI.Borders.empty(12));
        main.setOpaque(false);

        // ==================== 0. Title ====================
        JBLabel titleLabel = new JBLabel(FxToolsBundle.message("svg.tool.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, (float) JBUI.scale(16)));
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        main.add(titleLabel);
        main.add(Box.createVerticalStrut(JBUI.scale(10)));

        // ==================== 1. Drop Zone ====================
        JPanel dropZone = createDropZone();
        main.add(dropZone);
        main.add(Box.createVerticalStrut(JBUI.scale(8)));

        // ==================== 2. Preview Card ====================
        JPanel previewPanel = new JPanel(new GridBagLayout());
        previewPanel.setOpaque(false);

        originalPreview = new OriginalPreview();
        extractedPreview = new PathPreview();

        GridBagConstraints gbc = new GridBagConstraints();

        // Original
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        previewPanel.add(createLabeledPreview(FxToolsBundle.message("svg.tool.original"), originalPreview), gbc);

        // Arrow
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.insets = JBUI.insets(0, 8, 0, 8);
        JBLabel arrow = new JBLabel("\u2192");
        arrow.setForeground(HINT_FG);
        arrow.setFont(arrow.getFont().deriveFont((float) JBUI.scale(16)));
        previewPanel.add(arrow, gbc);

        // Extracted
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.emptyInsets();
        previewPanel.add(createLabeledPreview(FxToolsBundle.message("svg.tool.extracted"), extractedPreview), gbc);

        previewCard = wrapInCard(previewPanel, JBUI.insets(10, 12));
        main.add(previewCard);
        main.add(Box.createVerticalStrut(JBUI.scale(8)));

        // ==================== 3. Settings Card ====================
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(4, 8);

        // Row 0: Convert shapes
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel convertLabel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        convertLabel.setOpaque(false);
        JBLabel convertTitle = new JBLabel(FxToolsBundle.message("svg.tool.convert.shapes"));
        convertTitle.setFont(convertTitle.getFont().deriveFont(Font.BOLD));
        convertLabel.add(convertTitle);
        JBLabel convertDesc = new JBLabel(FxToolsBundle.message("svg.tool.convert.shapes.desc"));
        convertDesc.setForeground(HINT_FG);
        convertLabel.add(convertDesc);
        settings.add(convertLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        convertToggle = new OnOffButton();
        convertToggle.setSelected(true);
        Dimension toggleSize = new Dimension(JBUI.scale(60), JBUI.scale(35));
        convertToggle.setPreferredSize(toggleSize);
        convertToggle.setMinimumSize(toggleSize);
        convertToggle.setMaximumSize(toggleSize);
        convertToggle.addActionListener(e -> updateResult());
        settings.add(convertToggle, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Separator
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(0, 4);
        settings.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = JBUI.insets(4, 8);

        // Row 1: Normalize
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JBLabel normLabel = new JBLabel(FxToolsBundle.message("svg.tool.normalize"));
        normLabel.setFont(normLabel.getFont().deriveFont(Font.BOLD));
        settings.add(normLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0));
        sizePanel.setOpaque(false);
        sizePanel.add(new JBLabel(FxToolsBundle.message("svg.tool.size")));
        String[] sizeLabels = new String[SIZE_OPTIONS.length];
        for (int i = 0; i < SIZE_OPTIONS.length; i++) {
            sizeLabels[i] = String.valueOf(SIZE_OPTIONS[i]);
        }
        sizeCombo = new ComboBox<>(sizeLabels);
        sizeCombo.setEditable(true);
        sizeCombo.setSelectedIndex(DEFAULT_SIZE_INDEX);
        sizeCombo.addActionListener(e -> updateResult());
        sizePanel.add(sizeCombo);
        settings.add(sizePanel, gbc);

        // Separator
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(0, 4);
        settings.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = JBUI.insets(4, 8);

        // Row 2: Precision
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        JBLabel precLabel = new JBLabel(FxToolsBundle.message("svg.tool.precision"));
        precLabel.setFont(precLabel.getFont().deriveFont(Font.BOLD));
        settings.add(precLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        String[] precLabels = new String[PRECISION_OPTIONS.length];
        for (int i = 0; i < PRECISION_OPTIONS.length; i++) {
            precLabels[i] = String.valueOf(PRECISION_OPTIONS[i]);
        }
        precisionCombo = new ComboBox<>(precLabels);
        precisionCombo.setSelectedIndex(DEFAULT_PRECISION_INDEX);
        precisionCombo.addActionListener(e -> updateResult());
        settings.add(precisionCombo, gbc);

        settingsCard = wrapInCard(settings, JBUI.insets(4, 0));
        main.add(settingsCard);
        main.add(Box.createVerticalStrut(JBUI.scale(8)));

        // ==================== 4. Status Bar ====================
        statusBar = new JPanel(new GridBagLayout());
        statusBar.setOpaque(false);
        statusBar.setAlignmentX(LEFT_ALIGNMENT);

        copyButton = new JButton(FxToolsBundle.message("svg.tool.copy"));
        copyButton.addActionListener(e -> copyToClipboard());

        statusSection = statusBar;
        main.add(statusBar);
        main.add(Box.createVerticalStrut(JBUI.scale(6)));

        // ==================== 5. Output Area ====================
        outputArea = new JBTextArea(6, 20);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(11)));
        outputArea.setBorder(JBUI.Borders.empty(8));

        JBScrollPane outputScroll = new JBScrollPane(outputArea);
        outputScroll.setAlignmentX(LEFT_ALIGNMENT);
        int outputH = JBUI.scale(150);
        outputScroll.setPreferredSize(new Dimension(100, outputH));
        outputScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, outputH));

        outputSection = outputScroll;
        main.add(outputScroll);

        // ==================== Scroll Wrapper ====================
        JPanel scrollContent = new JPanel(new BorderLayout());
        scrollContent.setOpaque(false);
        scrollContent.add(main, BorderLayout.NORTH);

        JBScrollPane scroll = new JBScrollPane(scrollContent);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
        add(scroll, BorderLayout.CENTER);

        setFileLoaded(false);
    }

    // ==================== Drop Zone ====================

    @NotNull
    private JPanel createDropZone() {
        JPanel zone = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = JBUI.scale(CARD_ARC);
                g2.setColor(DROP_ZONE_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(DROP_BORDER);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10f, new float[]{5f, 4f}, 0f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
                g2.dispose();
            }
        };
        zone.setOpaque(false);
        zone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        zone.setAlignmentX(LEFT_ALIGNMENT);
        int h = JBUI.scale(66);
        zone.setPreferredSize(new Dimension(100, h));
        zone.setMaximumSize(new Dimension(Short.MAX_VALUE, h));
        setupDropTarget(zone);
        zone.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openSvgFile();
            }
        });

        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0));
        hintRow.setOpaque(false);

        int iconSize = JBUI.scale(32);
        Icon sourceIcon = AllIcons.FileTypes.Image;
        Icon scaledIcon = new Icon() {
            @Override
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                double s = (double) iconSize / sourceIcon.getIconWidth();
                g2.translate(x, y);
                g2.scale(s, s);
                sourceIcon.paintIcon(c, g2, 0, 0);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return iconSize;
            }

            @Override
            public int getIconHeight() {
                return iconSize;
            }
        };
        hintRow.add(new JBLabel(scaledIcon));

        JPanel textPanel = new JPanel();
        BoxLayout boxLayout = new BoxLayout(textPanel, BoxLayout.Y_AXIS);
        textPanel.setLayout(boxLayout);
        textPanel.setOpaque(false);

        JBLabel line1 = new JBLabel(FxToolsBundle.message("svg.tool.drop.line1"), SwingConstants.CENTER);
        line1.setForeground(HINT_FG);
        line1.setFont(line1.getFont().deriveFont(Font.BOLD));
        line1.setAlignmentX(CENTER_ALIGNMENT);
        textPanel.add(line1);

        textPanel.add(Box.createVerticalStrut(JBUI.scale(4)));

        JBLabel line2 = new JBLabel(FxToolsBundle.message("svg.tool.drop.line2"), SwingConstants.CENTER);
        line2.setForeground(HINT_FG);
        line2.setFont(line2.getFont().deriveFont(Font.BOLD));
        line2.setAlignmentX(CENTER_ALIGNMENT);
        textPanel.add(line2);

        hintRow.add(textPanel);
        zone.add(hintRow);
        return zone;
    }

    // ==================== Labeled Preview ====================

    @NotNull
    private static JPanel createLabeledPreview(@NotNull String title, @NotNull JPanel preview) {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        panel.setOpaque(false);
        JBLabel label = new JBLabel(title, SwingConstants.CENTER);
        label.setForeground(HINT_FG);
        label.setFont(label.getFont().deriveFont((float) JBUI.scale(10)));
        panel.add(label, BorderLayout.NORTH);
        panel.add(preview, BorderLayout.CENTER);
        return panel;
    }

    // ==================== Card Wrapper ====================

    @NotNull
    private static JPanel wrapInCard(@NotNull JComponent content, @NotNull Insets padding) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = JBUI.scale(CARD_ARC);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(padding.top, padding.left, padding.bottom, padding.right));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // ==================== Visibility ====================

    @SuppressWarnings("unused")
    private void setFileLoaded(boolean loaded) {
        // All sections always visible
    }

    // ==================== File Opening ====================

    private void openSvgFile() {
        var descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("svg");
        descriptor.setTitle(FxToolsBundle.message("svg.tool.chooser.title"));
        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file == null) {
            return;
        }
        try {
            String content = new String(file.contentsToByteArray(), file.getCharset());
            loadSvgFromFile(new File(file.getPath()), content);
        } catch (Exception e) {
            loadSvgFailed();
        }
    }

    /**
     * Loads an SVG file into the panel, updating preview and output.
     *
     * @param file    the SVG file (used for original preview rendering)
     * @param content the SVG file content as a string
     */
    public void loadFile(@NotNull File file, @NotNull String content) {
        loadSvgFromFile(file, content);
    }

    private void loadSvgFromFile(@NotNull File file, @NotNull String content) {
        currentSvgContent = content;
        currentAnalysis = SvgPathExtractor.analyze(content);
        originalPreview.setSvgFile(file);
        setFileLoaded(true);
        updateResult();
    }

    private void loadSvgFailed() {
        currentSvgContent = null;
        currentAnalysis = null;
        originalPreview.setSvgFile(null);
        extractedPreview.setPathData(null);
        outputArea.setText(FxToolsBundle.message("svg.tool.read.error"));
        setFileLoaded(true);
    }

    // ==================== Drag & Drop ====================

    @SuppressWarnings("unchecked")
    private void setupDropTarget(@NotNull JComponent target) {
        target.setDropTarget(new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.rejectDrop();
                        return;
                    }
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) event.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    File svgFile = null;
                    for (File f : files) {
                        if (f.getName().toLowerCase().endsWith(".svg")) {
                            svgFile = f;
                            break;
                        }
                    }
                    if (svgFile == null) {
                        event.dropComplete(false);
                        return;
                    }
                    String content = Files.readString(svgFile.toPath());
                    loadSvgFromFile(svgFile, content);
                    event.dropComplete(true);
                } catch (Exception e) {
                    loadSvgFailed();
                    event.dropComplete(false);
                }
            }
        }));
    }

    // ==================== Processing ====================

    private void updateResult() {
        if (currentSvgContent == null) {
            return;
        }

        boolean convertShapes = convertToggle.isSelected();
        String rawPath = SvgPathExtractor.extract(currentSvgContent, convertShapes);
        if (rawPath == null || rawPath.isBlank()) {
            extractedPreview.setPathData(null);
            outputArea.setText(FxToolsBundle.message("svg.tool.no.paths"));
            rebuildStatusBar(null, 0);
            return;
        }

        int precision = getSelectedPrecision();
        int targetSize = getSelectedSize();
        String result = SvgPathTransformer.normalize(rawPath, targetSize, precision);

        if (result == null || result.isBlank()) {
            extractedPreview.setPathData(null);
            outputArea.setText(FxToolsBundle.message("svg.tool.no.paths"));
            rebuildStatusBar(null, 0);
            return;
        }

        extractedPreview.setPathData(result);
        outputArea.setText(result);
        outputArea.setCaretPosition(0);
        rebuildStatusBar(currentAnalysis, result.length());
    }

    private void rebuildStatusBar(@Nullable SvgAnalysis analysis, int charCount) {
        statusBar.removeAll();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        if (analysis == null || analysis.total() == 0) {
            copyButton.setEnabled(false);
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            statusBar.add(Box.createGlue(), gbc);
            gbc.gridx = 1;
            gbc.weightx = 0;
            statusBar.add(copyButton, gbc);
            statusBar.revalidate();
            statusBar.repaint();
            return;
        }

        copyButton.setEnabled(charCount > 0);

        boolean convertShapes = convertToggle.isSelected();
        Map<ShapeType, Integer> counts = analysis.counts();
        int col = 0;

        // Total paths badge
        int totalPaths = analysis.pathCount()
                + (convertShapes ? analysis.shapeCount() : 0);
        if (totalPaths > 0) {
            gbc.gridx = col++;
            gbc.weightx = 0;
            gbc.insets = JBUI.insets(0, 0, 0, 4);
            statusBar.add(createBadge(
                    FxToolsBundle.message("svg.tool.paths", totalPaths), BADGE_PATH_BG), gbc);
        }

        // Per-shape badges (only when convertShapes is on)
        if (convertShapes) {
            ShapeType[] types = {ShapeType.RECT, ShapeType.CIRCLE, ShapeType.ELLIPSE,
                    ShapeType.LINE, ShapeType.POLYLINE, ShapeType.POLYGON};
            for (ShapeType type : types) {
                Integer count = counts.get(type);
                if (count != null && count > 0) {
                    gbc.gridx = col++;
                    statusBar.add(createBadge(
                            count + " " + type.name().toLowerCase(), BADGE_SHAPE_BG), gbc);
                }
            }
        }

        // Char count
        if (charCount > 0) {
            gbc.gridx = col++;
            gbc.insets = JBUI.insets(0, 4, 0, 0);
            JBLabel charsLabel = new JBLabel(
                    FxToolsBundle.message("svg.tool.chars", NumberFormat.getInstance().format(charCount)));
            charsLabel.setForeground(HINT_FG);
            statusBar.add(charsLabel, gbc);
        }

        // Spacer + Copy button
        gbc.gridx = col++;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.emptyInsets();
        statusBar.add(Box.createGlue(), gbc);

        gbc.gridx = col;
        gbc.weightx = 0;
        statusBar.add(copyButton, gbc);

        statusBar.revalidate();
        statusBar.repaint();
    }

    @NotNull
    private static JBLabel createBadge(@NotNull String text, @NotNull Color bg) {
        JBLabel badge = new JBLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        JBUI.scale(10), JBUI.scale(10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setForeground(BADGE_FG);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, (float) JBUI.scale(11)));
        badge.setBorder(JBUI.Borders.empty(2, 8));
        badge.setOpaque(false);
        return badge;
    }

    private int getSelectedSize() {
        Object item = sizeCombo.getSelectedItem();
        if (item != null) {
            try {
                int val = Integer.parseInt(item.toString().trim());
                if (val > 0) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 24;
    }

    private int getSelectedPrecision() {
        int idx = precisionCombo.getSelectedIndex();
        if (idx >= 0 && idx < PRECISION_OPTIONS.length) {
            return PRECISION_OPTIONS[idx];
        }
        return 2;
    }

    private void copyToClipboard() {
        String text = outputArea.getText();
        if (text != null && !text.isBlank()) {
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            String original = copyButton.getText();
            copyButton.setText(FxToolsBundle.message("svg.tool.copied"));
            Timer timer = new Timer(1500, e -> copyButton.setText(original));
            timer.setRepeats(false);
            timer.start();
        }
    }

    // ==================== Checkerboard ====================

    private static void paintCheckerboard(@NotNull Graphics2D g2, int x, int y, int w, int h,
                                          @NotNull Shape clip) {
        Shape oldClip = g2.getClip();
        g2.setClip(clip);
        int cs = JBUI.scale(CHECKER_SIZE);
        for (int row = 0; row * cs < h; row++) {
            for (int col = 0; col * cs < w; col++) {
                g2.setColor((row + col) % 2 == 0 ? CHECKER_A : CHECKER_B);
                g2.fillRect(x + col * cs, y + row * cs, cs, cs);
            }
        }
        g2.setClip(oldClip);
    }

    // ==================== Original SVG Preview ====================

    private static class OriginalPreview extends JPanel {

        @Nullable
        private Icon svgIcon;

        OriginalPreview() {
            setOpaque(false);
            int s = JBUI.scale(PREVIEW_SIZE);
            Dimension dim = new Dimension(s, s);
            setPreferredSize(dim);
            setMinimumSize(dim);
        }

        void setSvgFile(@Nullable File file) {
            if (file == null) {
                this.svgIcon = null;
            } else {
                try {
                    this.svgIcon = IconLoader.findIcon(file.toURI().toURL());
                } catch (Exception e) {
                    this.svgIcon = null;
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = JBUI.scale(CARD_ARC);

            RoundRectangle2D clip = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
            paintCheckerboard(g2, 0, 0, w, h, clip);

            if (svgIcon != null) {
                int pad = JBUI.scale(PREVIEW_PADDING);
                int availW = w - 2 * pad;
                int availH = h - 2 * pad;
                int iconW = svgIcon.getIconWidth();
                int iconH = svgIcon.getIconHeight();
                if (iconW > 0 && iconH > 0) {
                    double scale = Math.min((double) availW / iconW, (double) availH / iconH);
                    int drawW = (int) (iconW * scale);
                    int drawH = (int) (iconH * scale);
                    int x = pad + (availW - drawW) / 2;
                    int y = pad + (availH - drawH) / 2;

                    Graphics2D ig = (Graphics2D) g2.create(x, y, drawW, drawH);
                    ig.scale(scale, scale);
                    svgIcon.paintIcon(this, ig, 0, 0);
                    ig.dispose();
                }
            }

            g2.dispose();
        }
    }

    // ==================== Extracted Path Preview ====================

    private static class PathPreview extends JPanel {

        @Nullable
        private GeneralPath parsedPath;

        PathPreview() {
            setOpaque(false);
            int s = JBUI.scale(PREVIEW_SIZE);
            Dimension dim = new Dimension(s, s);
            setPreferredSize(dim);
            setMinimumSize(dim);
        }

        void setPathData(@Nullable String pathData) {
            this.parsedPath = pathData != null ? FxSvgRenderer.parseSvgPath(pathData) : null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = JBUI.scale(CARD_ARC);

            RoundRectangle2D clip = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);
            paintCheckerboard(g2, 0, 0, w, h, clip);

            if (parsedPath != null) {
                Rectangle2D bounds = parsedPath.getBounds2D();
                if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                    int pad = JBUI.scale(PREVIEW_PADDING);
                    double availW = w - 2.0 * pad;
                    double availH = h - 2.0 * pad;
                    double scale = Math.min(availW / bounds.getWidth(), availH / bounds.getHeight());
                    double tx = pad + (availW - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
                    double ty = pad + (availH - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;

                    AffineTransform transform = new AffineTransform();
                    transform.translate(tx, ty);
                    transform.scale(scale, scale);

                    g2.setColor(ICON_COLOR);
                    g2.fill(transform.createTransformedShape(parsedPath));
                }
            }

            g2.dispose();
        }
    }
}
