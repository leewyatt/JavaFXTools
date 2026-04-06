package io.github.leewyatt.fxtools.css.preview.effect;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Renders DropShadow and InnerShadow effects using Java2D for preview purposes.
 * <p>
 * All parameters (sigma, spread formula, box kernel sizing) are derived from
 * the actual JavaFX source code in:
 * - GaussianRenderState.getGaussianWeights() → sigma = radius/3, spread modifies normalization
 * - BoxShadow.setGaussianWidth() → box kernel size = round(gaussianWidth / passes)
 * - BoxRenderState.validateWeights() → spread as post-normalization boost
 * <p>
 * Core principle: blur only the alpha channel, then colorize with shadow color.
 */
@SuppressWarnings("UndesirableClassUsage")
public final class ShadowRenderer {

    private static final int SHAPE_SIZE = 120;
    private static final int SHAPE_ARC = 0;
    private static final Color SHAPE_FILL = Color.WHITE;
    private static final Color SHAPE_BORDER = Color.BLACK;

    private ShadowRenderer() {
    }

    // ==================== Public API ====================

    public static BufferedImage renderDropShadow(EffectConfig config, int previewWidth, int previewHeight) {
        BufferedImage result = createTransparentImage(previewWidth, previewHeight);
        Graphics2D g2 = result.createGraphics();
        setupGraphics(g2);

        int cx = previewWidth / 2;
        int cy = previewHeight / 2;
        int halfShape = SHAPE_SIZE / 2;

        double hRadius = (config.getWidth() - 1) / 2.0;
        double vRadius = (config.getHeight() - 1) / 2.0;
        int pad = (int) Math.ceil(Math.max(hRadius, vRadius)) + 2;
        int imgW = SHAPE_SIZE + pad * 2;
        int imgH = SHAPE_SIZE + pad * 2;

        int[] alphaMap = new int[imgW * imgH];
        fillShapeAlpha(alphaMap, imgW, imgH, pad, pad, SHAPE_SIZE, SHAPE_SIZE, SHAPE_ARC);

        alphaMap = blurAlpha(alphaMap, imgW, imgH, hRadius, vRadius,
                config.getBlurType(), config.getSpreadOrChoke());

        BufferedImage shadowImg = colorize(alphaMap, imgW, imgH, config.getColor());

        int sx = cx - halfShape - pad + (int) Math.round(config.getOffsetX());
        int sy = cy - halfShape - pad + (int) Math.round(config.getOffsetY());
        g2.drawImage(shadowImg, sx, sy, null);

        g2.setColor(SHAPE_FILL);
        g2.fill(new RoundRectangle2D.Float(cx - halfShape, cy - halfShape, SHAPE_SIZE, SHAPE_SIZE, SHAPE_ARC, SHAPE_ARC));
        g2.setColor(SHAPE_BORDER);
        g2.draw(new RoundRectangle2D.Float(cx - halfShape, cy - halfShape, SHAPE_SIZE, SHAPE_SIZE, SHAPE_ARC, SHAPE_ARC));

        g2.dispose();
        return result;
    }

    public static BufferedImage renderInnerShadow(EffectConfig config, int previewWidth, int previewHeight) {
        BufferedImage result = createTransparentImage(previewWidth, previewHeight);
        Graphics2D g2 = result.createGraphics();
        setupGraphics(g2);

        int cx = previewWidth / 2;
        int cy = previewHeight / 2;
        int halfShape = SHAPE_SIZE / 2;

        double hRadius = (config.getWidth() - 1) / 2.0;
        double vRadius = (config.getHeight() - 1) / 2.0;
        int pad = (int) Math.ceil(Math.max(hRadius, vRadius)) + 2;
        int maskW = SHAPE_SIZE + pad * 2;
        int maskH = SHAPE_SIZE + pad * 2;

        int[] alphaMap = new int[maskW * maskH];
        Arrays.fill(alphaMap, 255);
        int shapeX = pad + (int) Math.round(config.getOffsetX());
        int shapeY = pad + (int) Math.round(config.getOffsetY());
        clearShapeAlpha(alphaMap, maskW, maskH, shapeX, shapeY, SHAPE_SIZE, SHAPE_SIZE, SHAPE_ARC);

        alphaMap = blurAlpha(alphaMap, maskW, maskH, hRadius, vRadius,
                config.getBlurType(), config.getSpreadOrChoke());

        BufferedImage shadowImg = colorize(alphaMap, maskW, maskH, config.getColor());

        Shape shape = new RoundRectangle2D.Float(cx - halfShape, cy - halfShape,
                SHAPE_SIZE, SHAPE_SIZE, SHAPE_ARC, SHAPE_ARC);

        BufferedImage composited = createTransparentImage(previewWidth, previewHeight);
        Graphics2D cg = composited.createGraphics();
        setupGraphics(cg);
        cg.setColor(SHAPE_FILL);
        cg.fill(shape);
        cg.setComposite(AlphaComposite.SrcAtop);
        cg.drawImage(shadowImg, cx - halfShape - pad, cy - halfShape - pad, null);
        cg.dispose();

        g2.drawImage(composited, 0, 0, null);
        g2.setColor(SHAPE_BORDER);
        g2.draw(shape);

        g2.dispose();
        return result;
    }

    // ==================== Alpha Mask Operations ====================

    private static void fillShapeAlpha(int[] alpha, int w, int h,
                                        int sx, int sy, int sw, int sh, int arc) {
        BufferedImage temp = createTransparentImage(w, h);
        Graphics2D g = temp.createGraphics();
        setupGraphics(g);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, arc, arc));
        g.dispose();
        for (int i = 0; i < w * h; i++) {
            alpha[i] = (temp.getRGB(i % w, i / w) >> 24) & 0xFF;
        }
    }

    private static void clearShapeAlpha(int[] alpha, int w, int h,
                                         int sx, int sy, int sw, int sh, int arc) {
        BufferedImage temp = createTransparentImage(w, h);
        Graphics2D g = temp.createGraphics();
        setupGraphics(g);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, arc, arc));
        g.dispose();
        for (int i = 0; i < w * h; i++) {
            int shapeAlpha = (temp.getRGB(i % w, i / w) >> 24) & 0xFF;
            if (shapeAlpha > 0) {
                alpha[i] = Math.max(0, alpha[i] - shapeAlpha);
            }
        }
    }

    private static BufferedImage colorize(int[] alphaMap, int w, int h, Color color) {
        BufferedImage img = createTransparentImage(w, h);
        int rgb = (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        int colorAlpha = color.getAlpha();
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int a = (alphaMap[i] * colorAlpha) / 255;
            pixels[i] = (a << 24) | rgb;
        }
        img.setRGB(0, 0, w, h, pixels, 0, w);
        return img;
    }

    // ==================== Alpha-Only Blur ====================

    /**
     * Blurs alpha channel and applies spread/choke.
     * <p>
     * Gaussian: spread is baked into kernel weights (matching JavaFX's
     * GaussianRenderState.getGaussianWeights() exactly).
     * <p>
     * Box: spread is applied as post-processing boost after all passes
     * (matching JavaFX's BoxRenderState.validateWeights() logic).
     */
    private static int[] blurAlpha(int[] src, int w, int h,
                                    double hRadius, double vRadius,
                                    String blurType, double spread) {
        if (hRadius < 0.5 && vRadius < 0.5) {
            return src;
        }

        boolean gaussian = EffectConfig.BLUR_GAUSSIAN.equals(blurType);
        int passes = switch (blurType) {
            case EffectConfig.BLUR_ONE_PASS_BOX -> 1;
            case EffectConfig.BLUR_TWO_PASS_BOX -> 2;
            default -> gaussian ? 1 : 3;
        };

        int[] result = Arrays.copyOf(src, src.length);
        int[] temp = new int[src.length];

        if (gaussian) {
            float[] hKernel = hRadius >= 0.5
                    ? createGaussianKernel(hRadius, (float) spread) : null;
            // Spread only applied on one pass (second pass if both exist)
            float[] vKernel = vRadius >= 0.5
                    ? createGaussianKernel(vRadius, hKernel == null ? (float) spread : 0f) : null;

            if (hKernel != null) {
                convolveHorizontalAlpha(result, temp, w, h, hKernel);
                System.arraycopy(temp, 0, result, 0, result.length);
            }
            if (vKernel != null) {
                convolveVerticalAlpha(result, temp, w, h, vKernel);
                System.arraycopy(temp, 0, result, 0, result.length);
            }
        } else {
            float[] hKernel = hRadius >= 0.5 ? createBoxKernel(hRadius, passes) : null;
            float[] vKernel = vRadius >= 0.5 ? createBoxKernel(vRadius, passes) : null;

            for (int pass = 0; pass < passes; pass++) {
                if (hKernel != null) {
                    convolveHorizontalAlpha(result, temp, w, h, hKernel);
                    System.arraycopy(temp, 0, result, 0, result.length);
                }
                if (vKernel != null) {
                    convolveVerticalAlpha(result, temp, w, h, vKernel);
                    System.arraycopy(temp, 0, result, 0, result.length);
                }
            }

            if (spread > 0) {
                applyBoxSpread(result, spread, hKernel, vKernel, passes);
            }
        }

        return result;
    }

    // ==================== Gaussian Kernel (with spread) ====================

    /**
     * Creates a gaussian kernel matching JavaFX's GaussianRenderState.getGaussianWeights().
     * <p>
     * From JavaFX source (GaussianRenderState.java line 76-98):
     * <pre>
     *   sigma = radius / 3
     *   kernelSize = pad * 2 + 1  (where pad = ceil(radius))
     *   total += (edgeWeight - total) * spread
     * </pre>
     */
    private static float[] createGaussianKernel(double radius, float spread) {
        int pad = (int) Math.ceil(radius);
        int klen = pad * 2 + 1;
        if (klen < 1) klen = 1;
        if (klen > 255) klen = 255;

        float[] kernel = new float[klen];
        double sigma = radius / 3.0;
        double sigma22 = 2.0 * sigma * sigma;
        if (sigma22 < Float.MIN_VALUE) {
            sigma22 = Float.MIN_VALUE;
        }

        float total = 0.0f;
        for (int i = 0; i < klen; i++) {
            int row = i - pad;
            float kval = (float) Math.exp(-(row * row) / sigma22);
            kernel[i] = kval;
            total += kval;
        }

        // Spread modifies normalization (JavaFX GaussianRenderState line 88)
        // kernel[0] = edge weight (smallest value in the kernel)
        // When spread > 0: total shrinks → each weight grows → alpha amplified
        // When spread = 0: no change
        // When spread = 1: total = edgeWeight → massive amplification → solid shadow
        total += (kernel[0] - total) * spread;

        for (int i = 0; i < klen; i++) {
            kernel[i] /= total;
        }

        return kernel;
    }

    // ==================== Box Kernel ====================

    /**
     * Creates a box kernel matching JavaFX's BoxShadow internals.
     * <p>
     * From JavaFX source (BoxShadow.java):
     *   setGaussianWidth(w) → horizontalSize = round(w / 3)
     * Generalized: per-pass size = round(gaussianWidth / passes).
     */
    private static float[] createBoxKernel(double radius, int passes) {
        int gaussianWidth = (int) (radius * 2) + 1;
        int size = Math.round((float) gaussianWidth / passes);
        if (size < 1) size = 1;
        if (size % 2 == 0) size++;
        if (size > 255) size = 255;

        float[] kernel = new float[size];
        float weight = 1.0f / size;
        Arrays.fill(kernel, weight);
        return kernel;
    }

    // ==================== Spread / Choke ====================

    /**
     * Applies box blur spread as a post-processing alpha boost.
     * <p>
     * From JavaFX source (BoxRenderState.java line 535):
     *   sum += (1.0 - sum) * passSpread
     * where sum is the un-normalized combined kernel weight sum.
     * <p>
     * Our multi-pass box blur uses normalized kernels (sum=1 per pass),
     * so the combined output is already normalized. To replicate JavaFX's
     * spread, we compute the boost factor from the un-normalized sum:
     *   S = hSize^passes * vSize^passes  (un-normalized combined sum)
     *   adjustedSum = S + (1 - S) * spread
     *   boost = S / adjustedSum
     */
    private static void applyBoxSpread(int[] alphaMap, double spread,
                                        float[] hKernel, float[] vKernel, int passes) {
        if (spread <= 0 || spread > 1) return;

        int hSize = (hKernel != null) ? hKernel.length : 1;
        int vSize = (vKernel != null) ? vKernel.length : 1;

        double s = Math.pow(hSize, passes) * Math.pow(vSize, passes);
        double adjustedSum = s + (1.0 - s) * spread;
        if (adjustedSum < 0.001) adjustedSum = 0.001;
        double boost = s / adjustedSum;

        for (int i = 0; i < alphaMap.length; i++) {
            int boosted = (int) Math.round(alphaMap[i] * boost);
            alphaMap[i] = Math.min(255, boosted);
        }
    }

    // ==================== Convolution ====================

    private static void convolveHorizontalAlpha(int[] src, int[] dst, int w, int h, float[] kernel) {
        int kCenter = kernel.length / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float a = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sx = x + k - kCenter;
                    if (sx >= 0 && sx < w) {
                        a += src[y * w + sx] * kernel[k];
                    }
                }
                dst[y * w + x] = clamp(a);
            }
        }
    }

    private static void convolveVerticalAlpha(int[] src, int[] dst, int w, int h, float[] kernel) {
        int kCenter = kernel.length / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float a = 0;
                for (int k = 0; k < kernel.length; k++) {
                    int sy = y + k - kCenter;
                    if (sy >= 0 && sy < h) {
                        a += src[sy * w + x] * kernel[k];
                    }
                }
                dst[y * w + x] = clamp(a);
            }
        }
    }

    // ==================== Utilities ====================

    private static BufferedImage createTransparentImage(int w, int h) {
        return new BufferedImage(Math.max(w, 1), Math.max(h, 1), BufferedImage.TYPE_INT_ARGB);
    }

    private static void setupGraphics(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}