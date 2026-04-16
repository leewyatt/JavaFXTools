package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.module.Module;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiRequiresStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends missing {@code requires} directives to the main source set's
 * {@code module-info.java}. Must be called inside a {@code WriteCommandAction}.
 */
public final class ModuleInfoUpdater {

    private ModuleInfoUpdater() {
    }

    /**
     * Adds missing {@code requires} directives according to the context.
     */
    public static void update(@NotNull DependencyInsertionContext ctx) {
        Module module = ctx.getModule();
        if (module == null || !ctx.hasModuleInfo()) {
            return;
        }

        PsiJavaModule descriptor = FxmlKitModuleConstants.findMainModuleDescriptor(module);
        if (descriptor == null) {
            return;
        }

        List<String> toAdd = new ArrayList<>();
        if (ctx.isRequiresControlsMissing()) {
            toAdd.add(FxmlKitModuleConstants.JAVAFX_CONTROLS_MODULE);
        }
        if (ctx.isRequiresFxmlMissing()) {
            toAdd.add(FxmlKitModuleConstants.JAVAFX_FXML_MODULE);
        }
        if (ctx.isRequiresFxmlKitMissing()) {
            toAdd.add(FxmlKitModuleConstants.JPMS_MODULE_NAME);
        }

        if (toAdd.isEmpty()) {
            return;
        }

        PsiRequiresStatement lastRequires = findLastRequires(descriptor);

        for (String moduleName : toAdd) {
            PsiRequiresStatement stmt = createRequiresStatement(module, moduleName);
            if (stmt == null) {
                continue;
            }
            if (lastRequires != null) {
                lastRequires = (PsiRequiresStatement) descriptor.addAfter(stmt, lastRequires);
            } else {
                descriptor.add(stmt);
            }
        }
    }

    @Nullable
    private static PsiRequiresStatement createRequiresStatement(@NotNull Module module,
                                                                 @NotNull String moduleName) {
        String text = "module _dummy { requires " + moduleName + "; }";
        PsiFile dummy = PsiFileFactory.getInstance(module.getProject())
                .createFileFromText("module-info.java", JavaLanguage.INSTANCE, text);
        if (dummy instanceof PsiJavaFile javaFile) {
            PsiJavaModule dummyModule = javaFile.getModuleDeclaration();
            if (dummyModule != null) {
                for (PsiRequiresStatement req : dummyModule.getRequires()) {
                    return req;
                }
            }
        }
        return null;
    }

    @Nullable
    private static PsiRequiresStatement findLastRequires(@NotNull PsiJavaModule descriptor) {
        PsiRequiresStatement last = null;
        for (PsiRequiresStatement req : descriptor.getRequires()) {
            last = req;
        }
        return last;
    }

}
