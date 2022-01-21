package com.itcodebox.fxtools.components.fx;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.IconUtil;
import com.itcodebox.fxtools.components.fx.paintpicker.DoubleTextField;
import com.itcodebox.fxtools.components.fx.stage.FullSvgStage;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.utils.CustomUIUtil;
import com.itcodebox.fxtools.utils.CustomUtil;
import com.itcodebox.fxtools.utils.PaintConvertUtil;
import com.itcodebox.fxtools.utils.PluginConstant;
import com.itcodebox.fxtools.utils.SVGs.SVGUtil;
import com.itcodebox.fxtools.utils.SVGs.SvgXml;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import net.miginfocom.layout.CC;
import org.jetbrains.annotations.NotNull;
import org.tbee.javafx.scene.layout.MigPane;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * @author LeeWyatt
 */
public class FXSvgPane extends HBox {

    private final TabPane rightTabPane = new TabPane();
    private final SVGPath svgPathNode = new SVGPath();
    private final StackPane svgPathPane = new StackPane();
    private final TextArea svgPathTextArea = new TextArea();
    private final TextField svgWidthTextField = new TextField();
    private final TextField svgHeightTextField = new TextField();
    private final DoubleTextField svgScaleTextField = new DoubleTextField();
    private final TextField xmlWidthTextField = new TextField();
    private final TextField xmlHeightTextField = new TextField();
    private final DoubleTextField xmlScaleTextField = new DoubleTextField();
    private final TextField pathTextField = new TextField();
    private final TextField fileTextField = new TextField();
    private final ImageView imageView = new ImageView();
    private final TextArea xmlTextArea = new TextArea();
    private final Label xmlInfoLabel = new Label("SVG File");
    private final Label svgSizeInfoLabel = new Label("JavaFX Path");
    private final Label svgPathNumInfoLabel = new Label("");

    private final CheckBox xmlCheckBox2 = new CheckBox("2x");
    private final CheckBox xmlCheckBox3 = new CheckBox("3x");
    private final CheckBox xmlCheckBox4 = new CheckBox("Disabled");
    private final CheckBox svgCheckBox2 = new CheckBox("2x");
    private final CheckBox svgCheckBox3 = new CheckBox("3x");
    private final CheckBox svgCheckBox4 = new CheckBox("Disabled");
    private final HBox svgScaleTypeBox = new HBox(5, svgCheckBox2, svgCheckBox3, svgCheckBox4);
    private final ComboBox<String> svgDataType = new ComboBox<>(FXCollections.observableArrayList("path", "javafx code", "png", "jpg", "gif"));
    private final ComboBox<String> xmlDataType = new ComboBox<>(FXCollections.observableArrayList("png", "jpg", "gif"));
    private String lastFilePath;
    private double xmlScaleNow = 1.0;

    public FXSvgPane(Project project) {
        getStyleClass().add("svg-pane-root");
        getChildren().add(createLeftPane());
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        getChildren().add(region);
        getChildren().add(createRightPane());
        CustomUIUtil.addStylesheets(getStylesheets(), "/css/svgPane.css");

    }

    private VBox createLeftPane() {
        VBox box = new VBox(12);

        StackPane imageViewPane = new StackPane(imageView);
        imageViewPane.getStyleClass().add("image-view-pane");

        imageViewPane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                String path = fileTextField.getText();
                if (!path.isEmpty()) {
                    try {
                        File file = new File(path);
                        if (file.exists()) {
                            BrowserUtil.browse(file);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Tab webViewTab = new Tab("Browser View", imageViewPane);

        xmlTextArea.setEditable(false);
        xmlTextArea.setWrapText(true);
        StackPane sourcePane = new StackPane(xmlTextArea);
        Tab sourceTab = new Tab("Source Code", sourcePane);
        TabPane tabPane = new TabPane(webViewTab, sourceTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.prefWidthProperty().bind(this.widthProperty().divide(2).subtract(10));
        tabPane.maxHeightProperty().bind(tabPane.prefWidthProperty());
        rightTabPane.prefWidthProperty().bind(tabPane.widthProperty());
        rightTabPane.prefHeightProperty().bind(tabPane.heightProperty());
        rightTabPane.minWidthProperty().bind(tabPane.widthProperty());
        rightTabPane.minHeightProperty().bind(tabPane.heightProperty());

        tabPane.setMinSize(238, 238);
        box.getChildren().add(tabPane);

        fileTextField.setEditable(false);
        SVGPath openSvg = new SVGPath();
        openSvg.setContent(PluginConstant.OpenSVG);
        Button openBtn = new Button("", openSvg);
        openBtn.getStyleClass().add("icon-btn-blue");

        openBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG", "*.svg", "*.SVG"));
            if (lastFilePath != null) {
                File initDir = new File(lastFilePath).getParentFile();
                if (initDir.exists()) {
                    fileChooser.setInitialDirectory(initDir);
                }
            }
            File file = fileChooser.showOpenDialog(FXSvgPane.this.getScene().getWindow());
            if (file != null) {
                setXMLData(file);
            }
        });

        xmlWidthTextField.setEditable(false);
        xmlHeightTextField.setEditable(false);
        pathTextField.setEditable(false);

        HBox openBox = new HBox(2, fileTextField, openBtn);
        openBox.setAlignment(Pos.CENTER_LEFT);
        openBox.prefWidthProperty().bind(xmlWidthTextField.widthProperty());

        Button copyBtn = createCopyBtn();
        xmlDataType.getSelectionModel().select(0);
        copyBtn.setOnAction(e -> {
            if (fileTextField.getText().trim().isEmpty()) {
                return;
            }
            CustomUtil.copyToClipboard(fileTextField.getText(), xmlDataType.getSelectionModel().getSelectedItem(), xmlScaleNow, xmlCheckBox2.isSelected(), xmlCheckBox3.isSelected(), xmlCheckBox4.isSelected());
        });
        HBox copyBox = new HBox(2, xmlDataType, copyBtn);
        copyBox.setAlignment(Pos.CENTER_LEFT);
        xmlDataType.prefWidthProperty().bind(fileTextField.widthProperty());

        HBox scaleTypeBox = new HBox(5, xmlCheckBox2, xmlCheckBox3, xmlCheckBox4);
        scaleTypeBox.setAlignment(Pos.CENTER);

        SVGPath svgPath = new SVGPath();
        svgPath.setContent(PluginConstant.RestSVG);
        Button resetScaleBtn = new Button("", svgPath);
        resetScaleBtn.getStyleClass().add("icon-btn");
        resetScaleBtn.setOnAction(e -> {
            if (imageView.getImage() == null) {
                xmlScaleTextField.setText("");
                return;
            }

            xmlScaleTextField.setText("1");
            if (Double.compare(xmlScaleNow, 1.0) != 0) {
                xmlScaleNow = 1.0;
                changXMLNodeScale(CustomUtil.loadImage(fileTextField.getText(), (float) 1.0));
            }

        });
        HBox scaleBox = new HBox(xmlScaleTextField, resetScaleBtn);
        scaleBox.setAlignment(Pos.CENTER_LEFT);
        scaleBox.prefWidthProperty().bind(xmlWidthTextField.widthProperty());
        xmlScaleTextField.setOnAction(e -> {
            if (imageView.getImage() == null) {
                xmlScaleTextField.setText("");
                return;
            }
            String scaleText = xmlScaleTextField.getText();
            String scaleStr = scaleText.trim();
            if (scaleStr.isEmpty()) {
                svgScaleTextField.setText("1");
            }
            double scale = Double.parseDouble(scaleText);
            if (Double.compare(scale, xmlScaleNow) != 0) {
                xmlScaleNow = scale;
                changXMLNodeScale(CustomUtil.loadImage(fileTextField.getText(), (float) scale));
            }
        });
        MigPane pane = new MigPane();
        pane.add(xmlInfoLabel, new CC().span(2).wrap());
        pane.add(new Label("File:"), new CC().gapRight("5"));
        pane.add(openBox, "wrap");
        pane.add(new Label("Data:"));
        pane.add(copyBox, "wrap");
        pane.add(new Label("Image:"));
        pane.add(scaleTypeBox, "wrap");
        pane.add(new Label("Scale:"));
        pane.add(scaleBox, "wrap");
        pane.add(new Label("Width:"));
        pane.add(xmlWidthTextField, "wrap");
        pane.add(new Label("Height:"));
        pane.add(xmlHeightTextField, "wrap");
        box.getChildren().add(pane);
        return box;
    }

    private void saveIcon(Icon scaledIcon, String name) {
        BufferedImage image = IconUtil.toBufferedImage(scaledIcon, true);
        File file = new File("d:\\" + name);
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void setXMLData(File file) {
        xmlScaleTextField.setText("1");
        xmlScaleNow = 1.0;
        Image image = CustomUtil.loadImage(file, 1.0f);
        changXMLNodeScale(image);
        if (image != null) {
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            xmlInfoLabel.setText(String.format("Original Size: %dx%d", w, h));
        }

        SvgXml svgXml = SVGUtil.getSvgPath(file);
        String svgPath = svgXml.getSvgPath();
        svgPathNode.setContent(svgPath);
        svgPathNode.setScaleX(1.0);
        svgPathNode.setScaleY(1.0);

        svgScaleTextField.setText("1.0");
        svgPathTextArea.setText(svgPath);
        double w = svgPathNode.getLayoutBounds().getWidth();
        double h = svgPathNode.getLayoutBounds().getHeight();
        svgWidthTextField.setText(String.format("%.1f", w));
        svgHeightTextField.setText(String.format("%.1f", h));
        svgSizeInfoLabel.setText(String.format("; Original Size: %.1fx%.1f", w, h));
        String pathCount = svgXml.getPathCount();
        svgPathNumInfoLabel.setText("Path: "+ pathCount);
        if("Error".equals(pathCount)|| "0".equals(pathCount)){
            svgPathNumInfoLabel.setTextFill(Color.DARKGOLDENROD);
        }else {
            svgPathNumInfoLabel.setTextFill(Color.BLACK);
        }

        lastFilePath = file.getAbsolutePath();
        fileTextField.setText(lastFilePath);
        fileTextField.positionCaret(lastFilePath.length());
        StringBuilder sb = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(lastFilePath))) {
            // 输出重定向
            stream.forEach(line -> sb.append(line).append(System.lineSeparator()));
            xmlTextArea.setText(sb.toString());
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private VBox createRightPane() {
        VBox box = new VBox(12);
        svgPathPane.getChildren().setAll(svgPathNode);

        svgPathPane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                FullSvgStage svgStage = FullSvgStage.getInstance();
                svgStage.setSvgContent(svgPathNode.getContent(), svgPathNode.getFill(), svgPathNode.getScaleX());
                svgStage.show();
            }
        });
        svgPathPane.setOnScroll(event -> {
            if (!svgPathNode.getContent().isEmpty()) {
                double value;
                if (event.getDeltaY() > 0) {
                    value = svgPathNode.getScaleX() * 1.1;
                } else {
                    value = svgPathNode.getScaleX() * 0.9;
                }
                svgPathNode.setScaleX(value);
                svgPathNode.setScaleY(value);
                double w = svgPathNode.getLayoutBounds().getWidth() * value;
                double h = svgPathNode.getLayoutBounds().getHeight() * value;
                svgWidthTextField.setText(String.format("%.1f", w));
                svgHeightTextField.setText(String.format("%.1f", h));
            }
        });

        svgPathPane.getStyleClass().add("svg-path-pane");
        Tab svgTab = new Tab("JavaFX View", svgPathPane);

        svgPathTextArea.setWrapText(true);
        svgPathTextArea.setEditable(false);
        StackPane sourcePane = new StackPane(svgPathTextArea);
        Tab sourceTab = new Tab("Source Code", sourcePane);
        rightTabPane.getTabs().addAll(svgTab, sourceTab);
        rightTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        box.getChildren().add(rightTabPane);
        svgDataType.getSelectionModel().select(0);
        Button copyBtn = createCopyBtn();
        svgDataType.getSelectionModel().selectedItemProperty().addListener((ob, ov, nv) -> svgScaleTypeBox.setDisable("path".equals(nv)||"javafx code".equals(nv)));
        copyBtn.setOnAction(e -> {
            String fieldText = fileTextField.getText();
            if (fieldText.isEmpty()) {
                return;
            }
            String selectedItem = svgDataType.getSelectionModel().getSelectedItem();
            if ("path".equalsIgnoreCase(selectedItem)) {
                CustomUtil.copyToClipboard(svgPathNode.getContent());
                return;
            }
            if ("javafx code".equalsIgnoreCase(selectedItem)) {
                Paint fill = svgPathNode.getFill();
                double scaleX = svgPathNode.getScaleX();
                String paintStr = PaintConvertUtil.toFXJavaCode(fill, true);
                String format;
                if (Color.BLACK.equals(fill) && Double.compare(scaleX, 1.0)==0) {
                     format = String.format("SVGPath svgPath = new SVGPath();%ssvgPath.setContent(\"%s\");",
                            System.lineSeparator(), svgPathNode.getContent());
                }else if(Color.BLACK.equals(fill)){
                    format = String.format("SVGPath svgPath = new SVGPath();%ssvgPath.setContent(\"%s\");" +
                                    "%ssvgPath.setScaleX(%s);%ssvgPath.setScaleY(%s);",
                            System.lineSeparator(), svgPathNode.getContent(), System.lineSeparator(),
                            CustomUtil.roundingDouble(scaleX, 2)
                            , System.lineSeparator(), CustomUtil.roundingDouble(svgPathNode.getScaleY(), 2));
                }else if(Double.compare(scaleX, 1.0)==0){
                    format = String.format("SVGPath svgPath = new SVGPath();%ssvgPath.setContent(\"%s\");" +
                                    "%s%s%ssvgPath.setFill(paint);",
                            System.lineSeparator(), svgPathNode.getContent(), System.lineSeparator(), paintStr, System.lineSeparator());
                }else{
                    format = String.format("SVGPath svgPath = new SVGPath();%ssvgPath.setContent(\"%s\");" +
                                    "%s%s%ssvgPath.setFill(paint);%ssvgPath.setScaleX(%s);%ssvgPath.setScaleY(%s);",
                            System.lineSeparator(), svgPathNode.getContent(), System.lineSeparator(), paintStr, System.lineSeparator(), System.lineSeparator(),
                            CustomUtil.roundingDouble(scaleX, 2)
                            , System.lineSeparator(), CustomUtil.roundingDouble(svgPathNode.getScaleY(), 2));
                }
                CustomUtil.copyToClipboard(format);
                return;
            }
            //图片的复制
            CustomUtil.copyToClipboard(svgPathNode, fieldText, selectedItem, svgCheckBox2.isSelected(), svgCheckBox3.isSelected(), svgCheckBox4.isSelected());

        });

        svgPathNode.scaleXProperty().addListener((ob, ov, nv) -> svgScaleTextField.setText(nv.doubleValue() + ""));
        svgScaleTextField.setOnAction(e -> {
            if (svgPathNode.getContent().isEmpty()) {
                svgScaleTextField.setText("");
                return;
            }
            String scaleStr = svgScaleTextField.getText().trim();
            if (scaleStr.isEmpty()) {
                svgScaleTextField.setText("1");
            }
            double scale = Double.parseDouble(svgScaleTextField.getText());
            changSvgNodeScale(scale);
        });
        ImageView colorPickerImageView = new ImageView();
        URL resource = getClass().getResource("/images/colors-btn.png");
        if (resource != null) {
            colorPickerImageView.setImage(new Image(resource.toExternalForm()));
        }
        Button openColorPickerBtn = new Button("Paint Picker", colorPickerImageView);
        PaintPickerStage colorPickerStage = PaintPickerStage.getInstance();
        svgPathNode.setFill(colorPickerStage.getPaintPicker().getPaintProperty());
        colorPickerStage.getPaintPicker().paintProperty().addListener((ob, ov, nv) -> {
            svgPathNode.setFill(nv);
        });

        openColorPickerBtn.setOnAction(e -> {
            colorPickerStage.show();
        });

        SVGPath svgPath = new SVGPath();
        svgPath.setContent(PluginConstant.RestSVG);
        Button resetScaleBtn = new Button("", svgPath);
        resetScaleBtn.getStyleClass().add("icon-btn");
        resetScaleBtn.setOnAction(e -> {
            if (svgPathNode.getContent().isEmpty()) {
                return;
            }
            changSvgNodeScale(1.0);
        });

        svgWidthTextField.setEditable(false);
        svgHeightTextField.setEditable(false);
        openColorPickerBtn.prefWidthProperty().bind(svgWidthTextField.widthProperty());

        HBox copyBox = new HBox(2, svgDataType, copyBtn);
        copyBox.setAlignment(Pos.CENTER_LEFT);
        svgDataType.prefWidthProperty().bind(xmlDataType.widthProperty());

        svgScaleTypeBox.setAlignment(Pos.CENTER);
        svgScaleTypeBox.setDisable(true);
        HBox scaleBox = new HBox(svgScaleTextField, resetScaleBtn);
        scaleBox.setAlignment(Pos.CENTER_LEFT);
        scaleBox.prefWidthProperty().bind(svgWidthTextField.widthProperty());
        HBox infoBox = new HBox(2,svgPathNumInfoLabel,svgSizeInfoLabel);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        MigPane pane = new MigPane();
        pane.add(infoBox, new CC().span(2).wrap());
        pane.add(new Label("Color:"));
        pane.add(openColorPickerBtn, "wrap");
        pane.add(new Label("Data:"), new CC().gapRight("5"));
        pane.add(copyBox, "wrap");
        //TODO path数量和 宽高信息显示在一起
        //pane.add(new Label("Path:"));
        //pane.add(pathTextField, "wrap");
        pane.add(new Label("Image:"));
        pane.add(svgScaleTypeBox, "wrap");
        pane.add(new Label("Scale:"));
        pane.add(scaleBox, "wrap");
        pane.add(new Label("Width:"));
        pane.add(svgWidthTextField, "wrap");
        pane.add(new Label("Height:"));
        pane.add(svgHeightTextField, "wrap");
        box.getChildren().add(pane);
        return box;
    }

    @NotNull
    private SnapshotParameters getSnapshotParameters(Color fill) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(fill);
        return parameters;
    }

    private void changSvgNodeScale(double scale) {
        svgPathNode.setScaleY(scale);
        svgPathNode.setScaleX(scale);
        double w = svgPathNode.getLayoutBounds().getWidth() * scale;
        double h = svgPathNode.getLayoutBounds().getHeight() * scale;
        svgWidthTextField.setText(String.format("%.1f", w));
        svgHeightTextField.setText(String.format("%.1f", h));
    }

    private void changXMLNodeScale(Image image) {
        if (image != null) {
            imageView.setImage(image);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            xmlWidthTextField.setText(w + "");
            xmlHeightTextField.setText(h + "");
        }
    }

    private Button createCopyBtn() {
        SVGPath svgPath = new SVGPath();
        svgPath.setContent(PluginConstant.CopySVG);
        Button button = new Button("", svgPath);
        button.getStyleClass().add("icon-btn");
        return button;
    }

}
