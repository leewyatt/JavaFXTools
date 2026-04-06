package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registers PsiReferences on FXML resource paths: Image url, fx:include source, URL value.
 */
public class FxmlResourceReferenceContributor extends PsiReferenceContributor {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Image url="@..."
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(
                        XmlPatterns.xmlAttribute().withName("url").withParent(
                                XmlPatterns.xmlTag().withName("Image"))),
                new AtPathReferenceProvider(IMAGE_EXTENSIONS)
        );

        // fx:include source="..."
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(
                        XmlPatterns.xmlAttribute().withName("source").withParent(
                                XmlPatterns.xmlTag().withName("fx:include"))),
                new IncludeSourceReferenceProvider()
        );

        // URL value="@..."
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(
                        XmlPatterns.xmlAttribute().withName("value").withParent(
                                XmlPatterns.xmlTag().withName("URL"))),
                new AtPathReferenceProvider(Set.of("css", "bss"))
        );
    }

    private static class AtPathReferenceProvider extends PsiReferenceProvider {

        private final Set<String> extensions;

        AtPathReferenceProvider(@NotNull Set<String> extensions) {
            this.extensions = extensions;
        }

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof XmlAttributeValue)) {
                return PsiReference.EMPTY_ARRAY;
            }

            VirtualFile vFile = element.getContainingFile().getVirtualFile();
            if (vFile == null || !"fxml".equals(vFile.getExtension())) {
                return PsiReference.EMPTY_ARRAY;
            }

            XmlAttributeValue attrValue = (XmlAttributeValue) element;
            String value = attrValue.getValue();
            if (!value.startsWith("@") || value.length() <= 1) {
                return PsiReference.EMPTY_ARRAY;
            }

            int offset = element.getText().indexOf(value);
            TextRange range = TextRange.create(offset, offset + value.length());
            return new PsiReference[]{new ResourcePathReference(attrValue, range, value, extensions)};
        }
    }

    private static class IncludeSourceReferenceProvider extends PsiReferenceProvider {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof XmlAttributeValue)) {
                return PsiReference.EMPTY_ARRAY;
            }

            VirtualFile vFile = element.getContainingFile().getVirtualFile();
            if (vFile == null || !"fxml".equals(vFile.getExtension())) {
                return PsiReference.EMPTY_ARRAY;
            }

            XmlAttributeValue attrValue = (XmlAttributeValue) element;
            String value = attrValue.getValue();
            if (value.isEmpty()) {
                return PsiReference.EMPTY_ARRAY;
            }

            int offset = element.getText().indexOf(value);
            TextRange range = TextRange.create(offset, offset + value.length());
            return new PsiReference[]{
                    new ResourcePathReference(attrValue, range, value, Set.of("fxml"))};
        }
    }

    private static class ResourcePathReference extends PsiReferenceBase<XmlAttributeValue> {

        private final String path;
        private final Set<String> extensions;

        ResourcePathReference(@NotNull XmlAttributeValue element, @NotNull TextRange range,
                              @NotNull String path, @NotNull Set<String> extensions) {
            super(element, range, true);
            this.path = path;
            this.extensions = extensions;
        }

        @Override
        public @Nullable PsiElement resolve() {
            VirtualFile fxmlFile = myElement.getContainingFile().getVirtualFile();
            if (fxmlFile == null) {
                return null;
            }
            VirtualFile fxmlDir = fxmlFile.getParent();
            if (fxmlDir == null) {
                return null;
            }

            String relativePath = path.startsWith("@") ? path.substring(1) : path;
            VirtualFile target = fxmlDir.findFileByRelativePath(relativePath);
            if (target == null) {
                return null;
            }

            return PsiManager.getInstance(myElement.getProject()).findFile(target);
        }

        @Override
        public Object @NotNull [] getVariants() {
            VirtualFile fxmlFile = myElement.getContainingFile().getVirtualFile();
            if (fxmlFile == null) {
                return EMPTY_ARRAY;
            }
            VirtualFile fxmlDir = fxmlFile.getParent();
            if (fxmlDir == null) {
                return EMPTY_ARRAY;
            }

            String prefix = path.startsWith("@") ? "@" : "";
            List<LookupElementBuilder> variants = new ArrayList<>();
            collectFiles(fxmlDir, prefix, variants);
            return variants.toArray();
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            String prefix = path.startsWith("@") ? "@" : "";
            int lastSlash = path.lastIndexOf('/');
            String newPath;
            if (lastSlash >= 0) {
                newPath = path.substring(0, lastSlash + 1) + newElementName;
            } else {
                newPath = prefix + newElementName;
            }
            return ElementManipulators.handleContentChange(myElement, newPath);
        }

        private void collectFiles(@NotNull VirtualFile dir, @NotNull String prefix,
                                  @NotNull List<LookupElementBuilder> result) {
            for (VirtualFile child : dir.getChildren()) {
                if (child.isDirectory()) {
                    collectFiles(child, prefix + child.getName() + "/", result);
                } else {
                    String ext = child.getExtension();
                    if (ext != null && extensions.contains(ext.toLowerCase())) {
                        result.add(LookupElementBuilder.create(prefix + child.getName())
                                .withIcon(AllIcons.FileTypes.Any_type));
                    }
                }
            }
        }
    }
}
