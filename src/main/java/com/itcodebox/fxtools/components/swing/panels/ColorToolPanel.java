package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.itcodebox.fxtools.components.ShowPaintPicker;
import com.itcodebox.fxtools.components.fx.paintpicker.PaintPicker;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import javafx.application.Platform;

import javax.swing.*;
import java.awt.*;

/**
 * @author LeeWyatt
 */
public class ColorToolPanel extends JPanel implements ShowPaintPicker {

    Project project;

    public ColorToolPanel(Project project, ToolWindow toolWindow) {
        this.project = project;
        setLayout(new BorderLayout());
        add(new ColorSchemePanel(this));
    }


    @Override
    public void show(javafx.scene.paint.Color paint) {
        Platform.runLater(() -> {
            PaintPickerStage stage = PaintPickerStage.getInstance();
            PaintPicker paintPicker = stage.getPaintPicker();
            //比较奇怪,需要先updateUI,然后再setPaint
            paintPicker.getColorPicker().updateUI(paint);
            paintPicker.setPaintProperty(paint);
            stage.show();
            stage.requestFocus();
        });
    }

    @Override
    public void show(javafx.scene.paint.LinearGradient paint) {
        Platform.runLater(() -> {
            PaintPickerStage stage = PaintPickerStage.getInstance();
            PaintPicker paintPicker = stage.getPaintPicker();
            //比较奇怪,需要set两次
            paintPicker.setPaintProperty(paint);
            paintPicker.setPaintProperty(paint);
            stage.show();
            stage.requestFocus();
        });
    }

}
