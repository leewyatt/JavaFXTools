package com.itcodebox.fxtools.components.fx;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.itcodebox.fxtools.components.fx.stage.PaintPickerStage;
import com.itcodebox.fxtools.utils.CustomUtil;
import com.itcodebox.fxtools.utils.PaintConvertUtil;
import com.itcodebox.fxtools.utils.PluginConstant;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static com.itcodebox.fxtools.utils.JavaFXToolsBundle.message;

/**
 * @author LeeWyatt
 */
public class FXFontPane extends BorderPane {

    private final Slider fontSizSlider = new Slider(0, 35, 20);
    private final TextField fontFamilyTextField = new TextField();
    private final TextField fontNameTextField = new TextField();
    private final TextField testTextField = new TextField(message("fontPane.test.text"));
    private final ListView<String> fontStyleListView = new ListView<>();
    private final ListView<String> fontFamilyView;
    private static final String REGULA_WEIGHT = "Regular";
    private static final String ITALIC_STYLE = "Italic";
    private static final String DEFAULT = "null";
    private final ComboBox<String> comboBoxDataType = new ComboBox<String>(FXCollections.observableArrayList("JavaFX Code", "Css", "Css Shortened"));
    private final CheckBox checkBoxWithPaint = new CheckBox("With paint");

    public FXFontPane(Project project) {
        getStyleClass().add("font-pane-root");

        setPadding(new Insets(5));
        URL cssResource = getClass().getResource("/css/fontPane.css");
        if (cssResource != null) {
            getStylesheets().add(cssResource.toExternalForm());
        }
        ObservableList<String> fonts = FXCollections.observableArrayList(Font.getFamilies());
        FilteredList<String> filteredList = new FilteredList<String>(fonts);

        //第1行, 中央字体与字形选择部分
        //1. 字体选择
        //搜索文本框
        TextField searchTextField = new TextField();
        filteredList.setPredicate(item -> true);
        Label fontLabel = new Label("Font");
        BorderPane searchPane = new BorderPane();
        searchPane.setPrefHeight(30);
        searchPane.setLeft(fontLabel);
        BorderPane.setAlignment(fontLabel, Pos.CENTER);
        searchPane.setCenter(searchTextField);
        //字体列表
        fontFamilyView = new ListView<>(filteredList);
        //fontFamilyView.setPrefHeight(230);
        //复制字体
        SVGPath copyFamily = getCopySvgPath();
        fontFamilyTextField.setEditable(false);
        Button btnCopyFamily = new Button("", copyFamily);
        btnCopyFamily.getStyleClass().add("icon-btn");
        btnCopyFamily.setOnAction(e -> CustomUtil.copyToClipboard(fontFamilyTextField.getText()));
        Label familyLabel = new Label("Family");
        BorderPane copyFamilyPane = new BorderPane();
        BorderPane.setAlignment(familyLabel, Pos.CENTER);
        copyFamilyPane.setLeft(familyLabel);
        copyFamilyPane.setCenter(fontFamilyTextField);
        copyFamilyPane.setRight(btnCopyFamily);
        VBox fontFamilyBox = new VBox(searchPane, fontFamilyView, copyFamilyPane);
        fontFamilyBox.setMinWidth(160);

        //2. 字形选择
        Label labelStyle = new Label("Posture");
        labelStyle.setPrefHeight(30);
        SVGPath copyName = getCopySvgPath();
        fontNameTextField.setEditable(false);
        Button btnCopyName = new Button("", copyName);
        btnCopyName.getStyleClass().add("icon-btn");
        btnCopyName.setOnAction(e -> CustomUtil.copyToClipboard(fontNameTextField.getText()));
        BorderPane copyNamePane = new BorderPane();
        Label nameLabel = new Label("Name");
        BorderPane.setAlignment(nameLabel, Pos.CENTER);
        nameLabel.setAlignment(Pos.CENTER);
        copyNamePane.setLeft(nameLabel);
        copyNamePane.setCenter(fontNameTextField);
        copyNamePane.setRight(btnCopyName);
        //fontStyleListView.setPrefHeight(230);
        VBox fontStyleBox = new VBox(labelStyle, fontStyleListView, copyNamePane);

        BorderPane centerPane = new BorderPane();
        centerPane.setCenter(fontFamilyBox);
        centerPane.setRight(fontStyleBox);
        BorderPane.setMargin(fontStyleBox, new Insets(0, 0, 0, 10));
        //第2行, 添加字体按钮
        SVGPath addSvgPath = new SVGPath();
        addSvgPath.setContent(PluginConstant.AddSVG);
        Button addFontBtn = new Button("Add Font", addSvgPath);
        addFontBtn.getStyleClass().add("icon-btn");
        addFontBtn.setOnAction(e -> addNewFonts(fonts, filteredList));

        ImageView colorPickerImageView = new ImageView();
        URL resource = getClass().getResource("/images/colors-btn.png");
        if (resource != null) {
            colorPickerImageView.setImage(new Image(resource.toExternalForm()));
        }
        Button openColorPickerBtn = new Button("Paint Picker", colorPickerImageView);
        PaintPickerStage pickerStage = PaintPickerStage.getInstance();
        openColorPickerBtn.setOnAction(e -> {
            pickerStage.show();
        });
        pickerStage.getPaintPicker().paintProperty().addListener((ob, ov, nv) -> {
            testTextField.setStyle("-fx-text-fill: " + PaintConvertUtil.toFXCssCode(nv));
        });
        testTextField.setStyle("-fx-text-fill: " + PaintConvertUtil.toFXCssCode(pickerStage.getPaintPicker().getPaintProperty()));

        Button copyCssCode = new Button("", getCopySvgPath());
        copyCssCode.getStyleClass().add("icon-btn");
        comboBoxDataType.getSelectionModel().select(0);
        checkBoxWithPaint.setSelected(true);
        HBox codeTypeBox = new HBox(2, checkBoxWithPaint, comboBoxDataType, copyCssCode);
        codeTypeBox.setAlignment(Pos.CENTER);
        copyCssCode.setOnAction(e -> copyCodeAction());

        HBox btnBox = new HBox(20, addFontBtn, openColorPickerBtn, codeTypeBox);
        VBox.setMargin(btnBox, new Insets(5, 0, 5, 0));
        btnBox.getStyleClass().add("btn-box");
        btnBox.setAlignment(Pos.CENTER_LEFT);

        //第3行
        fontSizSlider.setShowTickLabels(true);
        fontSizSlider.setShowTickMarks(true);
        fontSizSlider.setMajorTickUnit(5);
        fontSizSlider.setSnapToTicks(true);
        testTextField.setPrefHeight(132);
        testTextField.setFont(Font.font(fontSizSlider.getValue()));
        testTextField.setFocusTraversable(false);
        VBox topBox = new VBox(testTextField, fontSizSlider, btnBox);

        setTop(topBox);
        setCenter(centerPane);

        searchTextField.textProperty().addListener(e -> filterItems(searchTextField, filteredList));
        fontFamilyView.getSelectionModel().selectedItemProperty().addListener((ob, ov, nv) -> fontFamilyChanged(nv));
        fontStyleListView.getSelectionModel().selectedItemProperty().addListener((os, ov, nv) -> fontStyleChanged());
        fontSizSlider.valueProperty().addListener((os, ov, nv) -> fontStyleChanged());

    }

    private void copyCodeAction() {
        int selectedIndex = comboBoxDataType.getSelectionModel().getSelectedIndex();
        if (selectedIndex == 0) {
            copyJavaCodeAction();
        } else if (selectedIndex == 1) {
            copyCss();
        } else if (selectedIndex == 2) {
            copyShortenedCss();
        }

    }

    private void copyCss() {
        String newFontStyle = fontStyleListView.getSelectionModel().getSelectedItem();
        String cssCode;
        int fontSize = (int) fontSizSlider.getValue();

        String cssFontSize = "-fx-font-size: " + fontSize + ";";
        String paintCss = "";
        if (checkBoxWithPaint.isSelected()) {
            String paintStr = PaintConvertUtil.toFXCssCode(PaintPickerStage.getInstance().getPaintPicker().getPaintProperty());
            paintCss = String.format("%s-fx-text-fill: %s;%s-fx-fill: %s;", System.lineSeparator(), paintStr, System.lineSeparator(), paintStr);
        }

        if (newFontStyle != null) {
            String cssFontPosture = null;
            if (newFontStyle.endsWith(ITALIC_STYLE)) {
                cssFontPosture = "-fx-font-style: italic;";
            }
            String cssWeightName = null;
            String weightName = newFontStyle.replace(ITALIC_STYLE, "").trim();
            if (!weightName.isEmpty() && !REGULA_WEIGHT.equals(weightName)) {
                cssWeightName = "-fx-font-weight: " + weightName + ";";
            }

            String fontFamily = fontFamilyView.getSelectionModel().getSelectedItem().trim();
            //fontFamily = fontFamily.contains(" ")?"\""+fontFamily+"\"":fontFamily;
            String cssFontFamily = String.format("-fx-font-family: \"%s\";", fontFamily);
            StringBuilder sb = new StringBuilder();
            sb.append(cssFontFamily).append(System.lineSeparator());
            if (cssFontPosture != null) {
                sb.append(cssFontPosture).append(System.lineSeparator());
            }
            if (cssWeightName != null) {
                sb.append(cssWeightName).append(System.lineSeparator());
            }
            sb.append(cssFontSize);
            sb.append(paintCss);
            cssCode = sb.toString();
        } else {
            cssCode = String.format("-fx-font-size: %d;%s", fontSize, paintCss);
        }

        CustomUtil.copyToClipboard("/*If it is an external font, you need to load it; @font-face {...} */" + System.lineSeparator() + cssCode);
    }

    private void copyShortenedCss() {
        String newFontStyle = fontStyleListView.getSelectionModel().getSelectedItem();
        String cssCode;
        int fontSize = (int) fontSizSlider.getValue();
        String paintCss = "";
        if (checkBoxWithPaint.isSelected()) {
            String paintStr = PaintConvertUtil.toFXCssCode(PaintPickerStage.getInstance().getPaintPicker().getPaintProperty());
            paintCss = String.format("%s-fx-text-fill: %s;%s-fx-fill: %s;", System.lineSeparator(), paintStr, System.lineSeparator(), paintStr);
        }
        if (newFontStyle != null) {
            String strFontPosture = null;
            if (newFontStyle.endsWith(ITALIC_STYLE)) {
                strFontPosture = "italic";
            }
            String strWeightName = null;
            String weightName = newFontStyle.replace(ITALIC_STYLE, "").trim();
            if (!weightName.isEmpty() && !REGULA_WEIGHT.equals(weightName)) {
                strWeightName = weightName;
            }
            String fontFamily = "\"" + fontFamilyView.getSelectionModel().getSelectedItem().trim() + "\"";
            if (strFontPosture == null && strWeightName == null) {
                cssCode = String.format("-fx-font:%d %s;", fontSize, fontFamily);
            } else if (strFontPosture == null) {
                cssCode = String.format("-fx-font: %s %d %s;", strWeightName, fontSize, fontFamily);
            } else if (strWeightName == null) {
                cssCode = String.format("-fx-font: %s %d %s;", strFontPosture, fontSize, fontFamily);
            } else {
                cssCode = String.format("-fx-font: %s %s %d %s;", strFontPosture, strWeightName, fontSize, fontFamily);
            }

            cssCode += paintCss;
        } else {
            cssCode = String.format("-fx-font-size: %d;%s", fontSize, paintCss);
        }

        CustomUtil.copyToClipboard("/*If it is an external font, you need to load it; @font-face {...} */" + System.lineSeparator() + cssCode);
    }

    private void copyJavaCodeAction() {
        String newFontStyle = fontStyleListView.getSelectionModel().getSelectedItem();
        String javaCode;
        int fontSize = (int) fontSizSlider.getValue();
        String paintStr = "";
        if (checkBoxWithPaint.isSelected()) {
            Paint paint = PaintPickerStage.getInstance().getPaintPicker().getPaintProperty();
            paintStr = PaintConvertUtil.toFXJavaCode(paint, true);
        }
        if (newFontStyle != null) {
            String strFontPosture = DEFAULT;
            if (newFontStyle.endsWith(ITALIC_STYLE)) {
                strFontPosture = "FontPosture.ITALIC";
            }
            String strWeightName = DEFAULT;
            String weightName = newFontStyle.replace(ITALIC_STYLE, "").trim();
            if (!weightName.isEmpty() && !REGULA_WEIGHT.equals(weightName)) {
                strWeightName = "FontWeight.findByName(\"" + weightName + "\")";
            }
            String fontFamily = fontFamilyView.getSelectionModel().getSelectedItem();
            if (DEFAULT.equals(strFontPosture) && DEFAULT.equals(strWeightName)) {
                javaCode = String.format("Font font = Font.font(\"%s\",%d);", fontFamily, fontSize);
            } else if (DEFAULT.equals(strFontPosture)) {
                javaCode = String.format("Font font = Font.font(\"%s\",%s,%d);", fontFamily, strWeightName, fontSize);
            } else if (DEFAULT.equals(strWeightName)) {
                javaCode = String.format("Font font = Font.font(\"%s\",%s,%d);", fontFamily, strFontPosture, fontSize);
            } else {
                javaCode = String.format("Font font = Font.font(\"%s\",%s,%s,%d);", fontFamily, strWeightName, strFontPosture, fontSize);
            }
        } else {
            javaCode = String.format("Font font = Font.font(null, %d);", fontSize);
        }
        CustomUtil.copyToClipboard(String.format("%s%s%s%s", paintStr.isEmpty() ? "" : paintStr + System.lineSeparator(), "\t//If it is an external font, you need to load it; Font.loadFont(...);", System.lineSeparator(), javaCode));
    }

    @NotNull
    private SVGPath getCopySvgPath() {
        SVGPath copySvg = new SVGPath();
        copySvg.setContent(PluginConstant.CopySVG);
        copySvg.setFill(Color.web("#6E6E6E"));
        return copySvg;
    }

    private void filterItems(TextField searchTextField, FilteredList<String> filteredList) {
        filteredList.setPredicate((v)
                -> (searchTextField.getText() == null
                || searchTextField.getText().length() == 0 || v.toLowerCase().contains(searchTextField.getText().toLowerCase())));
    }

    private void fontFamilyChanged(String newValue) {
        if (newValue == null) {
            fontStyleListView.getItems().clear();
        } else {
            fontStyleListView.getItems().setAll(
                    Font.getFontNames(newValue).stream().map(s -> s.equals(newValue) ? REGULA_WEIGHT : s.replace(newValue, "").trim()).collect(Collectors.toList()));
            if (fontStyleListView.getItems().size() != 0) {
                fontStyleListView.getSelectionModel().select(0);
            }
        }
        fontFamilyTextField.setText(newValue == null ? "System" : newValue);
    }

    private void addNewFonts(ObservableList<String> fonts, FilteredList<String> filteredList) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ttf", "*.ttf", "*.TTF"));
        List<File> files = fileChooser.showOpenMultipleDialog(FXFontPane.this.getScene().getWindow());
        if (files == null) {
            return;
        }
        boolean containsNull = false;
        for (File file : files) {
            if (file != null) {
                try {
                    Font font = Font.loadFont(file.toURI().toURL().toExternalForm(), fontSizSlider.getValue());
                    if (font != null) {
                        String family = font.getFamily();
                        if (!filteredList.contains(family)) {
                            fonts.add(family);
                            int index = fonts.size() - 1;
                            fontFamilyView.getSelectionModel().select(index);
                            fontFamilyView.scrollTo(index);
                        }
                    } else {
                        containsNull = true;
                    }
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                }
            }
        }
        //如果部分字体加载失败 ,那么进行提示
        if (containsNull) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showMessageDialog(message("fontPane.loadFont.errorMsg"), message("fontPane.loadFont.errorTitle"), null);
            });
        }
    }

    private void fontStyleChanged() {
        Font font;
        String newFontStyle = fontStyleListView.getSelectionModel().getSelectedItem();
        if (newFontStyle != null) {
            FontPosture fp = null;
            if (newFontStyle.endsWith(ITALIC_STYLE)) {
                fp = FontPosture.ITALIC;
            }
            String weightName = newFontStyle.replace(ITALIC_STYLE, "").trim();
            FontWeight fw = FontWeight.findByName(weightName);
            font = Font.font(fontFamilyView.getSelectionModel().getSelectedItem(), fw, fp, fontSizSlider.getValue());
        } else {
            font = Font.font(null, fontSizSlider.getValue());
        }
        testTextField.setFont(font);
        fontNameTextField.setText(font.getName());
    }
}
