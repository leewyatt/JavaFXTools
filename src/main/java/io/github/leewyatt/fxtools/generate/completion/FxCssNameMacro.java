package io.github.leewyatt.fxtools.generate.completion;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.macro.MacroBase;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Live Template macro that converts a camelCase property name to -fx-kebab-case CSS name.
 */
public class FxCssNameMacro extends MacroBase {

    /**
     * Creates the {@code fxCssName} live template macro.
     */
    public FxCssNameMacro() {
        super("fxCssName", "fxCssName(String)");
    }

    @Override
    protected @Nullable Result calculateResult(Expression @NotNull [] params,
                                               ExpressionContext context, boolean quick) {
        if (params.length == 0) {
            return null;
        }
        Result result = params[0].calculateResult(context);
        if (result == null) {
            return null;
        }
        String name = result.toString();
        if (name.isEmpty()) {
            return new TextResult("-fx-");
        }
        return new TextResult(FxNamingUtil.toFxKebabCase(name));
    }
}
