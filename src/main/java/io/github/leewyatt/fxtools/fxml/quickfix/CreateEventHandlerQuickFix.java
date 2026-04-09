package io.github.leewyatt.fxtools.fxml.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Quick fix that generates an @FXML event handler method in the Controller class.
 */
public class CreateEventHandlerQuickFix implements LocalQuickFix {

    private static final Logger LOG = Logger.getInstance(CreateEventHandlerQuickFix.class);
    private static final Map<String, String> EVENT_TYPE_MAP = buildEventTypeMap();

    private final String controllerFqn;
    private final String methodName;
    private final String eventAttributeName;

    public CreateEventHandlerQuickFix(@NotNull String controllerFqn,
                                      @NotNull String methodName,
                                      @NotNull String eventAttributeName) {
        this.controllerFqn = controllerFqn;
        this.methodName = methodName;
        this.eventAttributeName = eventAttributeName;
    }

    @Override
    public @NotNull String getFamilyName() {
        String simpleCtrl = controllerFqn.contains(".")
                ? controllerFqn.substring(controllerFqn.lastIndexOf('.') + 1)
                : controllerFqn;
        return FxToolsBundle.message("quickfix.create.event.handler", methodName, simpleCtrl);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass controllerClass = JavaPsiFacade.getInstance(project)
                .findClass(controllerFqn, GlobalSearchScope.projectScope(project));
        if (controllerClass == null) {
            return;
        }

        String eventTypeFqn = resolveEventType();
        String eventSimpleName = eventTypeFqn.substring(eventTypeFqn.lastIndexOf('.') + 1);

        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            try {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                String methodText = "@javafx.fxml.FXML\n"
                        + "private void " + methodName + "(" + eventTypeFqn + " event) {\n\n}";
                PsiMethod method = factory.createMethodFromText(methodText, controllerClass);

                PsiElement added = controllerClass.add(method);

                JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);

                PsiFile containingFile = controllerClass.getContainingFile();
                if (containingFile != null) {
                    VirtualFile vf = containingFile.getVirtualFile();
                    if (vf != null) {
                        FileEditorManager.getInstance(project).openTextEditor(
                                new OpenFileDescriptor(project, vf, added.getTextOffset()), true);
                    }
                }
            } catch (ProcessCanceledException pce) {
                throw pce;
            } catch (Exception ex) {
                LOG.error("Failed to create event handler method", ex);
            }
        });
    }

    @NotNull
    private String resolveEventType() {
        String eventType = EVENT_TYPE_MAP.get(eventAttributeName.toLowerCase());
        if (eventType != null) {
            return eventType;
        }
        return "javafx.event.Event";
    }

    private static Map<String, String> buildEventTypeMap() {
        Map<String, String> map = new HashMap<>();

        // ActionEvent
        map.put("onaction", "javafx.event.ActionEvent");

        // MouseEvent
        String mouseEvent = "javafx.scene.input.MouseEvent";
        map.put("onmouseclicked", mouseEvent);
        map.put("onmousepressed", mouseEvent);
        map.put("onmousereleased", mouseEvent);
        map.put("onmouseentered", mouseEvent);
        map.put("onmouseexited", mouseEvent);
        map.put("onmousemoved", mouseEvent);
        map.put("onmousedragged", mouseEvent);
        map.put("ondragdetected", mouseEvent);

        // DragEvent
        String dragEvent = "javafx.scene.input.DragEvent";
        map.put("ondragover", dragEvent);
        map.put("ondragdropped", dragEvent);
        map.put("ondragentered", dragEvent);
        map.put("ondragexited", dragEvent);
        map.put("ondragdone", dragEvent);

        // KeyEvent
        String keyEvent = "javafx.scene.input.KeyEvent";
        map.put("onkeypressed", keyEvent);
        map.put("onkeyreleased", keyEvent);
        map.put("onkeytyped", keyEvent);

        // ScrollEvent
        map.put("onscroll", "javafx.scene.input.ScrollEvent");

        // ContextMenuEvent
        map.put("oncontextmenurequested", "javafx.scene.input.ContextMenuEvent");

        // SwipeEvent
        String swipeEvent = "javafx.scene.input.SwipeEvent";
        map.put("onswipeup", swipeEvent);
        map.put("onswipedown", swipeEvent);
        map.put("onswipeleft", swipeEvent);
        map.put("onswiperight", swipeEvent);

        // TouchEvent
        String touchEvent = "javafx.scene.input.TouchEvent";
        map.put("ontouchpressed", touchEvent);
        map.put("ontouchreleased", touchEvent);
        map.put("ontouchmoved", touchEvent);
        map.put("ontouchstationary", touchEvent);

        // ZoomEvent
        String zoomEvent = "javafx.scene.input.ZoomEvent";
        map.put("onzoom", zoomEvent);
        map.put("onzoomstarted", zoomEvent);
        map.put("onzoomfinished", zoomEvent);

        // RotateEvent
        String rotateEvent = "javafx.scene.input.RotateEvent";
        map.put("onrotate", rotateEvent);
        map.put("onrotatestarted", rotateEvent);
        map.put("onrotatefinished", rotateEvent);

        return map;
    }
}
