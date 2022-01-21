package com.itcodebox.fxtools;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.itcodebox.fxtools.components.fx.paintpicker.PaintPicker;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.components.swing.dialog.AboutDialog;
import com.itcodebox.fxtools.components.swing.panels.MainPanel;
import com.itcodebox.fxtools.utils.CustomColorUtil;
import com.sun.javafx.application.PlatformImpl;
import icons.PluginIcons;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author LeeWyatt
 */
public class ToolWindowFactoryImpl implements ToolWindowFactory, DumbAware {
    private Project project;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        //System.setProperty("prism.allowhidpi","false");

        //启动javaFX运行环境
        PlatformImpl.startup(() -> {
        });
        //设置 不要隐式退出 , 否则关闭了,javaFX运行环境就失效了
        Platform.setImplicitExit(false);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        MainPanel mainPanel = new MainPanel(project, toolWindow);
        Content content = contentFactory.createContent(mainPanel, "", false);
        toolWindow.getContentManager().addContent(content);
        toolWindow.setTitleActions(List.of(
                initAwtColorChooserAction(mainPanel),
                initFXColorPickerAction(),
                initAboutAction()
        ));
    }

    private AnAction initAboutAction() {
        return new DumbAwareAction("About","", PluginIcons.Other) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                new AboutDialog(project).show();
            }
        };
    }


    private AnAction initAwtColorChooserAction(Component component) {
        return new DumbAwareAction("Color Picker", "Idea color picker", AllIcons.Ide.Pipette) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ColorChooser.chooseColor(project, component, "Color Picker", null, false, listeners, true);
            }
        };
    }

    private final List<ColorPickerListener> listeners = List.of(new ColorPickerListener() {
        @Override
        public void colorChanged(Color color) {
            //onColorChanged(color);
        }

        @Override
        public void closed(@Nullable Color color) {
            onColorChanged(color);
        }

        private void onColorChanged(Color color) {
            if (color == null) {
                return;
            }
            Platform.runLater(() -> {
                javafx.scene.paint.Color paint = CustomColorUtil.convertToFXColor(color);
                PaintPickerStage stage = PaintPickerStage.getInstance();
                PaintPicker paintPicker = stage.getPaintPicker();
                //比较奇怪,需要先updateUI,然后再setPaint
                paintPicker.getColorPicker().updateUI(paint);
                paintPicker.setPaintProperty(paint);
            });
        }
    });

    private AnAction initFXColorPickerAction() {
        return new DumbAwareAction("JavaFX Paint Picker", "JavaFX paint picker", AllIcons.Actions.Colors) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                Platform.runLater(() -> {
                    PaintPickerStage stage = PaintPickerStage.getInstance();
                    if (!stage.isShowing()) {
                        stage.show();
                    }
                });
            }
        };
    }
}
