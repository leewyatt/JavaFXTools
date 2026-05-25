package io.github.leewyatt.fxtools.javacolor;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.Color;

/**
 * A recognized {@code javafx.scene.paint.Color} expression in Java source, in
 * one of the nine shapes documented in {@code JAVA_COLOR_PREVIEW_RESEARCH.md §3}.
 *
 * <p>Each variant carries enough context to:</p>
 * <ul>
 *   <li>Anchor the gutter icon on a single identifier leaf ({@link #anchor()}).</li>
 *   <li>Locate the full expression range for write-back ({@link #replaceRange()}).</li>
 *   <li>Compute the AWT color for the gutter preview ({@link #color()}).</li>
 *   <li>Drive the formatter that produces author-friendly replacement text.</li>
 * </ul>
 */
public sealed interface JavaColorExpression
        permits JavaColorExpression.NamedConstant,
                JavaColorExpression.Constructor,
                JavaColorExpression.ColorFactory,
                JavaColorExpression.RgbFactory,
                JavaColorExpression.HsbFactory,
                JavaColorExpression.GrayFactory,
                JavaColorExpression.GrayRgbFactory,
                JavaColorExpression.WebHex,
                JavaColorExpression.WebNamed,
                JavaColorExpression.WebFunctional,
                JavaColorExpression.WebTwoArg {

    @NotNull Color color();

    @NotNull PsiIdentifier anchor();

    @NotNull TextRange replaceRange();

    @NotNull PsiElement sourceElement();

    /** {@code Color.RED} and friends. */
    record NamedConstant(
            @NotNull PsiReferenceExpression refExpr,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull String originalName,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return refExpr.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return refExpr; }
    }

    /** {@code new Color(r,g,b,a)}. Always 4 args (only public constructor). */
    record Constructor(
            @NotNull PsiNewExpression newExpr,
            @NotNull PsiIdentifier anchor,
            @NotNull String classRefText,
            @NotNull @Unmodifiable String[] argTexts,
            @NotNull @Unmodifiable double[] argValues,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return newExpr.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return newExpr; }
    }

    /** {@code Color.color(r,g,b[,a])} — all doubles. */
    record ColorFactory(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull @Unmodifiable String[] argTexts,
            @NotNull @Unmodifiable double[] argValues,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
        public int arity() { return argTexts.length; }
    }

    /** {@code Color.rgb(r,g,b[,a])} — RGB int, alpha double. */
    record RgbFactory(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull @Unmodifiable String[] argTexts,
            @NotNull @Unmodifiable int[] rgb,           // length 3
            double alpha,                                // 1.0 when 3-arg
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
        public int arity() { return argTexts.length; }
    }

    /**
     * {@code Color.hsb(h,s,b[,a])} — all doubles.
     *
     * <p>Intentionally does <b>not</b> carry {@code argTexts}/{@code argValues}
     * (unlike the RGB/color/gray factories): RGB↔HSB is not a per-component
     * mapping, so a single channel edit perturbs all three values. The writer
     * always rewrites the whole {@code hsb(...)} group from the new color
     * (research doc §4.1).</p>
     */
    record HsbFactory(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            int arity,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
    }

    /** {@code Color.gray(g[,a])} — all doubles. */
    record GrayFactory(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull @Unmodifiable String[] argTexts,
            @NotNull @Unmodifiable double[] argValues,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
        public int arity() { return argTexts.length; }
    }

    /** {@code Color.grayRgb(g[,a])} — gray int, alpha double. */
    record GrayRgbFactory(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull @Unmodifiable String[] argTexts,
            int gray,
            double alpha,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
        public int arity() { return argTexts.length; }
    }

    /** {@code Color.web("#abc")} / {@code Color.valueOf("#abc")} hex variants. */
    record WebHex(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull String methodName,            // "web" or "valueOf"
            @NotNull PsiLiteralExpression literal,
            @NotNull JavaFxWebStringParser.HexPrefix prefix,
            int hexLength,
            boolean upperCase,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
    }

    /** {@code Color.web("orange")} / {@code Color.valueOf("orange")} named variants. */
    record WebNamed(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull String methodName,
            @NotNull PsiLiteralExpression literal,
            @NotNull String originalName,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
    }

    /**
     * {@code Color.web("rgb/rgba/hsl/hsla(...)")} functional variants.
     *
     * <p>{@code rgbTokens} and {@code rgbBytes} are populated for RGB/RGBA
     * sub-formats only; they let the writer detect "this component is unchanged"
     * by byte equality and reuse the original token text verbatim. They are
     * {@code null} for HSL/HSLA where RGB↔HSB conversion is lossy and any RGB
     * edit perturbs all three HSB components.</p>
     */
    record WebFunctional(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull String methodName,
            @NotNull PsiLiteralExpression literal,
            @NotNull JavaFxWebStringParser.Subformat subformat,
            @Nullable JavaFxWebStringParser.@Unmodifiable ComponentStyle[] rgbStyles,
            @Nullable @Unmodifiable String[] rgbTokens,
            @Nullable @Unmodifiable int[] rgbBytes,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
    }

    /** {@code Color.web(s, opacity)} two-arg fallback form. */
    record WebTwoArg(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiIdentifier anchor,
            @NotNull String qualifierText,
            @NotNull PsiLiteralExpression literal,
            @NotNull Color color) implements JavaColorExpression {
        @Override public @NotNull TextRange replaceRange() { return call.getTextRange(); }
        @Override public @NotNull PsiElement sourceElement() { return call; }
    }
}
