package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.GradleDsl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserts Gradle dependency lines into the current module's build script.
 * <p>
 * Uses document-level text manipulation because the project does not depend on
 * the IntelliJ Gradle plugin (Groovy/Kotlin PSI unavailable). Insertions are
 * appended at the end of the {@code dependencies { }} block.
 * <p>
 * Must be called inside a {@code WriteCommandAction}.
 */
public final class GradleDependencyInserter {

    private static final Logger LOG = Logger.getInstance(GradleDependencyInserter.class);

    // Note: brace matching is not quote/comment-aware — a known limitation of
    // text-based editing. Exotic build scripts may need manual intervention (Copy path).
    private static final Pattern DEPENDENCIES_BLOCK =
            Pattern.compile("^\\s*dependencies\\s*\\{", Pattern.MULTILINE);

    private GradleDependencyInserter() {
    }

    /**
     * Inserts dependency lines into the module's build script.
     */
    public static void insert(@NotNull DependencyInsertionContext ctx) {
        Module module = ctx.getModule();
        if (module == null) {
            return;
        }

        VirtualFile buildScript = findModuleBuildScript(module);
        if (buildScript == null) {
            throw new IllegalStateException(
                    "Cannot find build.gradle(.kts) for module: " + module.getName());
        }

        PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(buildScript);
        if (psiFile == null) {
            return;
        }

        Document doc = PsiDocumentManager.getInstance(module.getProject()).getDocument(psiFile);
        if (doc == null) {
            return;
        }

        List<String> lines = buildDependencyLines(ctx);
        if (lines.isEmpty()) {
            return;
        }

        PsiDocumentManager docManager = PsiDocumentManager.getInstance(module.getProject());
        String text = doc.getText();
        int insertOffset = findDependenciesBlockEnd(text);
        if (insertOffset < 0) {
            appendNewDependenciesBlock(doc, text, lines, ctx.getGradleDsl(), docManager);
            return;
        }

        StringBuilder insertion = new StringBuilder();
        for (String line : lines) {
            insertion.append("    ").append(line).append("\n");
        }
        doc.insertString(insertOffset, insertion.toString());
        docManager.commitDocument(doc);
    }

    // ==================== Line building ====================

    @NotNull
    private static List<String> buildDependencyLines(@NotNull DependencyInsertionContext ctx) {
        List<String> lines = new ArrayList<>();
        GradleDsl dsl = ctx.getGradleDsl();
        String fxVersion = ctx.getJavafxVersion();

        if (!ctx.hasJavaFxGradlePlugin()) {
            if (!ctx.hasControls()) {
                if (fxVersion != null) {
                    lines.add(formatImplLine(dsl,
                            FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                            FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT, fxVersion));
                } else {
                    LOG.warn("Skipping javafx-controls insertion: JavaFX version is null");
                }
            }
            if (!ctx.hasFxml()) {
                if (fxVersion != null) {
                    lines.add(formatImplLine(dsl,
                            FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                            FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT, fxVersion));
                } else {
                    LOG.warn("Skipping javafx-fxml insertion: JavaFX version is null");
                }
            }
        }

        lines.add(formatImplLine(dsl,
                FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID, ctx.getFxmlKitVersion()));
        return lines;
    }

    @NotNull
    private static String formatImplLine(@NotNull GradleDsl dsl,
                                          @NotNull String groupId,
                                          @NotNull String artifactId,
                                          @NotNull String version) {
        String coord = groupId + ":" + artifactId + ":" + version;
        if (dsl == GradleDsl.GROOVY) {
            return "implementation '" + coord + "'";
        }
        return "implementation(\"" + coord + "\")";
    }

    // ==================== Block location ====================

    private static int findDependenciesBlockEnd(@NotNull String text) {
        Matcher m = DEPENDENCIES_BLOCK.matcher(text);
        if (!m.find()) {
            return -1;
        }

        int braceStart = m.end() - 1;
        int depth = 0;
        for (int i = braceStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void appendNewDependenciesBlock(@NotNull Document doc,
                                                    @NotNull String text,
                                                    @NotNull List<String> lines,
                                                    @NotNull GradleDsl dsl,
                                                    @NotNull PsiDocumentManager docManager) {
        StringBuilder block = new StringBuilder("\n\ndependencies {\n");
        for (String line : lines) {
            block.append("    ").append(line).append("\n");
        }
        block.append("}\n");
        doc.insertString(text.length(), block.toString());
        docManager.commitDocument(doc);
    }

    // ==================== File resolution ====================

    @Nullable
    private static VirtualFile findModuleBuildScript(@NotNull Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        for (VirtualFile root : roots) {
            VirtualFile kts = root.findChild("build.gradle.kts");
            if (kts != null) {
                return kts;
            }
            VirtualFile groovy = root.findChild("build.gradle");
            if (groovy != null) {
                return groovy;
            }
        }
        return null;
    }
}
