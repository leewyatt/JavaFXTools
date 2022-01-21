package com.itcodebox.fxtools.components.fx.stage;

import com.itcodebox.fxtools.components.fx.paintpicker.PaintPicker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Paint;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;

/**
 * @author LeeWyatt
 */
public class PaintPickerStage extends Stage {

    private final PaintPicker paintPicker = new PaintPicker(new PaintPicker.Delegate() {
        @Override
        public void handleError(String s, Object... objects) {
            System.out.println(s);
        }
    });

    public PaintPicker getPaintPicker() {
        return paintPicker;
    }

    private PaintPickerStage(Paint paint) {
        if (paint != null) {
            paintPicker.setPaintProperty(paint);
        }
        BorderPane rootPane = new BorderPane();
        rootPane.getStyleClass().add("paint-root-pane");
        rootPane.setPadding(new Insets(6));
        rootPane.setCenter(paintPicker);
        URL resource = getClass().getResource("/images/colors-icon.png");
        if (resource != null) {
            this.getIcons().add(new Image(resource.toExternalForm()));
        }
        Scene scene = new Scene(rootPane);
        scene.getStylesheets().add(getClass().getResource("/css/paintRootPane.css").toExternalForm());

        this.setScene(scene);
        this.setTitle("JavaFX Paint Picker");
        this.initModality(Modality.APPLICATION_MODAL);
        this.setResizable(false);
        this.setAlwaysOnTop(true);
    }

    private PaintPickerStage() {
        this(null);
    }


    private static volatile PaintPickerStage singleton;

    public static PaintPickerStage getInstance() {
        if (singleton == null) {
            synchronized (PaintPickerStage.class) {
                if (singleton == null) {
                    singleton = new PaintPickerStage();
                }
            }
        }
        return singleton;
    }
}
