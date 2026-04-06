package io.github.leewyatt.fxtools.css.preview.effect;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.css.index.FxCssPropertyIndex;
import io.github.leewyatt.fxtools.util.FxColorParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Handles gutter icon click to open an Effect editor popup for editing
 * CSS dropshadow()/innershadow() values.
 */
public final class CssGutterEffectHandler {

    private CssGutterEffectHandler() {
    }

    /**
     * Opens an Effect editor popup for the given CSS effect value range.
     *
     * @param mouseEvent the click event for popup positioning
     * @param psiFile    the source file
     * @param valueStart start offset of the effect value in the document
     * @param valueEnd   end offset of the effect value in the document
     * @param valueText  the current CSS effect value text
     */
    public static void openEditor(@NotNull MouseEvent mouseEvent,
                                   @NotNull PsiFile psiFile,
                                   int valueStart, int valueEnd,
                                   @NotNull String valueText) {
        Project project = psiFile.getProject();
        Editor editor = findEditor(project, psiFile.getVirtualFile());
        if (editor == null) {
            return;
        }

        Document document = editor.getDocument();
        RangeMarker marker = document.createRangeMarker(valueStart, valueEnd);
        marker.setGreedyToRight(true);

        // Resolve variable reference if needed, then parse
        String effectExpr = resolveEffectValue(valueText.trim(), project);
        EffectConfig initialConfig = FxEffectParser.parseEffect(effectExpr, project);
        if (initialConfig == null) {
            initialConfig = new EffectConfig();
        }

        EffectEditorPanel editorPanel = new EffectEditorPanel();
        editorPanel.setConfig(initialConfig);

        String groupId = "EffectEditor.Change." + System.nanoTime();

        // Set up real-time write-back (always CSS format)
        editorPanel.addChangeListener(e -> {
            if (!marker.isValid()) {
                return;
            }
            EffectConfig cfg = editorPanel.getConfig();
            String newText = cfg.toCssText();
            WriteCommandAction.runWriteCommandAction(project, "Change Effect", groupId, () -> {
                document.replaceString(marker.getStartOffset(), marker.getEndOffset(), newText);
            });
        });

        JBScrollPane scrollPane = new JBScrollPane(editorPanel);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(420), JBUI.scale(540)));

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, editorPanel)
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(false)
                .setCancelOnWindowDeactivation(false)
                .createPopup();

        popup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                marker.dispose();
            }
        });

        popup.show(new RelativePoint(mouseEvent));
    }

    // ==================== Variable Resolution ====================

    /**
     * Resolves an effect value, handling CSS variable references.
     */
    @NotNull
    private static String resolveEffectValue(@NotNull String value, @NotNull Project project) {
        if (FxEffectParser.isEffect(value)) {
            return value;
        }
        if (FxColorParser.isVariableReference(value)) {
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            java.util.List<String> values = FxCssPropertyIndex.getPropertyValues(value, project, scope);
            for (String rawValue : values) {
                String trimmed = rawValue.trim();
                if (FxEffectParser.isEffect(trimmed)) {
                    return trimmed;
                }
            }
        }
        return value;
    }

    // ==================== Helpers ====================

    @Nullable
    private static Editor findEditor(@NotNull Project project, @Nullable VirtualFile vFile) {
        if (vFile == null) {
            return null;
        }
        var editors = FileEditorManager.getInstance(project).getEditors(vFile);
        for (var fe : editors) {
            if (fe instanceof TextEditor te) {
                return te.getEditor();
            }
        }
        return null;
    }
}
