package io.github.leewyatt.fxtools.generate;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves CSS StyleConverter expressions for styleable ObjectProperty values.
 */
public final class StyleableConverterResolver {

    private static final Map<String, String> KNOWN_CONVERTERS = Map.ofEntries(
            Map.entry("String", "javafx.css.StyleConverter.getStringConverter()"),
            Map.entry("java.lang.String", "javafx.css.StyleConverter.getStringConverter()"),
            Map.entry("Boolean", "javafx.css.StyleConverter.getBooleanConverter()"),
            Map.entry("java.lang.Boolean", "javafx.css.StyleConverter.getBooleanConverter()"),
            Map.entry("Number", "javafx.css.StyleConverter.getSizeConverter()"),
            Map.entry("java.lang.Number", "javafx.css.StyleConverter.getSizeConverter()"),
            Map.entry("Color", "javafx.css.StyleConverter.getColorConverter()"),
            Map.entry("javafx.scene.paint.Color", "javafx.css.StyleConverter.getColorConverter()"),
            Map.entry("Paint", "javafx.css.StyleConverter.getPaintConverter()"),
            Map.entry("javafx.scene.paint.Paint", "javafx.css.StyleConverter.getPaintConverter()"),
            Map.entry("Duration", "javafx.css.StyleConverter.getDurationConverter()"),
            Map.entry("javafx.util.Duration", "javafx.css.StyleConverter.getDurationConverter()"),
            Map.entry("Font", "javafx.css.StyleConverter.getFontConverter()"),
            Map.entry("javafx.scene.text.Font", "javafx.css.StyleConverter.getFontConverter()"),
            Map.entry("Insets", "javafx.css.StyleConverter.getInsetsConverter()"),
            Map.entry("javafx.geometry.Insets", "javafx.css.StyleConverter.getInsetsConverter()"),
            Map.entry("Effect", "javafx.css.StyleConverter.getEffectConverter()"),
            Map.entry("javafx.scene.effect.Effect", "javafx.css.StyleConverter.getEffectConverter()"),
            Map.entry("Cursor", "javafx.css.converter.CursorConverter.getInstance()"),
            Map.entry("javafx.scene.Cursor", "javafx.css.converter.CursorConverter.getInstance()"),
            Map.entry("Shape", "javafx.css.converter.ShapeConverter.getInstance()"),
            Map.entry("javafx.scene.shape.Shape", "javafx.css.converter.ShapeConverter.getInstance()"),
            Map.entry("Stop", "javafx.css.converter.StopConverter.getInstance()"),
            Map.entry("javafx.scene.paint.Stop", "javafx.css.converter.StopConverter.getInstance()")
    );

    private StyleableConverterResolver() {
    }

    /**
     * Resolves a converter from user-entered generic text.
     *
     * @param genericTypeText the ObjectProperty generic type
     * @param project current project, used to resolve fully-qualified enum types when available
     */
    @NotNull
    public static Result resolveObjectProperty(@NotNull String genericTypeText, @Nullable Project project) {
        String typeText = normalizeTypeText(genericTypeText);
        if (typeText.isEmpty() || "Object".equals(typeText) || "java.lang.Object".equals(typeText)) {
            return Result.todo("Object");
        }

        String knownConverter = KNOWN_CONVERTERS.get(typeText);
        if (knownConverter != null) {
            return new Result(typeText, knownConverter);
        }

        PsiClass psiClass = findClass(project, typeText);
        if (psiClass != null && psiClass.isEnum()) {
            String qualifiedName = psiClass.getQualifiedName();
            String enumType = qualifiedName != null ? qualifiedName : typeText;
            return enumResult(enumType);
        }

        return Result.todo(typeText);
    }

    /**
     * Resolves a converter from the ObjectProperty generic PSI type.
     *
     * @param genericType the ObjectProperty generic type
     */
    @NotNull
    public static Result resolveObjectProperty(@Nullable PsiType genericType) {
        if (genericType == null) {
            return Result.todo("Object");
        }

        String typeText = normalizeTypeText(genericType.getCanonicalText());
        if (typeText.isEmpty() || "java.lang.Object".equals(typeText)) {
            return Result.todo("Object");
        }

        String knownConverter = KNOWN_CONVERTERS.get(typeText);
        if (knownConverter != null) {
            return new Result(typeText, knownConverter);
        }

        if (genericType instanceof PsiClassType) {
            PsiClass psiClass = ((PsiClassType) genericType).resolve();
            if (psiClass != null && psiClass.isEnum()) {
                String qualifiedName = psiClass.getQualifiedName();
                return enumResult(qualifiedName != null ? qualifiedName : typeText);
            }
        }

        return Result.todo(typeText);
    }

    @Nullable
    private static PsiClass findClass(@Nullable Project project, @NotNull String typeText) {
        if (project == null || !typeText.contains(".")) {
            return null;
        }
        return JavaPsiFacade.getInstance(project).findClass(typeText, GlobalSearchScope.allScope(project));
    }

    @NotNull
    private static Result enumResult(@NotNull String enumType) {
        return new Result(enumType, "javafx.css.StyleConverter.getEnumConverter(" + enumType + ".class)");
    }

    @NotNull
    private static String normalizeTypeText(@NotNull String typeText) {
        String trimmed = typeText.trim();
        if (trimmed.startsWith("? extends ")) {
            return trimmed.substring("? extends ".length()).trim();
        }
        if (trimmed.startsWith("? super ")) {
            return trimmed.substring("? super ".length()).trim();
        }
        return trimmed;
    }

    /**
     * CSS value type and converter expression used in generated CssMetaData code.
     *
     * @param cssValueType type parameter for CssMetaData and StyleableProperty
     * @param converterExpression expression passed to the CssMetaData constructor
     */
    public record Result(@NotNull String cssValueType, @NotNull String converterExpression) {

        @NotNull
        private static Result todo(@NotNull String cssValueType) {
            return new Result(cssValueType, FxPropertyType.TODO_STYLE_CONVERTER_EXPRESSION);
        }
    }
}
