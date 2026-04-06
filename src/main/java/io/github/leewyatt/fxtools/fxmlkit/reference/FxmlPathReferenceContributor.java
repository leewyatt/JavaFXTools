package io.github.leewyatt.fxtools.fxmlkit.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.leewyatt.fxtools.util.FxFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers PsiReferences on @FxmlPath annotation string values,
 * enabling Ctrl+Click navigation, code completion, and rename refactoring.
 */
public class FxmlPathReferenceContributor extends PsiReferenceContributor {

    private static final String FXML_PATH_ANNOTATION = "com.dlsc.fxmlkit.annotations.FxmlPath";

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class)
                        .inside(PlatformPatterns.psiElement(PsiAnnotation.class)),
                new FxmlPathReferenceProvider()
        );
    }

    private static class FxmlPathReferenceProvider extends PsiReferenceProvider {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof PsiLiteralExpression)) {
                return PsiReference.EMPTY_ARRAY;
            }

            PsiLiteralExpression literal = (PsiLiteralExpression) element;
            Object value = literal.getValue();
            if (!(value instanceof String)) {
                return PsiReference.EMPTY_ARRAY;
            }

            PsiAnnotation annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation.class);
            if (annotation == null || !FXML_PATH_ANNOTATION.equals(annotation.getQualifiedName())) {
                return PsiReference.EMPTY_ARRAY;
            }

            return new PsiReference[]{new FxmlPathReference(literal, (String) value)};
        }
    }

    private static class FxmlPathReference extends PsiReferenceBase<PsiLiteralExpression> {

        private final String path;

        FxmlPathReference(@NotNull PsiLiteralExpression element, @NotNull String path) {
            super(element, TextRange.create(1, Math.max(1, element.getTextLength() - 1)), true);
            this.path = path;
        }

        @Override
        public @Nullable PsiElement resolve() {
            PsiClass containingClass = PsiTreeUtil.getParentOfType(myElement, PsiClass.class);
            if (containingClass == null) {
                return null;
            }

            VirtualFile file = FxFileResolver.resolveFxmlPath(containingClass, path);
            if (file == null) {
                return null;
            }

            return PsiManager.getInstance(myElement.getProject()).findFile(file);
        }

        @Override
        public Object @NotNull [] getVariants() {
            PsiClass containingClass = PsiTreeUtil.getParentOfType(myElement, PsiClass.class);
            if (containingClass == null) {
                return EMPTY_ARRAY;
            }

            List<LookupElementBuilder> variants = new ArrayList<>();

            if (path.startsWith("/")) {
                Module module = ModuleUtilCore.findModuleForPsiElement(containingClass);
                if (module != null) {
                    for (VirtualFile root : ModuleRootManager.getInstance(module)
                            .getSourceRoots(JavaResourceRootType.RESOURCE)) {
                        collectFxmlFiles(root, "/", variants);
                    }
                }
            } else {
                VirtualFile resourceDir = FxFileResolver.findResourcePackageDir(containingClass);
                if (resourceDir != null) {
                    collectFxmlFiles(resourceDir, "", variants);
                }
            }

            return variants.toArray();
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            int lastSlash = path.lastIndexOf('/');
            String newPath;
            if (lastSlash >= 0) {
                newPath = path.substring(0, lastSlash + 1) + newElementName;
            } else {
                newPath = newElementName;
            }
            return ElementManipulators.handleContentChange(myElement, newPath);
        }

        private void collectFxmlFiles(@NotNull VirtualFile dir, @NotNull String prefix,
                                      @NotNull List<LookupElementBuilder> result) {
            for (VirtualFile child : dir.getChildren()) {
                if (child.isDirectory()) {
                    collectFxmlFiles(child, prefix + child.getName() + "/", result);
                } else if ("fxml".equals(child.getExtension())) {
                    result.add(LookupElementBuilder.create(prefix + child.getName())
                            .withIcon(AllIcons.FileTypes.Xml));
                }
            }
        }
    }
}
