package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.project.Project;
import com.itcodebox.fxtools.components.fx.FXSvgPane;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

/**
 * @author LeeWyatt
 */
public class SvgToolPanel extends JFXPanel {
    public SvgToolPanel(Project project) {
        Platform.runLater(() -> setScene(new Scene(new FXSvgPane(project))));
    }
}
