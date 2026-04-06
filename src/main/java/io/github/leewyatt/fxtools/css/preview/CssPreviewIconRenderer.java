package io.github.leewyatt.fxtools.css.preview;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.JBColor;
import io.github.leewyatt.fxtools.css.preview.effect.EffectConfig;
import io.github.leewyatt.fxtools.css.preview.effect.EffectType;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Gutter preview icons for CSS color, gradient, and SVG values.
 * All icons share the same logical size ({@link #ICON_SIZE}) and are drawn
 * in a coordinate system of that size; zoom/HiDPI scaling is handled by the
 * {@link BasePreviewIcon} base class.
 */
public final class CssPreviewIconRenderer {

    /** Logical pixel size for all gutter and completion preview icons. */
    public static final int ICON_SIZE = 13;
    private static final Color BORDER_COLOR = new JBColor(new Color(180, 180, 180), new Color(100, 100, 100));
    private static final Color ERROR_COLOR = new JBColor(new Color(200, 60, 60), new Color(220, 80, 80));

    private static final Pattern BLOCK_COMMENT_PATTERN =
            Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private CssPreviewIconRenderer() {
    }

    /**
     * Replaces CSS block comments with same-length whitespace to preserve character offsets.
     * This prevents the PROPERTY_PATTERN regex from matching "key: value" patterns
     * inside comments, which would consume subsequent real properties.
     */
    @NotNull
    public static String stripCommentsPreservingOffsets(@NotNull String text) {
        Matcher m = BLOCK_COMMENT_PATTERN.matcher(text);
        if (!m.find()) {
            return text;
        }
        char[] chars = text.toCharArray();
        m.reset();
        while (m.find()) {
            for (int i = m.start(); i < m.end(); i++) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    /**
     * Square icon for direct color values.
     */
    @NotNull
    public static Icon createSquareIcon(@NotNull Color color) {
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                g2.setColor(color);
                g2.fillRect(1, 1, ICON_SIZE - 2, ICON_SIZE - 2);
                g2.setColor(BORDER_COLOR);
                g2.drawRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createSquareIcon(color);
                icon.setScale(newScale);
                return icon;
            }
        };
    }

    /**
     * Circle icon for indirect reference with single definition.
     */
    @NotNull
    public static Icon createCircleIcon(@NotNull Color color) {
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                g2.setColor(color);
                g2.fillOval(0, 0, ICON_SIZE, ICON_SIZE);
                g2.setColor(BORDER_COLOR);
                g2.drawOval(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createCircleIcon(color);
                icon.setScale(newScale);
                return icon;
            }
        };
    }

    /**
     * Half-circle icon for indirect reference with multiple definitions.
     */
    @NotNull
    public static Icon createHalfCircleIcon(@NotNull Color left, @NotNull Color right) {
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                int half = ICON_SIZE / 2;
                g2.setClip(new Ellipse2D.Float(0, 0, ICON_SIZE, ICON_SIZE));
                g2.setColor(left);
                g2.fillRect(0, 0, half, ICON_SIZE);
                g2.setColor(right);
                g2.fillRect(half, 0, ICON_SIZE - half, ICON_SIZE);
                g2.setClip(null);
                g2.setColor(BORDER_COLOR);
                g2.drawOval(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createHalfCircleIcon(left, right);
                icon.setScale(newScale);
                return icon;
            }
        };
    }

    /**
     * Circle icon filled with gradient.
     */
    @NotNull
    public static Icon createGradientCircleIcon(@NotNull Paint paint) {
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                g2.setClip(new Ellipse2D.Float(0, 0, ICON_SIZE, ICON_SIZE));
                g2.setPaint(paint);
                g2.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
                g2.setClip(null);
                g2.setColor(BORDER_COLOR);
                g2.drawOval(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createGradientCircleIcon(paint);
                icon.setScale(newScale);
                return icon;
            }
        };
    }

    /**
     * SVG path icon.
     */
    @Nullable
    public static Icon createSvgIcon(@NotNull String pathData, @NotNull Color color) {
        GeneralPath path = FxSvgRenderer.parseSvgPath(pathData);
        if (path == null) {
            return null;
        }
        Rectangle2D bounds = path.getBounds2D();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return null;
        }

        double svgScale = Math.min((ICON_SIZE - 2) / bounds.getWidth(), (ICON_SIZE - 2) / bounds.getHeight());
        double tx = (ICON_SIZE - bounds.getWidth() * svgScale) / 2 - bounds.getX() * svgScale;
        double ty = (ICON_SIZE - bounds.getHeight() * svgScale) / 2 - bounds.getY() * svgScale;
        AffineTransform svgTransform = new AffineTransform();
        svgTransform.translate(tx, ty);
        svgTransform.scale(svgScale, svgScale);
        Shape transformed = svgTransform.createTransformedShape(path);

        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                g2.setColor(color);
                g2.fill(transformed);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createSvgIcon(pathData, color);
                if (icon != null) {
                    icon.setScale(newScale);
                }
                return icon;
            }
        };
    }

    /**
     * Error icon for invalid -fx-shape values.
     */
    @NotNull
    public static Icon createErrorIcon() {
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                g2.setColor(ERROR_COLOR);
                g2.fillOval(0, 0, ICON_SIZE, ICON_SIZE);
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, ICON_SIZE * 0.75f));
                FontMetrics fm = g2.getFontMetrics();
                String mark = "!";
                int mtx = (ICON_SIZE - fm.stringWidth(mark)) / 2;
                int mty = (ICON_SIZE + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(mark, mtx, mty);
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createErrorIcon();
                icon.setScale(newScale);
                return icon;
            }
        };
    }

    /**
     * Effect shadow icon — small rounded rectangle with a simplified shadow indicator.
     * DropShadow: shadow offset to bottom-right; InnerShadow: inner shadow gradient.
     */
    @Nullable
    public static Icon createEffectIcon(@NotNull EffectConfig config) {
        Color shadowColor = config.getColor();
        boolean isInner = config.getEffectType() == EffectType.INNERSHADOW;
        return new BasePreviewIcon() {
            @Override
            protected void paint(@NotNull Graphics2D g2) {
                int off = ICON_SIZE / 4;
                int body = ICON_SIZE - off;
                Color semi = new Color(
                        shadowColor.getRed(), shadowColor.getGreen(),
                        shadowColor.getBlue(), Math.min(180, shadowColor.getAlpha()));
                if (isInner) {
                    // InnerShadow: white rect with inner shadow overlay
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(1, 1, ICON_SIZE - 2, ICON_SIZE - 2, 4, 4);
                    g2.setColor(semi);
                    g2.fillRoundRect(1, 1, ICON_SIZE / 2, ICON_SIZE / 2, 4, 4);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1, 4, 4);
                } else {
                    // DropShadow: shadow behind, white rect on top
                    g2.setColor(semi);
                    g2.fillRoundRect(off, off, body, body, 4, 4);
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(1, 1, body, body, 4, 4);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(1, 1, body - 1, body - 1, 4, 4);
                }
            }

            @Override
            protected @NotNull BasePreviewIcon copyWithScale(float newScale) {
                BasePreviewIcon icon = (BasePreviewIcon) createEffectIcon(config);
                if (icon != null) {
                    icon.setScale(newScale);
                }
                return icon;
            }
        };
    }

    /**
     * Base icon class with ScalableIcon + editor zoom support.
     * All subclass drawing happens in the base ICON_SIZE x ICON_SIZE coordinate system.
     */
    private static abstract class BasePreviewIcon implements ScalableIcon {
        private static final java.util.WeakHashMap<Component, Editor> EDITOR_CACHE = new java.util.WeakHashMap<>();

        private float scale = 1f;
        private float lastZoom = 1f;

        void setScale(float scale) {
            this.scale = scale;
        }

        @Override
        public float getScale() {
            return scale;
        }

        @Override
        public @NotNull Icon scale(float scaleFactor) {
            if (scaleFactor == this.scale) {
                return this;
            }
            return copyWithScale(scaleFactor);
        }

        @Override
        public int getIconWidth() {
            return Math.round(ICON_SIZE * scale * lastZoom);
        }

        @Override
        public int getIconHeight() {
            return Math.round(ICON_SIZE * scale * lastZoom);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            lastZoom = getEditorZoom(c);
            float effectiveScale = scale * lastZoom;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            if (effectiveScale != 1f) {
                g2.scale(effectiveScale, effectiveScale);
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paint(g2);
            g2.dispose();
        }

        protected abstract void paint(@NotNull Graphics2D g2);

        protected abstract @NotNull BasePreviewIcon copyWithScale(float newScale);

        private static float getEditorZoom(@Nullable Component c) {
            if (c == null) {
                return 1f;
            }
            try {
                // Lookup Editor from cache; only call DataManager on first encounter
                Editor editor = EDITOR_CACHE.get(c);
                if (editor == null) {
                    editor = CommonDataKeys.EDITOR.getData(
                            DataManager.getInstance().getDataContext(c));
                    if (editor == null) {
                        return 1f;
                    }
                    EDITOR_CACHE.put(c, editor);
                }
                // These are trivial getters — no performance concern
                float editorFontSize = editor.getColorsScheme().getEditorFontSize2D();
                float baseFontSize = EditorColorsManager.getInstance()
                        .getGlobalScheme().getEditorFontSize2D();
                if (baseFontSize > 0) {
                    return editorFontSize / baseFontSize;
                }
            } catch (Exception ignored) {
            }
            return 1f;
        }
    }

}
