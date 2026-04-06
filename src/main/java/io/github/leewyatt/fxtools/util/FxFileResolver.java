package io.github.leewyatt.fxtools.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core utility for resolving associations between FxmlKit View classes, FXML files, and CSS files.
 */
public final class FxFileResolver {

    private static final String FXML_VIEW_CLASS = "com.dlsc.fxmlkit.fxml.FxmlView";
    private static final String FXML_VIEW_PROVIDER_CLASS = "com.dlsc.fxmlkit.fxml.FxmlViewProvider";
    private static final String FXML_PATH_ANNOTATION = "com.dlsc.fxmlkit.annotations.FxmlPath";

    private static final String[] KNOWN_SUFFIXES = {"ViewProvider", "Provider", "View"};
    private static final Pattern IMPORT_PATTERN = Pattern.compile("<\\?import\\s+([\\w.]+)\\s*\\?>");

    private FxFileResolver() {
    }

    /**
     * Checks if the given class extends FxmlView or FxmlViewProvider.
     */
    public static boolean isFxmlKitViewClass(@NotNull PsiClass psiClass) {
        return InheritanceUtil.isInheritor(psiClass, false, FXML_VIEW_CLASS)
                || InheritanceUtil.isInheritor(psiClass, false, FXML_VIEW_PROVIDER_CLASS);
    }

    /**
     * Checks if the given class extends FxmlView (not FxmlViewProvider).
     */
    public static boolean isFxmlView(@NotNull PsiClass psiClass) {
        return InheritanceUtil.isInheritor(psiClass, false, FXML_VIEW_CLASS);
    }

    /**
     * Checks if the given class extends FxmlViewProvider (not FxmlView).
     */
    public static boolean isFxmlViewProvider(@NotNull PsiClass psiClass) {
        return InheritanceUtil.isInheritor(psiClass, false, FXML_VIEW_PROVIDER_CLASS);
    }

    /**
     * Finds the FXML file for a given FxmlView/FxmlViewProvider class.
     * Search order: @FxmlPath annotation, ClassName.fxml, suffix-stripped BaseName.fxml.
     */
    @Nullable
    public static VirtualFile findFxmlFile(@NotNull PsiClass viewClass) {
        // 1. @FxmlPath annotation
        PsiAnnotation annotation = viewClass.getAnnotation(FXML_PATH_ANNOTATION);
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
            if (value instanceof PsiLiteralExpression) {
                Object val = ((PsiLiteralExpression) value).getValue();
                if (val instanceof String && !((String) val).isEmpty()) {
                    return resolveFxmlPath(viewClass, (String) val);
                }
            }
        }

        // 2. ClassName.fxml
        VirtualFile resourceDir = findResourcePackageDir(viewClass);
        if (resourceDir == null) {
            return null;
        }

        String className = viewClass.getName();
        if (className == null) {
            return null;
        }

        VirtualFile exact = resourceDir.findChild(className + ".fxml");
        if (exact != null) {
            return exact;
        }

        // 3. Suffix-stripped BaseName.fxml
        String baseName = stripKnownSuffix(className);
        if (!baseName.equals(className)) {
            VirtualFile stripped = resourceDir.findChild(baseName + ".fxml");
            if (stripped != null) {
                return stripped;
            }
        }

        return null;
    }

    /**
     * Finds the CSS or BSS file associated with an FXML file.
     * Looks in the same directory with the same base name. BSS takes priority over CSS.
     */
    @Nullable
    public static VirtualFile findCssFile(@NotNull VirtualFile fxmlFile) {
        VirtualFile parent = fxmlFile.getParent();
        if (parent == null) {
            return null;
        }

        String baseName = fxmlFile.getNameWithoutExtension();

        VirtualFile bss = parent.findChild(baseName + ".bss");
        if (bss != null) {
            return bss;
        }

        VirtualFile css = parent.findChild(baseName + ".css");
        if (css != null) {
            return css;
        }

        return null;
    }

    /**
     * Reverse lookup: finds the FxmlView/FxmlViewProvider class for a given FXML file.
     */
    @Nullable
    public static PsiClass findViewClassForFxml(@NotNull PsiFile fxmlFile) {
        VirtualFile vFile = fxmlFile.getVirtualFile();
        if (vFile == null) {
            return null;
        }

        Project project = fxmlFile.getProject();
        Module module = ModuleUtilCore.findModuleForFile(vFile, project);
        if (module == null) {
            return null;
        }

        String packageName = getPackageFromResourceFile(vFile, module);
        String fxmlBaseName = vFile.getNameWithoutExtension();

        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);

        // Try exact class name match
        PsiClass exactMatch = findClassInPackage(facade, packageName, fxmlBaseName, scope);
        if (exactMatch != null && isFxmlKitViewClass(exactMatch)) {
            return exactMatch;
        }

        // Try with known suffixes appended: BaseName + View, BaseName + ViewProvider, BaseName + Provider
        String[] appendSuffixes = {"View", "ViewProvider", "Provider"};
        for (String suffix : appendSuffixes) {
            PsiClass candidate = findClassInPackage(facade, packageName, fxmlBaseName + suffix, scope);
            if (candidate != null && isFxmlKitViewClass(candidate)) {
                VirtualFile candidateFxml = findFxmlFile(candidate);
                if (vFile.equals(candidateFxml)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * Extracts the fx:controller class name from an FXML file's root element.
     */
    @Nullable
    public static String extractControllerClassName(@NotNull VirtualFile fxmlFile, @NotNull Project project) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(fxmlFile);
        if (!(psiFile instanceof XmlFile)) {
            return null;
        }

        XmlTag rootTag = ((XmlFile) psiFile).getRootTag();
        if (rootTag == null) {
            return null;
        }

        return rootTag.getAttributeValue("fx:controller");
    }

    /**
     * Resolves an @FxmlPath value to a VirtualFile.
     * Relative paths resolve from the class's resource package; absolute paths (starting with /)
     * resolve from classpath resource roots.
     */
    @Nullable
    public static VirtualFile resolveFxmlPath(@NotNull PsiClass annotatedClass, @NotNull String path) {
        if (path.isEmpty()) {
            return null;
        }

        if (path.startsWith("/")) {
            return resolveAbsoluteFxmlPath(annotatedClass, path.substring(1));
        } else {
            VirtualFile resourceDir = findResourcePackageDir(annotatedClass);
            if (resourceDir == null) {
                return null;
            }
            return resourceDir.findFileByRelativePath(path);
        }
    }

    /**
     * Finds the resource directory corresponding to a Java class's package.
     */
    @Nullable
    public static VirtualFile findResourcePackageDir(@NotNull PsiClass psiClass) {
        Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (module == null) {
            return null;
        }

        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile)) {
            return null;
        }

        String packageName = ((PsiJavaFile) containingFile).getPackageName();
        String packagePath = packageName.replace('.', '/');

        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);

        for (VirtualFile root : resourceRoots) {
            VirtualFile dir = packagePath.isEmpty() ? root : root.findFileByRelativePath(packagePath);
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }

        return null;
    }

    /**
     * Strips known suffixes from a class name to get the base name.
     */
    @NotNull
    public static String stripKnownSuffix(@NotNull String className) {
        for (String suffix : KNOWN_SUFFIXES) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        return className;
    }

    /**
     * Resolves a FXML element tag name to its fully qualified Java class name
     * using the import directives in the FXML file.
     */
    @Nullable
    public static String resolveTagNameToFqn(@NotNull String tagName,
                                             @NotNull VirtualFile fxmlFile,
                                             @NotNull Project project) {
        if ("fx:include".equals(tagName)) {
            return "javafx.scene.Node";
        }
        if (tagName.contains(".")) {
            return tagName;
        }

        List<String> imports = parseFxmlImports(fxmlFile);

        for (String imp : imports) {
            if (imp.endsWith("." + tagName)) {
                return imp;
            }
        }

        for (String imp : imports) {
            if (imp.endsWith(".*")) {
                String candidate = imp.substring(0, imp.length() - 1) + tagName;
                PsiClass cls = JavaPsiFacade.getInstance(project)
                        .findClass(candidate, GlobalSearchScope.allScope(project));
                if (cls != null) {
                    return candidate;
                }
            }
        }

        PsiClass[] found = com.intellij.psi.search.PsiShortNamesCache.getInstance(project)
                .getClassesByName(tagName, GlobalSearchScope.allScope(project));
        if (found.length > 0 && found[0].getQualifiedName() != null) {
            return found[0].getQualifiedName();
        }

        return null;
    }

    /**
     * Builds a type text with wildcard generics for types that have type parameters.
     */
    @NotNull
    public static String buildGenericTypeText(@NotNull String typeFqn, @NotNull Project project) {
        PsiClass cls = JavaPsiFacade.getInstance(project)
                .findClass(typeFqn, GlobalSearchScope.allScope(project));
        if (cls != null) {
            PsiTypeParameter[] typeParams = cls.getTypeParameters();
            if (typeParams.length > 0) {
                StringBuilder sb = new StringBuilder(typeFqn);
                sb.append('<');
                for (int i = 0; i < typeParams.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append('?');
                }
                sb.append('>');
                return sb.toString();
            }
        }
        return typeFqn;
    }

    @NotNull
    private static List<String> parseFxmlImports(@NotNull VirtualFile fxmlFile) {
        List<String> imports = new ArrayList<>();
        String content;
        try {
            content = new String(fxmlFile.contentsToByteArray(), fxmlFile.getCharset());
        } catch (Exception e) {
            return imports;
        }
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    @Nullable
    private static VirtualFile resolveAbsoluteFxmlPath(@NotNull PsiClass psiClass, @NotNull String relativePath) {
        Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (module == null) {
            return null;
        }

        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);

        for (VirtualFile root : resourceRoots) {
            VirtualFile file = root.findFileByRelativePath(relativePath);
            if (file != null) {
                return file;
            }
        }

        return null;
    }

    @Nullable
    private static PsiClass findClassInPackage(
            @NotNull JavaPsiFacade facade, @NotNull String packageName,
            @NotNull String className, @NotNull GlobalSearchScope scope) {
        String fqn = packageName.isEmpty() ? className : packageName + "." + className;
        return facade.findClass(fqn, scope);
    }

    @NotNull
    private static String getPackageFromResourceFile(@NotNull VirtualFile file, @NotNull Module module) {
        List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaResourceRootType.RESOURCE);

        for (VirtualFile root : resourceRoots) {
            if (VfsUtilCore.isAncestor(root, file, true)) {
                VirtualFile parent = file.getParent();
                if (parent == null) {
                    return "";
                }
                String relativePath = VfsUtilCore.getRelativePath(parent, root);
                if (relativePath != null && !relativePath.isEmpty()) {
                    return relativePath.replace('/', '.');
                }
                return "";
            }
        }

        return "";
    }
}
