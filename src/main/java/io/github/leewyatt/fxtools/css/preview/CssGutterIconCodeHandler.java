package io.github.leewyatt.fxtools.css.preview;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconDataService;
import io.github.leewyatt.fxtools.toolwindow.iconbrowser.IconPlaceholder;
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

/**
 * Handles gutter icon click for {@code -fx-icon-code} to open a read-only icon preview popup.
 * Shows a large SVG preview with pack name, icon literal, and license info.
 */
public final class CssGutterIconCodeHandler {

    private static final int PREVIEW_SIZE = 130;
    private static final Color SVG_FILL =
            new JBColor(new Color(0x3C3C3C), new Color(0xCCCCCC));
    private static final Color PREVIEW_BG =
            new JBColor(new Color(240, 240, 240), new Color(60, 60, 60));

    private CssGutterIconCodeHandler() {
    }

    /**
     * Opens a read-only icon preview popup at the mouse click location.
     *
     * @param mouseEvent the gutter click event
     * @param icon       the icon entry to preview
     * @param pathData   the resolved SVG path data (nullable for {@code np: true} icons;
     *                   when null, a placeholder and "cannot render" hint are shown)
     */
    public static void openPreview(@NotNull MouseEvent mouseEvent,
                                   @NotNull IconDataService.IconEntry icon,
                                   @Nullable String pathData) {
        final GeneralPath path = pathData != null ? FxSvgRenderer.parseSvgPath(pathData) : null;
        final boolean renderable = path != null;

        IconDataService.PackInfo pack = icon.getPack();

        // ==================== Content ====================
        JPanel content = new JPanel(new BorderLayout(JBUI.scale(12), 0));
        content.setBorder(JBUI.Borders.empty(10));
        content.setOpaque(false);

        // ==================== Left: SVG Preview (or placeholder) ====================
        JPanel previewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Rounded background
                g2.setColor(PREVIEW_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h,
                        JBUI.scale(8), JBUI.scale(8)));

                if (renderable) {
                    Rectangle2D bounds = path.getBounds2D();
                    if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                        int pad = JBUI.scale(10);
                        double availW = w - pad * 2;
                        double availH = h - pad * 2;
                        double scale = Math.min(availW / bounds.getWidth(),
                                availH / bounds.getHeight());
                        double tx = pad + (availW - bounds.getWidth() * scale) / 2
                                - bounds.getX() * scale;
                        double ty = pad + (availH - bounds.getHeight() * scale) / 2
                                - bounds.getY() * scale;

                        AffineTransform at = new AffineTransform();
                        at.translate(tx, ty);
                        at.scale(scale, scale);

                        g2.setColor(SVG_FILL);
                        g2.fill(at.createTransformedShape(path));
                    }
                } else {
                    // Centered placeholder
                    int ph = Math.min(w, h) * 2 / 3;
                    IconPlaceholder.paint(g2, (w - ph) / 2, (h - ph) / 2, ph);
                }

                g2.dispose();
            }
        };
        previewPanel.setOpaque(false);
        int size = JBUI.scale(PREVIEW_SIZE);
        previewPanel.setPreferredSize(new Dimension(size, size));
        content.add(previewPanel, BorderLayout.WEST);

        // ==================== Right: Info ====================
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        // Top glue — vertically centers the text block against the 130px preview
        infoPanel.add(Box.createVerticalGlue());

        // Pack name (bold)
        JBLabel packLabel = new JBLabel(pack.getName());
        packLabel.setFont(packLabel.getFont().deriveFont(Font.BOLD));
        packLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(packLabel);

        infoPanel.add(Box.createVerticalStrut(JBUI.scale(4)));

        // Icon literal
        JBLabel literalLabel = new JBLabel(icon.getLiteral());
        literalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(literalLabel);

        // "Cannot render" hint (only for np:true icons)
        if (!renderable) {
            infoPanel.add(Box.createVerticalStrut(JBUI.scale(6)));
            JBLabel hint = new JBLabel(FxToolsBundle.message("icon.browser.cannot.render"));
            hint.setForeground(UIUtil.getContextHelpForeground());
            hint.setFont(hint.getFont().deriveFont(Font.ITALIC));
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(hint);
        }

        // Bottom glue — balances the top glue for centering
        infoPanel.add(Box.createVerticalGlue());

        content.add(infoPanel, BorderLayout.CENTER);

        // ==================== Popup ====================
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(content, previewPanel)
                .setFocusable(true)
                .setRequestFocus(false)
                .setMovable(true)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .createPopup();

        popup.show(new RelativePoint(mouseEvent));
    }

}
