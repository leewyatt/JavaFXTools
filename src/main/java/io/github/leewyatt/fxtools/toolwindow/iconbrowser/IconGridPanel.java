package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.settings.FxToolsSettingsState;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom-painted grid panel that renders SVG icon cells.
 * Uses a single paintComponent pass for all visible cells (no per-cell JComponents).
 */
public class IconGridPanel extends JPanel {

    private static final int CELL_SIZE = 72;
    private static final int CELL_GAP = 6;
    private static final int CELL_PADDING = 5;
    private static final int GRID_MARGIN = 10;
    private static final int ICON_RENDER_SIZE = 26;
    private static final int ICON_NAME_GAP = 4;
    private static final int TAG_HPAD = 3;
    private static final int TAG_VPAD = 1;
    private static final int MAX_SHAPE_CACHE = 200;

    private static final JBColor HOVER_BG = new JBColor(new Color(0, 0, 0, 20), new Color(255, 255, 255, 20));
    private static final JBColor SELECTED_BORDER = new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    private static final JBColor ICON_COLOR = new JBColor(new Color(0x5A5A5A), new Color(0xBBBBBB));
    private static final JBColor NAME_COLOR = new JBColor(new Color(0x666666), new Color(0x999999));

    // ==================== Pack Tag Colors ====================

    private static final JBColor[][] TAG_COLORS = {
            {new JBColor(new Color(0xE6F1FB), new Color(0x1E3A5F)),
                    new JBColor(new Color(0x185FA5), new Color(0x85B7EB))},
            {new JBColor(new Color(0xEAF3DE), new Color(0x1A3306)),
                    new JBColor(new Color(0x639922), new Color(0x97C459))},
            {new JBColor(new Color(0xFAECE7), new Color(0x3A1508)),
                    new JBColor(new Color(0xD85A30), new Color(0xF0997B))},
            {new JBColor(new Color(0xEEEDFE), new Color(0x1E1B3D)),
                    new JBColor(new Color(0x534AB7), new Color(0xAFA9EC))},
            {new JBColor(new Color(0xFBEAF0), new Color(0x3A0F1E)),
                    new JBColor(new Color(0xD4537E), new Color(0xED93B1))},
            {new JBColor(new Color(0xFAEEDA), new Color(0x311A02)),
                    new JBColor(new Color(0xBA7517), new Color(0xEF9F27))},
    };

    // ==================== Shape Cache ====================

    @SuppressWarnings("serial")
    private static final Map<String, Shape> SHAPE_CACHE = new LinkedHashMap<>(MAX_SHAPE_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Shape> eldest) {
            return size() > MAX_SHAPE_CACHE;
        }
    };

    // ==================== State ====================

    private List<IconDataService.IconEntry> pageIcons = Collections.emptyList();
    private IconDataService service;
    @Nullable
    private Project project;
    private boolean showPackTags;
    private int selectedIndex = -1;
    private int hoverIndex = -1;

    @Nullable
    private SelectionListener selectionListener;

    public interface SelectionListener {
        void onIconSelected(@Nullable IconDataService.IconEntry icon);
    }

    public IconGridPanel() {
        setOpaque(true);
        setBackground(new JBColor(Color.WHITE, new Color(0x2B2D30)));
        setBorder(JBUI.Borders.empty(JBUIScale.scale(20)));
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                int idx = cellIndexAt(e.getPoint());
                if (idx >= 0 && idx < pageIcons.size()) {
                    selectedIndex = idx;
                    if (selectionListener != null) {
                        selectionListener.onIconSelected(pageIcons.get(idx));
                    }
                    repaint();
                    if (e.getClickCount() == 2) {
                        insertPathAtCaret(pageIcons.get(idx));
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = cellIndexAt(e.getPoint());
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverIndex != -1) {
                    hoverIndex = -1;
                    repaint();
                }
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
                repaint();
            }
        });
    }

    /**
     * Shows the copy context menu for the icon under the cursor. Also selects
     * that icon (so the detail panel updates in sync).
     *
     * <p>Popup trigger is platform-dependent: macOS fires on {@code mousePressed},
     * Windows/Linux on {@code mouseReleased}. We handle both and check
     * {@link MouseEvent#isPopupTrigger()} each time.</p>
     */
    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int idx = cellIndexAt(e.getPoint());
        if (idx < 0 || idx >= pageIcons.size()) {
            return;
        }
        // Select the icon so the detail panel reflects it
        selectedIndex = idx;
        if (selectionListener != null) {
            selectionListener.onIconSelected(pageIcons.get(idx));
        }
        repaint();

        IconCopyUtil.showPopupAt(this, pageIcons.get(idx), service, project, e.getX(), e.getY());
    }

    // ==================== Double-click Insert ====================

    private void insertPathAtCaret(@NotNull IconDataService.IconEntry icon) {
        if (project == null || !FxToolsSettingsState.getInstance().enableDoubleClickInsert) {
            return;
        }
        String pathData = service.getPath(icon);
        if (pathData == null || pathData.isBlank()) {
            return;
        }
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null || !editor.getCaretModel().isUpToDate()) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        String docText = editor.getDocument().getText();

        // CSS files: append semicolon if no semicolon follows the insert position
        boolean isCss = false;
        var vf = editor.getVirtualFile();
        if (vf != null && "css".equalsIgnoreCase(vf.getExtension())) {
            isCss = true;
        }
        boolean needSemicolon = isCss && !hasSemicolonAfter(docText, offset);
        String textToInsert = "\"" + pathData + "\"" + (needSemicolon ? ";" : "");

        WriteCommandAction.runWriteCommandAction(project, () ->
                editor.getDocument().insertString(offset, textToInsert));
        editor.getCaretModel().moveToOffset(offset + textToInsert.length());
        editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE);
    }

    /**
     * Checks whether there is a semicolon between the offset and the end of the current line.
     */
    private static boolean hasSemicolonAfter(@NotNull String text, int offset) {
        for (int i = offset; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ';') {
                return true;
            }
            if (c == '\n' || c == '\r') {
                return false;
            }
        }
        return false;
    }

    // ==================== Public API ====================

    public void setSelectionListener(@Nullable SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setPageData(@NotNull List<IconDataService.IconEntry> icons,
                            @NotNull IconDataService service,
                            @Nullable Project project,
                            boolean showPackTags) {
        this.pageIcons = icons;
        this.service = service;
        this.project = project;
        this.showPackTags = showPackTags;
        this.selectedIndex = -1;
        this.hoverIndex = -1;
        revalidate();
        repaint();
    }

    public int getSelectedIndex() { return selectedIndex; }

    @Nullable
    public IconDataService.IconEntry getSelectedIcon() {
        return selectedIndex >= 0 && selectedIndex < pageIcons.size() ? pageIcons.get(selectedIndex) : null;
    }

    // ==================== Layout ====================

    private int getCellSize() { return JBUI.scale(CELL_SIZE); }
    private int getGap() { return JBUI.scale(CELL_GAP); }
    private int getPadding() { return JBUI.scale(CELL_PADDING); }

    private int getMargin() { return JBUI.scale(GRID_MARGIN); }

    private int getColumns() {
        int w = getWidth() - getMargin() * 2;
        int step = getCellSize() + getGap();
        return Math.max(1, (w + getGap()) / step);
    }

    @Override
    public Dimension getPreferredSize() {
        int margin = getMargin();
        int step = getCellSize() + getGap();
        int parentW = getParent() != null ? getParent().getWidth() : 400;
        int cols = Math.max(1, (parentW - margin * 2 + getGap()) / step);
        int rows = (int) Math.ceil((double) pageIcons.size() / cols);
        return new Dimension(cols * step + margin * 2, rows * step + margin * 2);
    }

    // ==================== Painting ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (pageIcons.isEmpty() || service == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cell = getCellSize();
        int gap = getGap();
        int pad = getPadding();
        int step = cell + gap;
        int cols = getColumns();
        int iconSize = JBUI.scale(ICON_RENDER_SIZE);
        int nameGap = JBUI.scale(ICON_NAME_GAP);
        Font nameFont = UIUtil.getLabelFont().deriveFont((float) JBUI.scale(10));
        Font tagFont = UIUtil.getLabelFont().deriveFont(Font.BOLD, (float) JBUI.scale(9));

        int margin = getMargin();
        for (int i = 0; i < pageIcons.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = margin + col * step;
            int cy = margin + row * step;

            // Hover / selection background
            if (i == selectedIndex) {
                g2.setColor(SELECTED_BORDER);
                g2.drawRoundRect(cx + 1, cy + 1, cell - 3, cell - 3, JBUI.scale(4), JBUI.scale(4));
            } else if (i == hoverIndex) {
                g2.setColor(HOVER_BG);
                g2.fillRoundRect(cx + 1, cy + 1, cell - 2, cell - 2, JBUI.scale(4), JBUI.scale(4));
            }

            IconDataService.IconEntry icon = pageIcons.get(i);

            // SVG icon (centered horizontally, with top padding)
            int iconX = cx + (cell - iconSize) / 2;
            int iconY = cy + pad;
            String pathData = icon.isRenderable() ? service.getPath(icon) : null;
            if (pathData != null) {
                Shape shape = getOrParseShape(pathData, iconSize);
                if (shape != null) {
                    Graphics2D ig = (Graphics2D) g2.create(iconX, iconY, iconSize, iconSize);
                    ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    ig.setColor(ICON_COLOR);
                    ig.fill(shape);
                    ig.dispose();
                }
            } else {
                // Either icon is np:true (permanent placeholder) or pack not yet loaded (transient)
                IconPlaceholder.paint(g2, iconX, iconY, iconSize);
            }

            // Icon name (below icon with gap)
            g2.setFont(nameFont);
            g2.setColor(NAME_COLOR);
            FontMetrics fm = g2.getFontMetrics();
            String name = icon.getName();
            int nameY = iconY + iconSize + nameGap + fm.getAscent();
            int maxNameWidth = cell - pad * 2;
            String displayName = truncate(name, fm, maxNameWidth);
            int nameX = cx + (cell - fm.stringWidth(displayName)) / 2;
            g2.drawString(displayName, nameX, nameY);

            // Pack tag
            if (showPackTags) {
                drawPackTag(g2, tagFont, icon.getPack(), cx, cy, cell);
            }
        }

        g2.dispose();
    }

    private void drawPackTag(Graphics2D g2, Font tagFont, IconDataService.PackInfo pack,
                             int cx, int cy, int cell) {
        int colorIdx = pack.getIndex() % TAG_COLORS.length;
        Color bg = TAG_COLORS[colorIdx][0];
        Color fg = TAG_COLORS[colorIdx][1];

        g2.setFont(tagFont);
        FontMetrics fm = g2.getFontMetrics();
        String abbr = abbreviatePack(pack.getId());
        int tw = fm.stringWidth(abbr) + JBUI.scale(TAG_HPAD) * 2;
        int th = fm.getHeight() + JBUI.scale(TAG_VPAD) * 2;
        int tx = cx + cell - tw - JBUI.scale(3);
        int ty = cy + cell - th - JBUI.scale(3);

        g2.setColor(bg);
        g2.fillRoundRect(tx, ty, tw, th, JBUI.scale(3), JBUI.scale(3));
        g2.setColor(fg);
        g2.drawString(abbr, tx + JBUI.scale(TAG_HPAD), ty + JBUI.scale(TAG_VPAD) + fm.getAscent());
    }

    // ==================== Utilities ====================

    private int cellIndexAt(Point p) {
        int margin = getMargin();
        int mx = p.x - margin;
        int my = p.y - margin;
        if (mx < 0 || my < 0) {
            return -1;
        }
        int step = getCellSize() + getGap();
        int cols = getColumns();
        int col = mx / step;
        int row = my / step;
        if (col >= cols) {
            return -1;
        }
        // Check if click is in the gap area
        if (mx % step > getCellSize() || my % step > getCellSize()) {
            return -1;
        }
        return row * cols + col;
    }

    @Nullable
    private static Shape getOrParseShape(@NotNull String pathData, int size) {
        String key = pathData.length() + ":" + pathData.hashCode() + ":" + size;
        Shape cached = SHAPE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        GeneralPath path = FxSvgRenderer.parseSvgPath(pathData);
        if (path == null) {
            return null;
        }
        Rectangle2D bounds = path.getBounds2D();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return null;
        }

        double scale = Math.min((size - 2) / bounds.getWidth(), (size - 2) / bounds.getHeight());
        double tx = (size - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
        double ty = (size - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;
        AffineTransform transform = new AffineTransform();
        transform.translate(tx, ty);
        transform.scale(scale, scale);
        Shape transformed = transform.createTransformedShape(path);

        SHAPE_CACHE.put(key, transformed);
        return transformed;
    }

    @NotNull
    private static String truncate(@NotNull String text, @NotNull FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            if (fm.stringWidth(text.substring(0, i)) + ellipsisWidth <= maxWidth) {
                return text.substring(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }

    @NotNull
    private static String abbreviatePack(@NotNull String packId) {
        if (packId.length() <= 4) {
            return packId;
        }
        // Take first letters of camelCase words or first 3 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < packId.length() && sb.length() < 3; i++) {
            char c = packId.charAt(i);
            if (i == 0 || Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.length() >= 2 ? sb.toString() : packId.substring(0, Math.min(3, packId.length()));
    }

}
