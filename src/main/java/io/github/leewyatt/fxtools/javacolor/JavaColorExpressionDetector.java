package io.github.leewyatt.fxtools.javacolor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import io.github.leewyatt.fxtools.css.FxNamedColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Set;

/**
 * PSI-level recognizer: given an arbitrary {@link PsiIdentifier} leaf, returns
 * a {@link JavaColorExpression} if the surrounding expression refers to
 * {@code javafx.scene.paint.Color} in one of the recognized shapes.
 *
 * <p>To avoid {@code resolve()} traffic on every Java identifier, the entry
 * point first does a leaf-text prefilter (must be in the candidate name set)
 * and a parent-shape check before calling {@code resolve()}.</p>
 */
public final class JavaColorExpressionDetector {

    public static final String COLOR_FQN = "javafx.scene.paint.Color";

    /** Factory / web method names that we recognize. */
    private static final Set<String> METHOD_NAMES = Set.of(
            "color", "rgb", "hsb", "gray", "grayRgb", "web", "valueOf");

    private JavaColorExpressionDetector() {
    }

    /**
     * Quick prefilter: returns {@code true} if the identifier's text is a name
     * we might want to inspect further. Used so that {@code resolve()} is only
     * called for promising candidates.
     */
    public static boolean isCandidateIdentifier(@NotNull PsiIdentifier identifier) {
        String text = identifier.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (METHOD_NAMES.contains(text)) {
            return true;
        }
        if ("Color".equals(text)) {
            return true; // possible constructor
        }
        return FxNamedColorsReverseIndex.allUpperCaseNames().contains(text);
    }

    /**
     * Resolves the given identifier to a {@link JavaColorExpression} or returns
     * {@code null} if the surrounding expression does not match one of the
     * recognized shapes.
     */
    @Nullable
    public static JavaColorExpression detect(@NotNull PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiReferenceExpression refExpr) {
            if (refExpr.getReferenceNameElement() != identifier) {
                return null;
            }
            PsiElement grandparent = refExpr.getParent();
            if (grandparent instanceof PsiMethodCallExpression call
                    && call.getMethodExpression() == refExpr) {
                return detectMethodCall(call, identifier, refExpr);
            }
            return detectNamedConstant(identifier, refExpr);
        }
        if (parent instanceof PsiJavaCodeReferenceElement codeRef
                && "Color".equals(identifier.getText())) {
            return detectConstructor(codeRef, identifier);
        }
        return null;
    }

    // ==================== Named constant ====================

    @Nullable
    private static JavaColorExpression detectNamedConstant(@NotNull PsiIdentifier identifier,
                                                            @NotNull PsiReferenceExpression refExpr) {
        if (!FxNamedColorsReverseIndex.allUpperCaseNames().contains(identifier.getText())) {
            return null;
        }
        // Require a qualifier (e.g. Color.RED, javafx.scene.paint.Color.RED).
        PsiExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier == null) {
            return null;
        }
        PsiElement resolved = refExpr.resolve();
        if (!(resolved instanceof PsiField field)) {
            return null;
        }
        PsiClass owner = field.getContainingClass();
        if (owner == null || !COLOR_FQN.equals(owner.getQualifiedName())) {
            return null;
        }
        Color c = FxNamedColors.getColor(identifier.getText());
        if (c == null) {
            return null;
        }
        return new JavaColorExpression.NamedConstant(
                refExpr, identifier, qualifier.getText(), identifier.getText(), c);
    }

    // ==================== new Color(...) ====================

    @Nullable
    private static JavaColorExpression detectConstructor(@NotNull PsiJavaCodeReferenceElement codeRef,
                                                          @NotNull PsiIdentifier identifier) {
        // Walk up nested qualified references (e.g. javafx.scene.paint.Color)
        PsiJavaCodeReferenceElement top = codeRef;
        while (top.getParent() instanceof PsiJavaCodeReferenceElement parentRef
                && parentRef.getQualifier() == top) {
            top = parentRef;
        }
        if (!(top.getParent() instanceof PsiNewExpression newExpr)) {
            return null;
        }
        if (newExpr.getClassReference() != top) {
            return null;
        }
        PsiExpressionList argList = newExpr.getArgumentList();
        if (argList == null) {
            return null;
        }
        PsiExpression[] args = argList.getExpressions();
        if (args.length != 4) {
            return null;
        }
        PsiElement resolved = top.resolve();
        if (!(resolved instanceof PsiClass cls) || !COLOR_FQN.equals(cls.getQualifiedName())) {
            return null;
        }
        double[] vals = new double[4];
        for (int i = 0; i < 4; i++) {
            Double d = evalDouble(args[i]);
            if (d == null || !inDoubleRange01(d)) {
                return null;
            }
            vals[i] = d;
        }
        String[] texts = textsOf(args);
        Color color = colorFromDoubles(vals[0], vals[1], vals[2], vals[3]);
        if (color == null) {
            return null;
        }
        return new JavaColorExpression.Constructor(
                newExpr, identifier, top.getText(), texts, vals, color);
    }

    // ==================== Color.<method>(...) ====================

    @Nullable
    private static JavaColorExpression detectMethodCall(@NotNull PsiMethodCallExpression call,
                                                         @NotNull PsiIdentifier methodIdent,
                                                         @NotNull PsiReferenceExpression methodRef) {
        String name = methodIdent.getText();
        PsiExpression qualifier = methodRef.getQualifierExpression();
        if (qualifier == null) {
            return null;
        }
        // Cheap text prefilter to avoid resolveMethod() on the many
        // String.valueOf / Integer.valueOf / etc. call sites.
        if (!qualifier.getText().contains("Color")) {
            return null;
        }
        PsiMethod method = call.resolveMethod();
        if (method == null) {
            return null;
        }
        PsiClass owner = method.getContainingClass();
        if (owner == null || !COLOR_FQN.equals(owner.getQualifiedName())) {
            return null;
        }
        PsiExpression[] args = call.getArgumentList().getExpressions();
        return switch (name) {
            case "color" -> detectColorFactory(call, methodIdent, qualifier.getText(), args);
            case "rgb" -> detectRgbFactory(call, methodIdent, qualifier.getText(), args);
            case "hsb" -> detectHsbFactory(call, methodIdent, qualifier.getText(), args);
            case "gray" -> detectGrayFactory(call, methodIdent, qualifier.getText(), args);
            case "grayRgb" -> detectGrayRgbFactory(call, methodIdent, qualifier.getText(), args);
            case "web", "valueOf" -> detectWebOrValueOf(call, methodIdent, qualifier.getText(), name, args);
            default -> null;
        };
    }

    @Nullable
    private static JavaColorExpression detectColorFactory(@NotNull PsiMethodCallExpression call,
                                                           @NotNull PsiIdentifier ident,
                                                           @NotNull String qualifier,
                                                           @NotNull PsiExpression[] args) {
        if (args.length != 3 && args.length != 4) {
            return null;
        }
        double[] vals = new double[args.length];
        for (int i = 0; i < args.length; i++) {
            Double d = evalDouble(args[i]);
            if (d == null || !inDoubleRange01(d)) {
                return null;
            }
            vals[i] = d;
        }
        double alpha = vals.length == 4 ? vals[3] : 1.0;
        Color color = colorFromDoubles(vals[0], vals[1], vals[2], alpha);
        if (color == null) {
            return null;
        }
        return new JavaColorExpression.ColorFactory(
                call, ident, qualifier, textsOf(args), vals, color);
    }

    @Nullable
    private static JavaColorExpression detectRgbFactory(@NotNull PsiMethodCallExpression call,
                                                         @NotNull PsiIdentifier ident,
                                                         @NotNull String qualifier,
                                                         @NotNull PsiExpression[] args) {
        if (args.length != 3 && args.length != 4) {
            return null;
        }
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            Integer iv = evalInt(args[i]);
            if (iv == null || iv < 0 || iv > 255) {
                return null;
            }
            rgb[i] = iv;
        }
        double alpha = 1.0;
        if (args.length == 4) {
            Double d = evalDouble(args[3]);
            if (d == null || !inDoubleRange01(d)) {
                return null;
            }
            alpha = d;
        }
        Color color = new Color(rgb[0], rgb[1], rgb[2], clampByte(Math.round(alpha * 255.0)));
        return new JavaColorExpression.RgbFactory(
                call, ident, qualifier, textsOf(args), rgb, alpha, color);
    }

    @Nullable
    private static JavaColorExpression detectHsbFactory(@NotNull PsiMethodCallExpression call,
                                                         @NotNull PsiIdentifier ident,
                                                         @NotNull String qualifier,
                                                         @NotNull PsiExpression[] args) {
        if (args.length != 3 && args.length != 4) {
            return null;
        }
        Double h = evalDouble(args[0]);
        Double s = evalDouble(args[1]);
        Double b = evalDouble(args[2]);
        if (h == null || s == null || b == null) {
            return null;
        }
        // JavaFX checkSB requires S and B in [0,1]; H has no range check.
        if (!inDoubleRange01(s) || !inDoubleRange01(b)) {
            return null;
        }
        double alpha = 1.0;
        if (args.length == 4) {
            Double a = evalDouble(args[3]);
            if (a == null || !inDoubleRange01(a)) {
                return null;
            }
            alpha = a;
        }
        Color color = hsbToColor(h, s, b, alpha);
        return new JavaColorExpression.HsbFactory(
                call, ident, qualifier, args.length, color);
    }

    @Nullable
    private static JavaColorExpression detectGrayFactory(@NotNull PsiMethodCallExpression call,
                                                          @NotNull PsiIdentifier ident,
                                                          @NotNull String qualifier,
                                                          @NotNull PsiExpression[] args) {
        if (args.length != 1 && args.length != 2) {
            return null;
        }
        double[] vals = new double[args.length];
        for (int i = 0; i < args.length; i++) {
            Double d = evalDouble(args[i]);
            if (d == null || !inDoubleRange01(d)) {
                return null;
            }
            vals[i] = d;
        }
        double g = vals[0];
        double alpha = vals.length == 2 ? vals[1] : 1.0;
        Color color = colorFromDoubles(g, g, g, alpha);
        if (color == null) {
            return null;
        }
        return new JavaColorExpression.GrayFactory(
                call, ident, qualifier, textsOf(args), vals, color);
    }

    @Nullable
    private static JavaColorExpression detectGrayRgbFactory(@NotNull PsiMethodCallExpression call,
                                                             @NotNull PsiIdentifier ident,
                                                             @NotNull String qualifier,
                                                             @NotNull PsiExpression[] args) {
        if (args.length != 1 && args.length != 2) {
            return null;
        }
        Integer g = evalInt(args[0]);
        if (g == null || g < 0 || g > 255) {
            return null;
        }
        double alpha = 1.0;
        if (args.length == 2) {
            Double d = evalDouble(args[1]);
            if (d == null || !inDoubleRange01(d)) {
                return null;
            }
            alpha = d;
        }
        Color color = new Color(g, g, g, clampByte(Math.round(alpha * 255.0)));
        return new JavaColorExpression.GrayRgbFactory(
                call, ident, qualifier, textsOf(args), g, alpha, color);
    }

    @Nullable
    private static JavaColorExpression detectWebOrValueOf(@NotNull PsiMethodCallExpression call,
                                                           @NotNull PsiIdentifier ident,
                                                           @NotNull String qualifier,
                                                           @NotNull String methodName,
                                                           @NotNull PsiExpression[] args) {
        if (args.length == 0 || args.length > 2) {
            return null;
        }
        if (args.length == 2 && !"web".equals(methodName)) {
            // valueOf has no 2-arg variant.
            return null;
        }
        if (!(args[0] instanceof PsiLiteralExpression literal)) {
            return null;
        }
        if (!(literal.getValue() instanceof String s) || s.isEmpty()) {
            return null;
        }
        if (literal.isTextBlock()) {
            return null;
        }

        if (args.length == 2) {
            Double opacity = evalDouble(args[1]);
            if (opacity == null || !inDoubleRange01(opacity)) {
                return null;
            }
            JavaFxWebStringParser.Result parsed = JavaFxWebStringParser.parse(s, opacity);
            if (parsed == null) {
                return null;
            }
            return new JavaColorExpression.WebTwoArg(
                    call, ident, qualifier, literal, parsed.color);
        }

        JavaFxWebStringParser.Result parsed = JavaFxWebStringParser.parse(s);
        if (parsed == null) {
            return null;
        }
        return switch (parsed.subformat) {
            case HEX_HASH, HEX_0X, HEX_BARE -> new JavaColorExpression.WebHex(
                    call, ident, qualifier, methodName, literal,
                    parsed.hexPrefix, parsed.hexLength, parsed.hexUpperCase, parsed.color);
            case NAMED -> new JavaColorExpression.WebNamed(
                    call, ident, qualifier, methodName, literal,
                    parsed.namedOriginal, parsed.color);
            case FUNC_RGB, FUNC_RGBA, FUNC_HSL, FUNC_HSLA -> new JavaColorExpression.WebFunctional(
                    call, ident, qualifier, methodName, literal,
                    parsed.subformat, parsed.rgbStyles, parsed.rgbTokens, parsed.rgbBytes,
                    parsed.color);
        };
    }

    // ==================== Helpers ====================

    @Nullable
    private static Double evalDouble(@NotNull PsiExpression expr) {
        Project project = expr.getProject();
        PsiConstantEvaluationHelper helper =
                JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        Object val = helper.computeConstantExpression(expr);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    @Nullable
    private static Integer evalInt(@NotNull PsiExpression expr) {
        Project project = expr.getProject();
        PsiConstantEvaluationHelper helper =
                JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        Object val = helper.computeConstantExpression(expr);
        if (val instanceof Number n) {
            // Reject floating types — Color.rgb signatures take int.
            PsiType type = expr.getType();
            if (type != null && (type.equalsToText("double") || type.equalsToText("float"))) {
                return null;
            }
            long lv = n.longValue();
            if (lv < Integer.MIN_VALUE || lv > Integer.MAX_VALUE) {
                return null;
            }
            return (int) lv;
        }
        return null;
    }

    private static boolean inDoubleRange01(double d) {
        return d >= 0.0 && d <= 1.0;
    }

    @NotNull
    private static String[] textsOf(@NotNull PsiExpression[] args) {
        String[] out = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i].getText();
        }
        return out;
    }

    @Nullable
    private static Color colorFromDoubles(double r, double g, double b, double a) {
        try {
            return new Color(
                    (float) r, (float) g, (float) b, (float) a);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @NotNull
    private static Color hsbToColor(double h, double s, double b, double a) {
        int rgbInt = Color.HSBtoRGB((float) (h / 360.0), (float) s, (float) b);
        return new Color(
                (rgbInt >> 16) & 0xFF, (rgbInt >> 8) & 0xFF, rgbInt & 0xFF,
                clampByte(Math.round(a * 255.0)));
    }

    private static int clampByte(long v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
