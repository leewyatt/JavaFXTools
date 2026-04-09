package io.github.leewyatt.fxtools.css.preview;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.paintpicker.DoubleField;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles gutter SVG icon click to open a preview popup with size controls.
 */
public final class CssGutterSvgHandler {

    private static final int PREVIEW_SIZE = 130;
    private static final DecimalFormat FMT = new DecimalFormat("0.###");
    private static final Color SVG_FILL = new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY);
    private static final Color PREVIEW_BG = new JBColor(new Color(240, 240, 240), new Color(60, 60, 60));
    private static final Pattern INDENT_PATTERN = Pattern.compile("\\n([ \\t]+)\\S");
    private static final Pattern PROPERTY_PATTERN =
            Pattern.compile("([\\w-]+)\\s*:\\s*([^;{}]+?)\\s*;");

    private CssGutterSvgHandler() {
    }

    /**
     * Opens a SVG preview popup for CSS file context.
     *
     * @param mouseEvent the click event
     * @param psiFile    the CSS file
     * @param pathData   the SVG path data string
     * @param matchStart the start offset of the property in the document (for locating the selector block)
     */
    public static void openPreview(@NotNull MouseEvent mouseEvent,
                                    @NotNull PsiFile psiFile,
                                    @NotNull String pathData,
                                    int matchStart) {
        Project project = psiFile.getProject();
        showSvgPopup(mouseEvent, pathData, false, () -> {},
                (w, h) -> applySize(project, psiFile, matchStart, w, h));
    }

    /**
     * Opens a read-only SVG preview popup (no Apply Size).
     * Used for SVGPath.setContent() where size editing doesn't apply.
     */
    public static void openPreviewReadOnly(@NotNull MouseEvent mouseEvent,
                                            @NotNull String pathData) {
        showSvgPopup(mouseEvent, pathData, true, () -> {}, (w, h) -> {});
    }

    /**
     * Opens a SVG preview popup for inline CSS context (Java setStyle / FXML style attribute).
     * Apply size appends -fx-pref-width/-height to the inline style text.
     *
     * @param mouseEvent        the click event
     * @param psiFile           the Java/FXML file
     * @param pathData          the SVG path data string
     * @param styleContentStart document offset of the style text start (after opening quote)
     * @param styleContentEnd   document offset of the style text end (before closing quote)
     */
    public static void openPreviewInline(@NotNull MouseEvent mouseEvent,
                                          @NotNull PsiFile psiFile,
                                          @NotNull String pathData,
                                          int styleContentStart, int styleContentEnd) {
        Project project = psiFile.getProject();
        Editor editor = findEditor(project, psiFile.getVirtualFile());

        Runnable onClose;
        BiConsumer<Double, Double> onApply;
        if (editor != null) {
            Document document = editor.getDocument();
            RangeMarker marker = document.createRangeMarker(styleContentStart, styleContentEnd);
            marker.setGreedyToRight(true);
            onClose = marker::dispose;
            onApply = (w, h) -> applySizeInline(project, document, marker, w, h);
        } else {
            // Preview-only mode: no Apply Size
            onClose = () -> {};
            onApply = (w, h) -> {};
        }

        showSvgPopup(mouseEvent, pathData, false, onClose, onApply);
    }

    /**
     * Builds and shows the SVG preview popup with size controls.
     *
     * @param readOnly if true, size fields are read-only and lock/apply buttons are hidden
     */
    private static void showSvgPopup(@NotNull MouseEvent mouseEvent,
                                      @NotNull String pathData,
                                      boolean readOnly,
                                      @NotNull Runnable onClose,
                                      @NotNull BiConsumer<Double, Double> onApply) {
        GeneralPath path = FxSvgRenderer.parseSvgPath(pathData);
        if (path == null) {
            return;
        }
        Rectangle2D bounds = path.getBounds2D();
        double origW = bounds.getWidth() > 0 ? bounds.getWidth() : 100;
        double origH = bounds.getHeight() > 0 ? bounds.getHeight() : 100;

        // ---- Build popup content ----
        JPanel content = new JPanel(new BorderLayout(JBUI.scale(12), 0));
        content.setBorder(JBUI.Borders.empty(10));
        content.setOpaque(false);

        // Left: SVG preview
        SvgPreviewPanel previewPanel = new SvgPreviewPanel(path);
        int size = JBUI.scale(PREVIEW_SIZE);
        previewPanel.setPreferredSize(new Dimension(size, size));
        content.add(previewPanel, BorderLayout.WEST);

        // Right: size controls
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(2, 2);
        gbc.anchor = GridBagConstraints.LINE_START;

        DoubleField widthField = new DoubleField(7);
        DoubleField heightField = new DoubleField(7);
        widthField.setText(FMT.format(origW));
        heightField.setText(FMT.format(origH));

        if (readOnly) {
            widthField.setEditable(false);
            heightField.setEditable(false);
        }

        // Layout: Width row
        gbc.gridx = 0; gbc.gridy = 0;
        rightPanel.add(new JBLabel("Width"), gbc);
        gbc.gridx = 1;
        rightPanel.add(widthField, gbc);

        if (!readOnly) {
            // Lock toggle (editable mode only)
            boolean[] locked = {true};
            double[] ratio = {origH > 0 ? origW / origH : 1.0};
            JToggleButton lockBtn = new JToggleButton(new LockIcon(true), true);
            lockBtn.setSelectedIcon(new LockIcon(true));
            lockBtn.setIcon(new LockIcon(false));
            lockBtn.setToolTipText("Lock aspect ratio");
            lockBtn.setMargin(JBUI.insets(1));
            lockBtn.setBorderPainted(false);
            lockBtn.setContentAreaFilled(false);
            lockBtn.setFocusPainted(false);
            Dimension lockSize = JBUI.size(22, 22);
            lockBtn.setPreferredSize(lockSize);
            lockBtn.setMaximumSize(lockSize);
            lockBtn.setMinimumSize(lockSize);
            lockBtn.addActionListener(e -> {
                locked[0] = lockBtn.isSelected();
                if (locked[0]) {
                    try {
                        double w = Double.parseDouble(widthField.getText());
                        double h = Double.parseDouble(heightField.getText());
                        if (h > 0) {
                            ratio[0] = w / h;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            });

            // Document listener for real-time linked updates
            boolean[] updating = {false};
            widthField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                private void sync() {
                    if (updating[0] || !locked[0]) { return; }
                    updating[0] = true;
                    try {
                        double w = Double.parseDouble(widthField.getText());
                        heightField.setText(FMT.format(w / ratio[0]));
                    } catch (NumberFormatException ignored) {
                    }
                    updating[0] = false;
                }
            });
            heightField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { sync(); }
                private void sync() {
                    if (updating[0] || !locked[0]) { return; }
                    updating[0] = true;
                    try {
                        double h = Double.parseDouble(heightField.getText());
                        widthField.setText(FMT.format(h * ratio[0]));
                    } catch (NumberFormatException ignored) {
                    }
                    updating[0] = false;
                }
            });

            gbc.gridx = 2; gbc.gridy = 0; gbc.gridheight = 2;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;
            rightPanel.add(lockBtn, gbc);
            gbc.gridheight = 1;
            gbc.anchor = GridBagConstraints.LINE_START;
        }

        // Height row
        gbc.gridx = 0; gbc.gridy = 1;
        rightPanel.add(new JBLabel("Height"), gbc);
        gbc.gridx = 1;
        rightPanel.add(heightField, gbc);

        content.add(rightPanel, BorderLayout.CENTER);

        // Create popup
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(content, previewPanel)
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .createPopup();

        popup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                onClose.run();
            }
        });

        if (!readOnly) {
            // Apply button (editable mode only)
            JButton applyBtn = new JButton("Apply size");
            applyBtn.addActionListener(e -> {
                try {
                    double w = Double.parseDouble(widthField.getText());
                    double h = Double.parseDouble(heightField.getText());
                    onApply.accept(w, h);
                } catch (NumberFormatException ignored) {
                }
                popup.cancel();
            });
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = JBUI.insets(8, 2, 2, 2);
            rightPanel.add(applyBtn, gbc);
        }

        popup.show(new RelativePoint(mouseEvent));
    }

    /**
     * Writes -fx-pref-width and -fx-pref-height into an inline style string.
     */
    private static void applySizeInline(@NotNull Project project, @NotNull Document document,
                                         @NotNull RangeMarker marker, double width, double height) {
        if (!marker.isValid()) {
            return;
        }
        int start = marker.getStartOffset();
        int end = marker.getEndOffset();
        String currentCss = document.getText(new TextRange(start, end));
        String newCss = replaceSizeInCssText(currentCss, width, height);
        WriteCommandAction.runWriteCommandAction(project, "Apply SVG Size", null, () -> {
            document.replaceString(start, end, newCss);
        });
    }

    /**
     * Removes existing -fx-pref-width/-height and inserts new values
     * right after the -fx-shape (or similar SVG path) property.
     * Handles both single-line (regular string) and multi-line (text block) formats.
     */
    @NotNull
    static String replaceSizeInCssText(@NotNull String cssText, double width, double height) {
        if (cssText.contains("\n")) {
            return replaceSizeMultiLine(cssText, width, height);
        }
        return replaceSizeSingleLine(cssText, width, height);
    }

    /**
     * Single-line mode: inline insertion after -fx-shape property.
     */
    @NotNull
    private static String replaceSizeSingleLine(@NotNull String cssText, double width, double height) {
        String cleaned = cssText
                .replaceAll("\\s*-fx-pref-width\\s*:[^;]*;?", "")
                .replaceAll("\\s*-fx-pref-height\\s*:[^;]*;?", "")
                .trim();
        if (!cleaned.isEmpty() && !cleaned.endsWith(";")) {
            cleaned += ";";
        }

        String sizeText = " -fx-pref-width: " + FMT.format(width) + "px"
                + "; -fx-pref-height: " + FMT.format(height) + "px";

        int insertPos = findShapePropertyEnd(cleaned);
        if (insertPos >= 0) {
            return cleaned.substring(0, insertPos) + sizeText + ";" + cleaned.substring(insertPos);
        }

        if (!cleaned.isEmpty()) {
            cleaned += " ";
        }
        return cleaned + sizeText.trim();
    }

    /**
     * Multi-line mode (text block): inserts size as new lines after the -fx-shape line,
     * preserving existing indentation and line structure.
     */
    @NotNull
    private static String replaceSizeMultiLine(@NotNull String cssText, double width, double height) {
        String[] lines = cssText.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        String indent = detectInlineIndent(lines);
        boolean inserted = false;
        int shapeLineIdx = findShapeLineIndex(lines);

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Skip existing size property lines
            if (trimmed.startsWith("-fx-pref-width") || trimmed.startsWith("-fx-pref-height")) {
                continue;
            }

            sb.append(lines[i]);

            // Insert size lines right after the shape line
            if (!inserted && i == shapeLineIdx) {
                sb.append('\n').append(indent).append("-fx-pref-width: ").append(FMT.format(width)).append("px;");
                sb.append('\n').append(indent).append("-fx-pref-height: ").append(FMT.format(height)).append("px;");
                inserted = true;
            }

            // Append newline between lines (not after last)
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }

        // Fallback: if no shape line found, append before the last line
        if (!inserted) {
            // Find the last newline to insert before it
            int lastNl = sb.lastIndexOf("\n");
            if (lastNl >= 0) {
                String tail = sb.substring(lastNl);
                sb.setLength(lastNl);
                sb.append('\n').append(indent).append("-fx-pref-width: ").append(FMT.format(width)).append("px;");
                sb.append('\n').append(indent).append("-fx-pref-height: ").append(FMT.format(height)).append("px;");
                sb.append(tail);
            }
        }

        return sb.toString();
    }

    /**
     * Finds the line index containing -fx-shape or -fx-arrow-region.
     */
    private static int findShapeLineIndex(@NotNull String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            if (lower.startsWith("-fx-shape") || lower.startsWith("-fx-arrow-region")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Detects the leading indentation from the first non-blank content line.
     */
    @NotNull
    private static String detectInlineIndent(@NotNull String[] lines) {
        for (String line : lines) {
            if (!line.isBlank()) {
                int idx = 0;
                while (idx < line.length() && (line.charAt(idx) == ' ' || line.charAt(idx) == '\t')) {
                    idx++;
                }
                if (idx > 0) {
                    return line.substring(0, idx);
                }
            }
        }
        return "    ";
    }

    /**
     * Finds the semicolon position right after a SVG path property (-fx-shape, etc.).
     * Used by single-line mode. Returns the index AFTER the semicolon, or -1.
     */
    private static int findShapePropertyEnd(@NotNull String cssText) {
        String lower = cssText.toLowerCase();
        for (String prop : new String[]{"-fx-shape", "-fx-arrow-region"}) {
            int idx = lower.indexOf(prop);
            if (idx < 0) {
                continue;
            }
            boolean inQuote = false;
            char quoteChar = 0;
            for (int i = idx + prop.length(); i < cssText.length(); i++) {
                char c = cssText.charAt(i);
                if (!inQuote && (c == '"' || c == '\'')) {
                    inQuote = true;
                    quoteChar = c;
                } else if (inQuote && c == quoteChar) {
                    inQuote = false;
                } else if (!inQuote && c == ';') {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /**
     * Writes -fx-pref-width and -fx-pref-height into the CSS selector block.
     */
    private static void applySize(@NotNull Project project, @NotNull PsiFile psiFile,
                                    int offsetInBlock, double width, double height) {
        Editor editor = findEditor(project, psiFile.getVirtualFile());
        if (editor == null) {
            return;
        }
        Document document = editor.getDocument();
        String text = document.getText();

        // Find the selector block { ... } that contains offsetInBlock
        int braceOpen = text.lastIndexOf('{', offsetInBlock);
        int braceClose = text.indexOf('}', offsetInBlock);
        if (braceOpen < 0 || braceClose < 0) {
            return;
        }
        String block = text.substring(braceOpen, braceClose + 1);

        // Detect indentation from existing properties
        String indent = detectIndent(block);

        String widthProp = "-fx-pref-width";
        String heightProp = "-fx-pref-height";
        String widthValue = FMT.format(width) + "px";
        String heightValue = FMT.format(height) + "px";

        WriteCommandAction.runWriteCommandAction(project, "Apply SVG Size", null, () -> {
            String currentText = document.getText();
            int bo = currentText.lastIndexOf('{', offsetInBlock);
            int bc = currentText.indexOf('}', offsetInBlock);
            if (bo < 0 || bc < 0) {
                return;
            }
            String currentBlock = currentText.substring(bo, bc + 1);
            String newBlock = setPropertyInBlock(currentBlock, widthProp, widthValue, indent);
            newBlock = setPropertyInBlock(newBlock, heightProp, heightValue, indent);
            document.replaceString(bo, bc + 1, newBlock);
        });
    }

    /**
     * Sets or replaces a property value in a CSS block string.
     */
    @NotNull
    private static String setPropertyInBlock(@NotNull String block, @NotNull String propName,
                                              @NotNull String propValue, @NotNull String indent) {
        Matcher m = PROPERTY_PATTERN.matcher(block);
        while (m.find()) {
            if (m.group(1).equals(propName)) {
                // Replace existing value
                return block.substring(0, m.start(2)) + propValue + block.substring(m.end(2));
            }
        }
        // Insert before closing brace
        int closeBrace = block.lastIndexOf('}');
        if (closeBrace < 0) {
            return block;
        }
        String insertion = indent + propName + ": " + propValue + ";\n";
        return block.substring(0, closeBrace) + insertion + block.substring(closeBrace);
    }

    /**
     * Detects the indentation used in a CSS block.
     */
    @NotNull
    private static String detectIndent(@NotNull String block) {
        Matcher m = INDENT_PATTERN.matcher(block);
        if (m.find()) {
            return m.group(1);
        }
        return "    ";
    }

    @Nullable
    private static Editor findEditor(@NotNull Project project, @Nullable VirtualFile vFile) {
        if (vFile == null) {
            return null;
        }
        var editors = FileEditorManager.getInstance(project).getEditors(vFile);
        for (var fe : editors) {
            if (fe instanceof TextEditor te) {
                return te.getEditor();
            }
        }
        return null;
    }

    /**
     * Custom painted lock/unlock icon.
     */
    private static class LockIcon implements Icon {
        private static final Color LOCKED_COLOR = new JBColor(new Color(80, 80, 80), new Color(190, 190, 190));
        private static final Color UNLOCKED_COLOR = new JBColor(new Color(150, 150, 150), new Color(120, 120, 120));
        private final boolean locked;

        LockIcon(boolean locked) {
            this.locked = locked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);

            int w = getIconWidth();
            int h = getIconHeight();
            g2.setColor(locked ? LOCKED_COLOR : UNLOCKED_COLOR);
            g2.setStroke(new java.awt.BasicStroke(1.5f));

            // Lock body (rounded rect at bottom)
            int bx = w / 2 - 5, by = h / 2 - 1, bw = 10, bh = 8;
            g2.fillRoundRect(bx, by, bw, bh, 2, 2);

            // Shackle (arc at top)
            g2.setColor(locked ? LOCKED_COLOR : UNLOCKED_COLOR);
            if (locked) {
                g2.drawArc(w / 2 - 4, by - 6, 8, 10, 0, 180);
            } else {
                g2.drawArc(w / 2 - 4, by - 8, 8, 10, 0, 180);
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() { return JBUI.scale(16); }

        @Override
        public int getIconHeight() { return JBUI.scale(16); }
    }

    /**
     * Custom panel that renders an SVG path centered and scaled.
     */
    private static class SvgPreviewPanel extends JPanel {
        private final GeneralPath path;

        SvgPreviewPanel(@NotNull GeneralPath path) {
            this.path = path;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2.setColor(PREVIEW_BG);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, JBUI.scale(8), JBUI.scale(8)));

            // Scale SVG to fit with padding
            Rectangle2D bounds = path.getBounds2D();
            if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
                g2.dispose();
                return;
            }

            int pad = JBUI.scale(10);
            double availW = w - pad * 2;
            double availH = h - pad * 2;
            double scale = Math.min(availW / bounds.getWidth(), availH / bounds.getHeight());
            double tx = pad + (availW - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
            double ty = pad + (availH - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;

            AffineTransform at = new AffineTransform();
            at.translate(tx, ty);
            at.scale(scale, scale);
            Shape transformed = at.createTransformedShape(path);

            g2.setColor(SVG_FILL);
            g2.fill(transformed);

            g2.dispose();
        }
    }
}
