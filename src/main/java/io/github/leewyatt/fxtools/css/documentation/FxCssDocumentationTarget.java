package io.github.leewyatt.fxtools.css.documentation;

import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.css.FxCssPropertyTable;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Documentation target for a resolved CSS token (a {@code -fx-*} property or a
 * custom CSS variable). Holds an immutable snapshot of (project, token) captured
 * at the cursor position where Quick Documentation was triggered.
 *
 * <p>This is the replacement for the legacy {@code AbstractDocumentationProvider}
 * flow where the token was computed in {@code getCustomDocumentationElement} and
 * stashed in a field. The new API passes the resolved state through the target
 * object, which is stateless per-request.</p>
 */
final class FxCssDocumentationTarget implements DocumentationTarget {

    private final Project project;
    private final String token;

    FxCssDocumentationTarget(@NotNull Project project, @NotNull String token) {
        this.project = project;
        this.token = token;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        // Immutable target — hard pointer is safe and cheap.
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(token).presentation();
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        FxCssPropertyTable.PropertyInfo info = FxCssPropertyTable.getProperty(token, project);
        if (info != null) {
            return info.getName() + ": " + info.getValueType() + " (" + info.getAppliesTo() + ")";
        }
        return null;
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        FxCssPropertyTable.PropertyInfo info = FxCssPropertyTable.getProperty(token, project);
        if (info != null) {
            return DocumentationResult.documentation(generatePropertyDoc(info));
        }
        String variableDoc = generateVariableDoc(token, project);
        return variableDoc != null ? DocumentationResult.documentation(variableDoc) : null;
    }

    // ==================== HTML generation ====================

    @NotNull
    private static String generatePropertyDoc(@NotNull FxCssPropertyTable.PropertyInfo info) {
        List<FxCssPropertyTable.SourceEntry> sources = info.getSources();
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='definition'><pre>").append(escape(info.getName())).append("</pre></div>");
        sb.append("<div class='content'>");

        if (sources.size() == 1) {
            FxCssPropertyTable.SourceEntry src = sources.get(0);
            sb.append("<table>");
            sb.append("<tr><td><b>Type:</b></td><td>").append(escape(src.getValueType())).append("</td></tr>");
            sb.append("<tr><td><b>Default:</b></td><td>").append(escape(src.getDefaultValue())).append("</td></tr>");
            sb.append("<tr><td><b>Applies to:</b></td><td>").append(escape(src.getAppliesTo())).append("</td></tr>");
            if (src.getLibrary() != null) {
                sb.append("<tr><td><b>Library:</b></td><td>").append(escape(src.getLibrary())).append("</td></tr>");
            }
            sb.append("</table>");
            sb.append("<p>").append(escape(src.getDescription())).append("</p>");
            if (src.getExample() != null && !src.getExample().isEmpty()) {
                sb.append("<p><b>Example:</b></p>");
                sb.append("<pre>").append(escape(src.getExample())).append("</pre>");
            }
        } else {
            for (int i = 0; i < sources.size(); i++) {
                FxCssPropertyTable.SourceEntry src = sources.get(i);
                if (i > 0) {
                    sb.append("<hr>");
                }
                String heading = src.getLibrary() != null ? src.getLibrary() : "JavaFX";
                sb.append("<p><b>").append(escape(heading)).append("</b></p>");
                sb.append("<table>");
                sb.append("<tr><td><b>Type:</b></td><td>").append(escape(src.getValueType())).append("</td></tr>");
                sb.append("<tr><td><b>Default:</b></td><td>").append(escape(src.getDefaultValue())).append("</td></tr>");
                sb.append("<tr><td><b>Applies to:</b></td><td>").append(escape(src.getAppliesTo())).append("</td></tr>");
                sb.append("</table>");
                sb.append("<p>").append(escape(src.getDescription())).append("</p>");
                if (src.getExample() != null && !src.getExample().isEmpty()) {
                    sb.append("<p><b>Example:</b></p>");
                    sb.append("<pre>").append(escape(src.getExample())).append("</pre>");
                }
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    @Nullable
    private static String generateVariableDoc(@NotNull String variableName, @NotNull Project project) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Collection<VirtualFile> files = FxCssPropertyIndex.findFilesDefiningProperty(
                variableName, project, scope);
        if (files.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='definition'><pre>").append(escape(variableName)).append("</pre></div>");
        sb.append("<div class='content'>");
        sb.append("<p><b>").append(escape(FxToolsBundle.message("css.doc.custom.property"))).append("</b></p>");
        sb.append("<table>");

        for (VirtualFile file : files) {
            String rawValue = FxCssPropertyIndex.getPropertyValue(variableName, file, project);
            if (rawValue == null) {
                continue;
            }
            for (String val : rawValue.split("\n")) {
                String trimmed = val.trim();
                if (!trimmed.isEmpty()) {
                    sb.append("<tr><td>").append(escape(file.getName())).append("</td>");
                    sb.append("<td><code>").append(escape(trimmed)).append("</code></td></tr>");
                }
            }
        }

        sb.append("</table>");
        sb.append("</div>");
        return sb.toString();
    }

    @NotNull
    private static String escape(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
