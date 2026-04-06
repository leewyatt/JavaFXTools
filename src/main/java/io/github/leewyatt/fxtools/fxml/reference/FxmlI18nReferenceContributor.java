package io.github.leewyatt.fxtools.fxml.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Registers PsiReferences on %key internationalization references in FXML attribute values.
 */
public class FxmlI18nReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new I18nKeyReferenceProvider()
        );
    }

    private static class I18nKeyReferenceProvider extends PsiReferenceProvider {

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
            if (!value.startsWith("%") || value.length() <= 1) {
                return PsiReference.EMPTY_ARRAY;
            }

            PsiElement parent = attrValue.getParent();
            if (parent instanceof XmlAttribute) {
                String attrName = ((XmlAttribute) parent).getName();
                if (attrName.startsWith("fx:") || attrName.startsWith("xmlns")) {
                    return PsiReference.EMPTY_ARRAY;
                }
            }

            String key = value.substring(1);
            int offset = element.getText().indexOf(value) + 1;
            TextRange range = TextRange.create(offset, offset + key.length());
            return new PsiReference[]{new I18nKeyReference(attrValue, range, key)};
        }
    }

    private static class I18nKeyReference extends PsiReferenceBase<XmlAttributeValue> {

        private final String key;

        I18nKeyReference(@NotNull XmlAttributeValue element, @NotNull TextRange range,
                         @NotNull String key) {
            super(element, range, true);
            this.key = key;
        }

        @Override
        public @Nullable PsiElement resolve() {
            Project project = myElement.getProject();
            for (PropertiesFile propFile : findPropertiesFiles(project)) {
                IProperty property = propFile.findPropertyByKey(key);
                if (property != null) {
                    return property.getPsiElement();
                }
            }
            return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
            Project project = myElement.getProject();
            List<LookupElementBuilder> variants = new ArrayList<>();

            for (PropertiesFile propFile : findPropertiesFiles(project)) {
                for (IProperty property : propFile.getProperties()) {
                    String propKey = property.getKey();
                    String propValue = property.getValue();
                    if (propKey != null) {
                        LookupElementBuilder builder = LookupElementBuilder.create(propKey)
                                .withIcon(AllIcons.Nodes.ResourceBundle);
                        if (propValue != null && !propValue.isEmpty()) {
                            builder = builder.withTailText(" = " + truncate(propValue, 40), true);
                        }
                        variants.add(builder);
                    }
                }
            }

            return variants.toArray();
        }

        @NotNull
        private List<PropertiesFile> findPropertiesFiles(@NotNull Project project) {
            List<PropertiesFile> result = new ArrayList<>();
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "properties", scope);

            VirtualFile fxmlFile = myElement.getContainingFile().getVirtualFile();
            VirtualFile fxmlDir = fxmlFile != null ? fxmlFile.getParent() : null;

            // Prioritize files in same directory
            List<VirtualFile> sameDir = new ArrayList<>();
            List<VirtualFile> others = new ArrayList<>();
            for (VirtualFile f : files) {
                if (fxmlDir != null && fxmlDir.equals(f.getParent())) {
                    sameDir.add(f);
                } else {
                    others.add(f);
                }
            }

            addPropertiesFiles(sameDir, project, result);
            addPropertiesFiles(others, project, result);
            return result;
        }

        private void addPropertiesFiles(@NotNull List<VirtualFile> files,
                                        @NotNull Project project,
                                        @NotNull List<PropertiesFile> result) {
            for (VirtualFile f : files) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(f);
                if (psiFile instanceof PropertiesFile) {
                    result.add((PropertiesFile) psiFile);
                }
            }
        }

        @NotNull
        private String truncate(@NotNull String text, int maxLength) {
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength) + "...";
        }
    }
}
