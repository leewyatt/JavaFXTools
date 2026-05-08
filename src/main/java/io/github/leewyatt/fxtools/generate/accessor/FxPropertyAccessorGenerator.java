package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class FxPropertyAccessorGenerator {

    private FxPropertyAccessorGenerator() {
    }

    static void generateMissingAccessors(@NotNull Project project,
                                         @NotNull FxPropertyAccessorDescriptor descriptor) {
        generateMissingAccessors(project, Collections.singletonList(descriptor));
    }

    static void generateMissingAccessors(@NotNull Project project,
                                         @NotNull List<FxPropertyAccessorDescriptor> descriptors) {
        List<GenerationPlan> plans = new ArrayList<>();
        for (FxPropertyAccessorDescriptor descriptor : descriptors) {
            if (descriptor.hasMissingMethods()) {
                plans.add(new GenerationPlan(descriptor, findInsertionAnchor(descriptor)));
            }
        }
        if (plans.isEmpty()) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        IdentityHashMap<PsiElement, PsiElement> currentAnchors = new IdentityHashMap<>();
        Set<PsiElement> classesToReformat = Collections.newSetFromMap(new IdentityHashMap<>());

        for (GenerationPlan plan : plans) {
            FxPropertyAccessorDescriptor descriptor = plan.descriptor();
            PsiElement currentAnchor = currentAnchors.getOrDefault(plan.anchor(), plan.anchor());
            for (FxAccessorMethodKind kind : FxAccessorMethodKind.values()) {
                if (!descriptor.missingMethods().contains(kind)) {
                    continue;
                }
                PsiMethod method = factory.createMethodFromText(methodText(descriptor, kind),
                        descriptor.field());
                currentAnchor = descriptor.containingClass().addAfter(method, currentAnchor);
            }
            currentAnchors.put(plan.anchor(), currentAnchor);
            classesToReformat.add(descriptor.containingClass());
        }

        for (PsiElement containingClass : classesToReformat) {
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingClass);
            CodeStyleManager.getInstance(project).reformat(containingClass);
        }
    }

    private record GenerationPlan(@NotNull FxPropertyAccessorDescriptor descriptor,
                                  @NotNull PsiElement anchor) {
    }

    @NotNull
    private static PsiElement findInsertionAnchor(@NotNull FxPropertyAccessorDescriptor descriptor) {
        PsiElement anchor = descriptor.field();
        PsiElement child = anchor.getNextSibling();
        boolean seenRelatedAccessor = false;
        while (child != null) {
            if (child instanceof PsiWhiteSpace) {
                child = child.getNextSibling();
                continue;
            }
            if (child instanceof PsiMethod && isRelatedAccessor((PsiMethod) child, descriptor)) {
                anchor = child;
                seenRelatedAccessor = true;
                child = child.getNextSibling();
                continue;
            }
            if (child instanceof PsiField) {
                if (seenRelatedAccessor) {
                    break;
                }
                anchor = child;
                child = child.getNextSibling();
                continue;
            }
            if (!seenRelatedAccessor && child instanceof PsiMethod
                    && ((PsiMethod) child).isConstructor()) {
                anchor = child;
                child = child.getNextSibling();
                continue;
            }
            break;
        }
        return anchor;
    }

    private static boolean isRelatedAccessor(@NotNull PsiMethod method,
                                             @NotNull FxPropertyAccessorDescriptor descriptor) {
        String name = method.getName();
        return descriptor.getterName().equals(name)
                || descriptor.setterName().equals(name)
                || descriptor.propertyMethodName().equals(name);
    }

    @NotNull
    private static String methodText(@NotNull FxPropertyAccessorDescriptor descriptor,
                                     @NotNull FxAccessorMethodKind kind) {
        switch (kind) {
            case GETTER:
                return getterText(descriptor);
            case SETTER:
                return setterText(descriptor);
            case PROPERTY:
                return propertyText(descriptor);
            default:
                throw new IllegalArgumentException("Unsupported accessor kind: " + kind);
        }
    }

    @NotNull
    private static String getterText(@NotNull FxPropertyAccessorDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("public final ").append(descriptor.valueTypeText()).append(" ")
                .append(descriptor.getterName()).append("() {\n");
        if (descriptor.lazy()) {
            sb.append("    return ").append(descriptor.fieldName()).append(" == null ? ")
                    .append(descriptor.lazyFallbackText()).append(" : ")
                    .append(descriptor.fieldName()).append(".get();\n");
        } else {
            sb.append("    return ").append(descriptor.fieldName()).append(".get();\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @NotNull
    private static String setterText(@NotNull FxPropertyAccessorDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("public final void ").append(descriptor.setterName()).append("(")
                .append(descriptor.valueTypeText()).append(" value) {\n");
        if (descriptor.lazy()) {
            sb.append("    ").append(descriptor.propertyMethodName()).append("().set(value);\n");
        } else {
            sb.append("    this.").append(descriptor.fieldName()).append(".set(value);\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @NotNull
    private static String propertyText(@NotNull FxPropertyAccessorDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("public final ").append(descriptor.propertyReturnTypeText()).append(" ")
                .append(descriptor.propertyMethodName()).append("() {\n");
        if (descriptor.lazy()) {
            sb.append("    if (").append(descriptor.fieldName()).append(" == null) {\n");
            sb.append("        ").append(descriptor.fieldName()).append(" = ")
                    .append(descriptor.lazyInitializerText()).append(";\n");
            sb.append("    }\n");
        }
        if (descriptor.readOnlyWrapper()) {
            sb.append("    return ").append(descriptor.fieldName()).append(".getReadOnlyProperty();\n");
        } else {
            sb.append("    return ").append(descriptor.fieldName()).append(";\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
