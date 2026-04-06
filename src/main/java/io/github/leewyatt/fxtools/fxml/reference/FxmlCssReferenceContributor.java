package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Registers PsiReferences on stylesheets paths and styleClass attribute values
 * in FXML files. Works in all JavaFX projects.
 */
public class FxmlCssReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // stylesheets: value="@path.css" or fx:value="@path.css"
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(
                        XmlPatterns.xmlAttribute().withName(
                                PlatformPatterns.string().oneOf("value", "fx:value"))),
                new StylesheetPathProvider()
        );

        // styleClass="class1, class2"
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(
                        XmlPatterns.xmlAttribute().withName("styleClass")),
                new StyleClassProvider()
        );
    }

    private static class StylesheetPathProvider extends PsiReferenceProvider {

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
            if (!value.startsWith("@")) {
                return PsiReference.EMPTY_ARRAY;
            }

            int offset = element.getText().indexOf(value);
            TextRange range = TextRange.create(offset, offset + value.length());
            return new PsiReference[]{new StylesheetPathReference(attrValue, range, value)};
        }
    }

    private static class StyleClassProvider extends PsiReferenceProvider {

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

            String elementText = element.getText();
            int valueStart = elementText.indexOf(value);

            List<PsiReference> refs = new ArrayList<>();
            int searchFrom = 0;
            for (String cls : value.split("[,\\s]+")) {
                if (cls.isEmpty()) {
                    continue;
                }
                int pos = value.indexOf(cls, searchFrom);
                if (pos >= 0) {
                    TextRange range = TextRange.create(valueStart + pos, valueStart + pos + cls.length());
                    refs.add(new StyleClassReference(attrValue, range, cls));
                    searchFrom = pos + cls.length();
                }
            }

            return refs.toArray(PsiReference.EMPTY_ARRAY);
        }
    }

    private static class StylesheetPathReference extends PsiReferenceBase<XmlAttributeValue> {

        private final String path;

        StylesheetPathReference(@NotNull XmlAttributeValue element,
                                @NotNull TextRange range, @NotNull String path) {
            super(element, range, true);
            this.path = path;
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
            VirtualFile cssFile = fxmlDir.findFileByRelativePath(relativePath);
            if (cssFile == null) {
                return null;
            }

            return PsiManager.getInstance(myElement.getProject()).findFile(cssFile);
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

            List<LookupElementBuilder> variants = new ArrayList<>();
            for (VirtualFile child : fxmlDir.getChildren()) {
                String ext = child.getExtension();
                if ("css".equals(ext) || "bss".equals(ext)) {
                    variants.add(LookupElementBuilder.create("@" + child.getName())
                            .withIcon(AllIcons.FileTypes.Css));
                }
            }
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
    }

    private static class StyleClassReference extends PsiReferenceBase<XmlAttributeValue> {

        private final String className;

        StyleClassReference(@NotNull XmlAttributeValue element,
                            @NotNull TextRange range, @NotNull String className) {
            super(element, range, true);
            this.className = className;
        }

        @Override
        public @Nullable PsiElement resolve() {
            Project project = myElement.getProject();
            String selectorPattern = "." + className;
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            Collection<VirtualFile> cssFiles = FilenameIndex.getAllFilesByExt(project, "css", scope);
            for (VirtualFile cssFile : cssFiles) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(cssFile);
                if (psiFile == null) {
                    continue;
                }

                String content = psiFile.getText();
                int index = content.indexOf(selectorPattern);
                while (index >= 0) {
                    if (index == 0 || !Character.isLetterOrDigit(content.charAt(index - 1))) {
                        PsiElement elementAt = psiFile.findElementAt(index);
                        if (elementAt != null) {
                            return elementAt;
                        }
                    }
                    index = content.indexOf(selectorPattern, index + 1);
                }
            }

            return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
            return EMPTY_ARRAY;
        }
    }
}
