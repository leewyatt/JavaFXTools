package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.openapi.project.Project;
import com.itcodebox.fxtools.components.fx.FXFontPane;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

/**
 * @author LeeWyatt
 */
public class FontToolPanel extends JFXPanel {
    public FontToolPanel(Project project) {
        Platform.runLater(() -> setScene(new Scene(new FXFontPane(project))));
    }
}
