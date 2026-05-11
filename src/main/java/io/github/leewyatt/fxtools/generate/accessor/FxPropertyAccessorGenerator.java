package io.github.leewyatt.fxtools.generate.accessor;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
        generateMissingAccessors(project, descriptors, false,
                new InsertionAnchor(null, false));
    }

    static void generateMissingAccessorsAtCaret(@NotNull Project project,
                                                @NotNull List<FxPropertyAccessorDescriptor> descriptors,
                                                @NotNull PsiClass psiClass,
                                                @NotNull PsiFile psiFile,
                                                int caretOffset) {
        InsertionAnchor anchor = new InsertionAnchor(findCaretAnchor(psiClass, psiFile, caretOffset), true);
        generateMissingAccessors(project, descriptors, true, anchor);
    }

    private static void generateMissingAccessors(@NotNull Project project,
                                                 @NotNull List<FxPropertyAccessorDescriptor> descriptors,
                                                 boolean useCaretAnchor,
                                                 @NotNull InsertionAnchor caretAnchor) {
        List<GenerationPlan> plans = new ArrayList<>();
        for (FxPropertyAccessorDescriptor descriptor : descriptors) {
            if (descriptor.hasMissingMethods()) {
                plans.add(new GenerationPlan(descriptor, useCaretAnchor
                        ? caretAnchor
                        : new InsertionAnchor(findLastRelatedAccessor(descriptor), false)));
            }
        }
        if (plans.isEmpty()) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        Map<InsertionAnchor, PsiElement> currentAnchors = new HashMap<>();
        Set<PsiElement> classesToReformat = Collections.newSetFromMap(new IdentityHashMap<>());

        for (GenerationPlan plan : plans) {
            FxPropertyAccessorDescriptor descriptor = plan.descriptor();
            boolean firstAtAnchor = !currentAnchors.containsKey(plan.anchor());
            PsiElement currentAnchor = currentAnchors.get(plan.anchor());
            // IDEA arrangement-based path: intention with no existing related accessor.
            boolean useArrangement = !useCaretAnchor && plan.anchor().element() == null;
            for (FxAccessorMethodKind kind : FxAccessorMethodKind.values()) {
                if (!descriptor.missingMethods().contains(kind)) {
                    continue;
                }
                PsiMethod method = factory.createMethodFromText(methodText(descriptor, kind),
                        descriptor.field());
                if (useArrangement) {
                    currentAnchor = GenerateMembersUtil.insert(
                            descriptor.containingClass(), method, null, true);
                } else {
                    currentAnchor = addMethod(descriptor.containingClass(), method,
                            plan.anchor(), currentAnchor, firstAtAnchor);
                }
                firstAtAnchor = false;
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
                                  @NotNull InsertionAnchor anchor) {
    }

    private record InsertionAnchor(@Nullable PsiElement element, boolean before) {
    }

    @NotNull
    private static PsiElement addMethod(@NotNull PsiClass psiClass,
                                        @NotNull PsiMethod method,
                                        @NotNull InsertionAnchor insertionAnchor,
                                        @Nullable PsiElement currentAnchor,
                                        boolean firstAtAnchor) {
        if (!firstAtAnchor && currentAnchor != null) {
            return psiClass.addAfter(method, currentAnchor);
        }
        PsiElement element = insertionAnchor.element();
        if (element == null) {
            return psiClass.add(method);
        }
        if (insertionAnchor.before()) {
            return psiClass.addBefore(method, element);
        }
        return psiClass.addAfter(method, element);
    }

    @Nullable
    private static PsiElement findCaretAnchor(@NotNull PsiClass psiClass,
                                              @NotNull PsiFile psiFile,
                                              int caretOffset) {
        PsiElement leaf = psiFile.findElementAt(caretOffset);
        if (leaf == null && caretOffset > 0) {
            leaf = psiFile.findElementAt(caretOffset - 1);
        }
        PsiElement anchor = leaf;
        while (anchor != null && anchor.getParent() != psiClass) {
            anchor = anchor.getParent();
        }
        PsiElement lBrace = psiClass.getLBrace();
        PsiElement rBrace = psiClass.getRBrace();
        if (anchor == null || lBrace == null || rBrace == null) {
            return null;
        }
        int anchorOffset = anchor.getTextOffset();
        if (anchorOffset > lBrace.getTextOffset() && anchorOffset < rBrace.getTextOffset()) {
            if (leaf != null && leaf.getParent() == psiClass
                    && PsiUtilCore.getElementType(leaf.getPrevSibling()) == JavaTokenType.END_OF_LINE_COMMENT) {
                return leaf.getNextSibling();
            }
            return anchor;
        }
        return null;
    }

    private static PsiElement findLastRelatedAccessor(@NotNull FxPropertyAccessorDescriptor descriptor) {
        PsiElement anchor = null;
        for (PsiMethod method : descriptor.containingClass().getMethods()) {
            if (isRelatedAccessor(method, descriptor)
                    && (anchor == null || method.getTextOffset() > anchor.getTextOffset())) {
                anchor = method;
            }
        }
        return anchor;
    }

    private static boolean isRelatedAccessor(@NotNull PsiMethod method,
                                             @NotNull FxPropertyAccessorDescriptor descriptor) {
        String name = method.getName();
        return descriptor.getterName().equals(name)
                || descriptor.setterName().equals(name)
                || descriptor.propertyMethodName().equals(name)
                || descriptor.propertyImplMethodName().equals(name);
    }

    @NotNull
    private static String methodText(@NotNull FxPropertyAccessorDescriptor descriptor,
                                     @NotNull FxAccessorMethodKind kind) {
        switch (kind) {
            case GETTER:
                return getterText(descriptor);
            case PRIVATE_SETTER:
                return privateSetterText(descriptor);
            case SETTER:
                return setterText(descriptor);
            case PROPERTY:
                return propertyText(descriptor);
            case PROPERTY_IMPL:
                return propertyImplText(descriptor);
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
    private static String privateSetterText(@NotNull FxPropertyAccessorDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("private void ").append(descriptor.setterName()).append("(")
                .append(descriptor.valueTypeText()).append(" value) {\n");
        if (descriptor.lazy()) {
            sb.append("    ").append(descriptor.propertyImplMethodName()).append("().set(value);\n");
        } else {
            sb.append("    ").append(descriptor.fieldName()).append(".set(value);\n");
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
            if (descriptor.readOnlyWrapper()) {
                sb.append("    return ").append(descriptor.propertyImplMethodName())
                        .append("().getReadOnlyProperty();\n");
            } else {
                sb.append("    if (").append(descriptor.fieldName()).append(" == null) {\n");
                sb.append("        ").append(descriptor.fieldName()).append(" = ")
                        .append(descriptor.lazyInitializerText()).append(";\n");
                sb.append("    }\n");
            }
        }
        if (descriptor.readOnlyWrapper() && !descriptor.lazy()) {
            sb.append("    return ").append(descriptor.fieldName()).append(".getReadOnlyProperty();\n");
        } else if (!descriptor.readOnlyWrapper()) {
            sb.append("    return ").append(descriptor.fieldName()).append(";\n");
        }
        sb.append("}");
        return sb.toString();
    }

    @NotNull
    private static String propertyImplText(@NotNull FxPropertyAccessorDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("private ").append(descriptor.propertyImplReturnTypeText()).append(" ")
                .append(descriptor.propertyImplMethodName()).append("() {\n");
        sb.append("    if (").append(descriptor.fieldName()).append(" == null) {\n");
        sb.append("        ").append(descriptor.fieldName()).append(" = ")
                .append(descriptor.lazyInitializerText()).append(";\n");
        sb.append("    }\n");
        sb.append("    return ").append(descriptor.fieldName()).append(";\n");
        sb.append("}");
        return sb.toString();
    }
}
