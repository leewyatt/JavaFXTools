package io.github.leewyatt.fxtools.css.completion;

import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.css.preview.CssPreviewIconRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.util.Map;

/**
 * Provides icons for JavaFX {@code -fx-cursor} enum values in the completion popup.
 * All icons are drawn via {@code paintIcon()} with zero bitmap allocation.
 * Each icon has a subtle circular background to visually separate it from text.
 */
final class CssCursorIcons {

    private static final int S = CssPreviewIconRenderer.ICON_SIZE;
    private static final float CX = S / 2f;
    private static final float CY = S / 2f;

    private static final Color FG = JBColor.foreground();

    private static final Map<String, Icon> ICONS = buildIcons();

    private CssCursorIcons() {
    }

    /**
     * Returns a cursor icon for the given CSS cursor value, or {@code null}
     * if no icon is available for this value.
     */
    @Nullable
    static Icon getIcon(@NotNull String cursorValue) {
        return ICONS.get(cursorValue);
    }

    // ==================== Icon Registry ====================

    private static Map<String, Icon> buildIcons() {
        return Map.ofEntries(
                Map.entry("default", createDefaultIcon()),
                Map.entry("crosshair", createCrosshairIcon()),
                Map.entry("text", createTextIcon()),
                Map.entry("hand", createHandIcon()),
                Map.entry("move", createMoveIcon()),
                Map.entry("wait", createWaitIcon()),
                Map.entry("null", createNullIcon()),
                Map.entry("e-resize", createResizeIcon(0)),
                Map.entry("w-resize", createResizeIcon(0)),
                Map.entry("h-resize", createResizeIcon(0)),
                Map.entry("n-resize", createResizeIcon(90)),
                Map.entry("s-resize", createResizeIcon(90)),
                Map.entry("v-resize", createResizeIcon(90)),
                Map.entry("ne-resize", createResizeIcon(45)),
                Map.entry("sw-resize", createResizeIcon(45)),
                Map.entry("nw-resize", createResizeIcon(135)),
                Map.entry("se-resize", createResizeIcon(135))
        );
    }

    // ==================== Arrow Cursor (default) ====================

    private static Icon createDefaultIcon() {
        GeneralPath arrow = new GeneralPath();
        arrow.moveTo(3, 1);
        arrow.lineTo(3, 11);
        arrow.lineTo(5.5f, 8.5f);
        arrow.lineTo(8, 13);
        arrow.lineTo(9.5f, 12);
        arrow.lineTo(7, 7.5f);
        arrow.lineTo(10, 7.5f);
        arrow.closePath();
        return createIcon(g2 -> g2.fill(arrow));
    }

    // ==================== Crosshair (+) ====================

    private static Icon createCrosshairIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(new Line2D.Float(CX, 2.5f, CX, S - 2.5f));
            g2.draw(new Line2D.Float(2.5f, CY, S - 2.5f, CY));
        });
    }

    // ==================== Text (I-beam) ====================

    private static Icon createTextIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Line2D.Float(CX, 3, CX, S - 3));
            g2.draw(new Line2D.Float(CX - 3, 3, CX + 3, 3));
            g2.draw(new Line2D.Float(CX - 3, S - 3, CX + 3, S - 3));
        });
    }

    // ==================== Hand (pointer) ====================

    private static Icon createHandIcon() {
        GeneralPath hand = new GeneralPath();
        hand.moveTo(6, 1);
        hand.lineTo(8, 1);
        hand.lineTo(8, 6);
        hand.lineTo(10, 5);
        hand.lineTo(11.5f, 5);
        hand.lineTo(11.5f, 7);
        hand.lineTo(12.5f, 6.5f);
        hand.lineTo(13, 6.5f);
        hand.lineTo(13, 8.5f);
        hand.lineTo(13, 11);
        hand.quadTo(13, 13, 10.5f, 13);
        hand.lineTo(5.5f, 13);
        hand.quadTo(3, 13, 3, 11);
        hand.lineTo(3, 8);
        hand.lineTo(5, 8);
        hand.lineTo(5, 6);
        hand.lineTo(6, 6);
        hand.closePath();
        return createIcon(g2 -> g2.fill(hand));
    }

    // ==================== Move (4-way arrow) ====================

    private static Icon createMoveIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new Line2D.Float(CX, 2, CX, S - 2));
            g2.draw(new Line2D.Float(2, CY, S - 2, CY));
            float hs = 2.5f;
            drawArrowHead(g2, CX, 2, 0, hs);
            drawArrowHead(g2, CX, S - 2, 180, hs);
            drawArrowHead(g2, 2, CY, -90, hs);
            drawArrowHead(g2, S - 2, CY, 90, hs);
        });
    }

    // ==================== Wait (hourglass) ====================

    private static Icon createWaitIcon() {
        return createIcon(g2 -> {
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Line2D.Float(3, 2, S - 3, 2));
            g2.draw(new Line2D.Float(3, S - 2, S - 3, S - 2));
            GeneralPath hg = new GeneralPath();
            hg.moveTo(4, 3);
            hg.lineTo(CX, CY);
            hg.lineTo(S - 4, 3);
            hg.moveTo(4, S - 3);
            hg.lineTo(CX, CY);
            hg.lineTo(S - 4, S - 3);
            g2.draw(hg);
            GeneralPath sand = new GeneralPath();
            sand.moveTo(5.5f, S - 4);
            sand.lineTo(CX, CY + 1);
            sand.lineTo(S - 5.5f, S - 4);
            sand.closePath();
            g2.fill(sand);
        });
    }

    // ==================== Null (inherit parent / no cursor — empty placeholder) ====================

    private static Icon createNullIcon() {
        return createIcon(g2 -> { });
    }

    // ==================== Resize (bidirectional arrow) ====================

    private static Icon createResizeIcon(int angleDegrees) {
        return createIcon(g2 -> {
            AffineTransform old = g2.getTransform();
            g2.translate(CX, CY);
            g2.rotate(Math.toRadians(angleDegrees));
            float halfLen = 5f;
            float hs = 2.5f;
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Line2D.Float(-halfLen, 0, halfLen, 0));
            drawArrowHead(g2, -halfLen, 0, -90, hs);
            drawArrowHead(g2, halfLen, 0, 90, hs);
            g2.setTransform(old);
        });
    }

    // ==================== Arrow Head Utility ====================

    /**
     * Draws a small filled arrow head at (tipX, tipY) pointing in the given direction.
     * 0 degrees = up, 90 = right, 180 = down, -90 = left.
     */
    private static void drawArrowHead(@NotNull Graphics2D g2,
                                      float tipX, float tipY,
                                      float angleDegrees, float size) {
        AffineTransform old = g2.getTransform();
        g2.translate(tipX, tipY);
        g2.rotate(Math.toRadians(angleDegrees));
        GeneralPath head = new GeneralPath();
        head.moveTo(0, -size);
        head.lineTo(-size * 0.6f, 0);
        head.lineTo(size * 0.6f, 0);
        head.closePath();
        g2.fill(head);
        g2.setTransform(old);
    }

    // ==================== Icon Factory ====================

    @FunctionalInterface
    private interface IconPainter {
        void paint(@NotNull Graphics2D g2);
    }

    private static Icon createIcon(@NotNull IconPainter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);

                g2.setColor(FG);
                painter.paint(g2);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return S;
            }

            @Override
            public int getIconHeight() {
                return S;
            }
        };
    }
}
