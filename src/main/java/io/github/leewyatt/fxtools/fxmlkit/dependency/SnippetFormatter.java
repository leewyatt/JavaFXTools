package io.github.leewyatt.fxtools.fxmlkit.dependency;

import io.github.leewyatt.fxtools.util.BuildSystemDetector.BuildSystem;
import io.github.leewyatt.fxtools.util.BuildSystemDetector.GradleDsl;
import org.jetbrains.annotations.NotNull;

/**
 * Formats dependency-coordinate snippets for display in the dialog. Produces
 * Maven XML, Gradle Kotlin/Groovy DSL, and module-info {@code requires} blocks
 * based on the {@link DependencyInsertionContext}.
 */
public final class SnippetFormatter {

    private static final String VERSION_PLACEHOLDER = "{javafx-version}";
    private static final String MAVEN_VERSION_PLACEHOLDER = "${javafx.version}";

    private SnippetFormatter() {
    }

    /**
     * Builds the full dependency snippet for the given context.
     */
    @NotNull
    public static String formatDependencySnippet(@NotNull DependencyInsertionContext ctx) {
        return switch (ctx.getBuildSystem()) {
            case MAVEN -> formatMaven(ctx);
            case GRADLE -> formatGradle(ctx);
            case NONE -> formatMaven(ctx) + "\n\n" + formatGradleKotlin(ctx);
        };
    }

    /**
     * Builds the module-info snippet showing only the missing {@code requires} directives.
     * Returns an empty string when nothing is missing.
     */
    @NotNull
    public static String formatModuleInfoSnippet(@NotNull DependencyInsertionContext ctx) {
        if (!ctx.hasModuleInfo()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (ctx.isRequiresControlsMissing()) {
            sb.append("requires ").append(FxmlKitModuleConstants.JAVAFX_CONTROLS_MODULE)
                    .append(";\n");
        }
        if (ctx.isRequiresFxmlMissing()) {
            sb.append("requires ").append(FxmlKitModuleConstants.JAVAFX_FXML_MODULE)
                    .append(";\n");
        }
        if (ctx.isRequiresFxmlKitMissing()) {
            sb.append("requires ").append(FxmlKitModuleConstants.JPMS_MODULE_NAME)
                    .append(";\n");
        }
        return sb.toString().stripTrailing();
    }

    // ==================== Maven ====================

    @NotNull
    private static String formatMaven(@NotNull DependencyInsertionContext ctx) {
        StringBuilder sb = new StringBuilder();
        String fxVersion = resolveMavenJavafxVersion(ctx);

        if (!ctx.hasControls()) {
            appendMavenDep(sb, FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT,
                    fxVersion, isMavenVersionManaged(ctx, true));
        }
        if (!ctx.hasFxml()) {
            appendMavenDep(sb, FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT,
                    fxVersion, isMavenVersionManaged(ctx, false));
        }
        appendMavenDep(sb, FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID,
                ctx.getFxmlKitVersion(), isFxmlKitManaged(ctx));
        return sb.toString().stripTrailing();
    }

    private static void appendMavenDep(@NotNull StringBuilder sb,
                                       @NotNull String groupId,
                                       @NotNull String artifactId,
                                       @NotNull String version,
                                       boolean managed) {
        if (!sb.isEmpty()) {
            sb.append("\n");
        }
        sb.append("<dependency>\n");
        sb.append("    <groupId>").append(groupId).append("</groupId>\n");
        sb.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
        if (!managed) {
            sb.append("    <version>").append(version).append("</version>");
            if (MAVEN_VERSION_PLACEHOLDER.equals(version)) {
                sb.append("  <!-- replace with your JavaFX version -->");
            }
            sb.append("\n");
        }
        sb.append("</dependency>\n");
    }

    @NotNull
    private static String resolveMavenJavafxVersion(@NotNull DependencyInsertionContext ctx) {
        String v = ctx.getJavafxVersion();
        return v != null ? v : MAVEN_VERSION_PLACEHOLDER;
    }

    private static boolean isMavenVersionManaged(@NotNull DependencyInsertionContext ctx,
                                                  boolean controls) {
        ParentPomInfo parent = ctx.getParentPom();
        if (parent == null) {
            return false;
        }
        return controls ? parent.managesJavafxControls() : parent.managesJavafxFxml();
    }

    private static boolean isFxmlKitManaged(@NotNull DependencyInsertionContext ctx) {
        ParentPomInfo parent = ctx.getParentPom();
        return parent != null && parent.managesFxmlKit();
    }

    // ==================== Gradle ====================

    @NotNull
    private static String formatGradle(@NotNull DependencyInsertionContext ctx) {
        StringBuilder sb = new StringBuilder();

        if (ctx.hasJavaFxGradlePlugin()) {
            boolean missingControls = !ctx.hasControls();
            boolean missingFxml = !ctx.hasFxml();
            if (missingControls && missingFxml) {
                sb.append("// In javafx { } block, add \"javafx.controls\" and \"javafx.fxml\" to modules list\n");
            } else if (missingControls) {
                sb.append("// In javafx { } block, add \"javafx.controls\" to modules list\n");
            } else if (missingFxml) {
                sb.append("// In javafx { } block, add \"javafx.fxml\" to modules list\n");
            }
        }

        if (!ctx.hasJavaFxGradlePlugin()) {
            String fxVersion = resolveGradleJavafxVersion(ctx);
            if (!ctx.hasControls()) {
                appendGradleDep(sb, ctx.getGradleDsl(),
                        FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                        FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT, fxVersion);
            }
            if (!ctx.hasFxml()) {
                appendGradleDep(sb, ctx.getGradleDsl(),
                        FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                        FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT, fxVersion);
            }
        }

        appendGradleDep(sb, ctx.getGradleDsl(),
                FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID, ctx.getFxmlKitVersion());
        return sb.toString().stripTrailing();
    }

    @NotNull
    private static String formatGradleKotlin(@NotNull DependencyInsertionContext ctx) {
        StringBuilder sb = new StringBuilder();
        String fxVersion = resolveGradleJavafxVersion(ctx);

        if (!ctx.hasControls()) {
            appendGradleDep(sb, GradleDsl.KOTLIN,
                    FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_CONTROLS_ARTIFACT, fxVersion);
        }
        if (!ctx.hasFxml()) {
            appendGradleDep(sb, GradleDsl.KOTLIN,
                    FxmlKitModuleConstants.JAVAFX_GROUP_ID,
                    FxmlKitModuleConstants.JAVAFX_FXML_ARTIFACT, fxVersion);
        }
        appendGradleDep(sb, GradleDsl.KOTLIN,
                FxmlKitModuleConstants.GROUP_ID,
                FxmlKitModuleConstants.ARTIFACT_ID, ctx.getFxmlKitVersion());
        return sb.toString().stripTrailing();
    }

    private static void appendGradleDep(@NotNull StringBuilder sb,
                                        @NotNull GradleDsl dsl,
                                        @NotNull String groupId,
                                        @NotNull String artifactId,
                                        @NotNull String version) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append("\n");
        }
        String coord = groupId + ":" + artifactId + ":" + version;
        if (dsl == GradleDsl.GROOVY) {
            sb.append("implementation '").append(coord).append("'\n");
        } else {
            sb.append("implementation(\"").append(coord).append("\")\n");
        }
    }

    @NotNull
    private static String resolveGradleJavafxVersion(@NotNull DependencyInsertionContext ctx) {
        String v = ctx.getJavafxVersion();
        return v != null ? v : VERSION_PLACEHOLDER;
    }
}
