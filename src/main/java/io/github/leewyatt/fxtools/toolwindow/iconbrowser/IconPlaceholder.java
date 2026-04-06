package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Unified placeholder drawn whenever an icon cannot be rendered as SVG (marked
 * {@code np: true} in {@code icon-packs.json}). A thin rounded-rectangle outline
 * with a centered "?" — theme-adaptive, minimal visual weight, recognizable as
 * "data unavailable".
 *
 * <p>Used by the grid, detail preview, gutter markers and completion popup so
 * the placeholder looks identical everywhere the icon is referenced.</p>
 */
public final class IconPlaceholder {

    private static final JBColor COLOR =
            new JBColor(new Color(0x999999), new Color(0x808080));

    private IconPlaceholder() {
    }

    /**
     * Paints the placeholder into the given Graphics2D at (x, y) with the given square size.
     * Callers own the Graphics2D lifecycle.
     */
    public static void paint(@NotNull Graphics2D g, int x, int y, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(COLOR);
            g2.drawRoundRect(x, y, size - 1, size - 1, JBUI.scale(4), JBUI.scale(4));

            // "?" glyph centered, font size ~= 65% of box
            float fontSize = Math.max(8f, size * 0.65f);
            Font qFont = g2.getFont().deriveFont(Font.BOLD, fontSize);
            g2.setFont(qFont);
            FontMetrics fm = g2.getFontMetrics();
            String q = "?";
            int qx = x + (size - fm.stringWidth(q)) / 2;
            int qy = y + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(q, qx, qy);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Returns a Swing {@link Icon} that paints the placeholder at the given pixel size.
     * For gutter markers and completion popup use {@code 16}; larger sizes also work.
     */
    @NotNull
    public static Icon createIcon(int size) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                paint((Graphics2D) g, x, y, size);
            }
        };
    }
}
