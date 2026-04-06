package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects references for {@code ResourceBundle.getBundle("bundle.name")} string literals,
 * enabling Ctrl+Click navigation to the corresponding .properties files.
 */
public class ResourceBundleReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new ResourceBundleReferenceProvider()
        );
    }

    private static class ResourceBundleReferenceProvider extends PsiReferenceProvider {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof PsiLiteralExpression literal)) {
                return PsiReference.EMPTY_ARRAY;
            }
            Object value = literal.getValue();
            if (!(value instanceof String bundleName) || bundleName.isEmpty()) {
                return PsiReference.EMPTY_ARRAY;
            }
            if (!isGetBundleArgument(literal)) {
                return PsiReference.EMPTY_ARRAY;
            }
            return new PsiReference[]{new BundleReference(literal, bundleName)};
        }

        private static boolean isGetBundleArgument(@NotNull PsiLiteralExpression literal) {
            PsiElement parent = literal.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return false;
            }
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression call)) {
                return false;
            }
            return ResourceBundleUtil.isGetBundleCall(call, literal);
        }
    }

    private static class BundleReference extends PsiPolyVariantReferenceBase<PsiLiteralExpression> {
        private final String bundleName;

        BundleReference(@NotNull PsiLiteralExpression element, @NotNull String bundleName) {
            super(element, false);
            this.bundleName = bundleName;
        }

        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            List<PsiFile> files = ResourceBundleUtil.findBundleFiles(
                    myElement.getProject(), bundleName);
            if (files.isEmpty()) {
                return ResolveResult.EMPTY_ARRAY;
            }
            ResolveResult[] results = new ResolveResult[files.size()];
            for (int i = 0; i < files.size(); i++) {
                results[i] = new PsiElementResolveResult(new BundleFileElement(files.get(i)));
            }
            return results;
        }

        @Override
        public @Nullable PsiElement resolve() {
            ResolveResult[] results = multiResolve(false);
            return results.length == 1 ? results[0].getElement() : null;
        }
    }
}
