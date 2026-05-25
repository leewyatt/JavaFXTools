package io.github.leewyatt.fxtools.javacolor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.paintpicker.PaintPicker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;
import java.awt.event.MouseEvent;

/**
 * Opens a {@link PaintPicker} popup (color-only mode) anchored to the gutter
 * icon and writes back the edited color via the format-preserving
 * {@link JavaColorWriter}.
 */
public final class JavaColorGutterHandler {

    private JavaColorGutterHandler() {
    }

    /**
     * Opens the editor popup for the given recognized expression.
     */
    public static void openEditor(@NotNull MouseEvent mouseEvent,
                                   @NotNull PsiFile psiFile,
                                   @NotNull JavaColorExpression expression) {
        Project project = psiFile.getProject();
        Editor editor = findEditor(project, psiFile.getVirtualFile());
        if (editor == null) {
            return;
        }

        Document document = editor.getDocument();
        var range = expression.replaceRange();
        RangeMarker marker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset());
        // Greedy so the marker absorbs the new text on each picker change event;
        // without this, after the first replaceString the marker collapses to an
        // empty range and subsequent edits insert at the start instead of overwriting.
        marker.setGreedyToRight(true);

        PaintPicker picker = PaintPicker.createColorPicker();
        picker.setPaintProperty(expression.color());

        String groupId = "JavaColorPicker.Change." + System.nanoTime();

        picker.addPaintChangeListener(evt -> {
            Paint newPaint = (Paint) evt.getNewValue();
            if (!(newPaint instanceof Color newColor) || !marker.isValid()) {
                return;
            }
            String newText = JavaColorWriter.format(expression, newColor);
            String currentText = document.getText(
                    new TextRange(marker.getStartOffset(), marker.getEndOffset()));
            if (newText.equals(currentText)) {
                return;
            }
            WriteCommandAction.runWriteCommandAction(project, "Change Color", groupId, () -> {
                document.replaceString(marker.getStartOffset(), marker.getEndOffset(), newText);
            });
        });

        picker.setBorder(JBUI.Borders.empty(6));
        JBScrollPane scrollPane = new JBScrollPane(picker);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(JBUI.scale(420), JBUI.scale(665)));

        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, picker)
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .createPopup();

        popup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                marker.dispose();
            }
        });

        popup.show(new RelativePoint(mouseEvent));
    }

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
