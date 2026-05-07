package io.github.leewyatt.fxtools.generate.completion;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.macro.MacroBase;
import io.github.leewyatt.fxtools.util.FxNamingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Live template macro that converts a property name to a generated constant name.
 */
public class FxConstantNameMacro extends MacroBase {

    /**
     * Creates the {@code fxConstantName} live template macro.
     */
    public FxConstantNameMacro() {
        super("fxConstantName", "fxConstantName(String)");
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
        return new TextResult(FxNamingUtil.toUpperSnakeCase(result.toString()));
    }
}
