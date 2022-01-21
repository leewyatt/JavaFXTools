package com.itcodebox.fxtools.utils;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author LeeWyatt
 */
public interface PluginConstant {
    String NotFount = "NotFound";
    String CollectionSvg = "M11.8,12.1c0.3-0.1,0.5-0.3,0.5-0.7l-0.2-1.3l3.3-3.3c0.2-0.2,0.2-0.4,0.1-0.6S15.2,5.8,15,5.8l-4.5-0.7l-2-4.3 " +
            " C8.4,0.6,8.2,0.5,8,0.5S7.6,0.6,7.5,0.8l-2,4.3L1,5.8C0.8,5.8,0.6,6,0.5,6.2s0,0.4,0.1,0.6l3.3,3.3l-0.8,4.7c0,0.2,0.1,0.4,0.2,0.6 " +
            " c0.1,0.1,0.2,0.1,0.3,0.1c0.1,0,0.2,0,0.3-0.1l4-2.2L9.4,14c0.3,0.2,0.6,0.1,0.8-0.2c0.2-0.3,0.1-0.6-0.2-0.8l-1.7-0.9 " +
            " c-0.2-0.1-0.4-0.1-0.6,0l-3.2,1.8L5.1,10c0-0.2,0-0.4-0.2-0.5L2.3,6.8l3.7-0.6c0.2,0,0.4-0.2,0.4-0.3L8,2.4l1.6,3.4 " +
            " c0.1,0.2,0.2,0.3,0.4,0.3l3.7,0.6L11,9.5c-0.1,0.1-0.2,0.3-0.2,0.5l0.3,1.6C11.2,11.9,11.5,12.1,11.8,12.1z M14.9,12.9h-0.9V12 " +
            " c0-0.3-0.3-0.6-0.6-0.6s-0.6,0.3-0.6,0.6v0.9H12c-0.3,0-0.6,0.3-0.6,0.6s0.3,0.6,0.6,0.6h0.9v0.9c0,0.3,0.3,0.6,0.6,0.6 " +
            " s0.6-0.3,0.6-0.6v-0.9h0.9c0.3,0,0.6-0.3,0.6-0.6S15.2,12.9,14.9,12.9z";
    String CopySVG = "M11,3 L4,3 L4,11 L2,11 L2,1 L11,1 L11,3 Z M5,4 L14,4 L14,14 L5,14 L5,4 Z M7,6 L7,7 L12,7 L12,6 L7,6 Z M7,10 L7,11 L12,11 L12,10 L7,10 Z M7,8 L7,9 L12,9 L12,8 L7,8 Z";
    String AddSVG = "M6.7,1.5h2.6v13H6.7V1.5z M14.5,6.7v2.6h-13V6.7H14.5z";
    String OpenSVG = "M4.32342122,7 L2,11.0147552 L2,7 L2,5 L2,3 L6.60006714,3 L7.75640322,5 L14,5 L14,7 L4.32342122,7 Z M4.89129639,8 L16,8 L13.1082845,13 L2.00248718,13 L4.89129639,8 Z";
    String RestSVG = "M8 1.99998C7.2056 1.99539 6.4182 2.14861 5.68349 2.45074C4.94877 2.75286 4.28137 3.19788 3.72 3.75998L2 1.99998V6.99998H7L5.15 5.18998C5.51971 4.80897 5.96296 4.50703 6.45287 4.30247C6.94277 4.09791 7.46913 3.995 8 3.99998C8.73705 4.00004 9.45976 4.20374 10.0883 4.58861C10.7169 4.97348 11.227 5.52454 11.5621 6.18097C11.8973 6.8374 12.0446 7.57368 11.9878 8.30854C11.9309 9.0434 11.6721 9.74826 11.2399 10.3453C10.8078 10.9424 10.2191 11.4084 9.53875 11.692C8.85844 11.9756 8.113 12.0657 7.38471 11.9524C6.65642 11.8391 5.9736 11.5268 5.41161 11.0499C4.84963 10.573 4.43033 9.95012 4.2 9.24998L2.3 9.86998C2.64466 10.9208 3.27303 11.8559 4.11571 12.572C4.95839 13.2881 5.98259 13.7574 7.07522 13.9281C8.16784 14.0988 9.28638 13.9641 10.3073 13.5391C11.3282 13.114 12.2118 12.4151 12.8605 11.5195C13.5092 10.6238 13.8977 9.56633 13.9832 8.46376C14.0687 7.3612 13.8478 6.25646 13.3449 5.27154C12.842 4.28663 12.0767 3.45985 11.1335 2.88252C10.1903 2.30519 9.10587 1.99977 8 1.99998V1.99998Z";

    String USER_HOME_PATH = System.getProperty("user.home");

    Path PROJECT_DB_DIRECTORY_PATH = Paths.get(USER_HOME_PATH, ".ideaJavaFXTools");
    Path TEMP_DIRECTORY_PATH = PROJECT_DB_DIRECTORY_PATH.resolve("temp_images");

    String NOTIFICATION_CLEAR_CACHE = "JavaFXTools Clear Cache";

    /**
     * 是否正在清理缓存
     */
    AtomicBoolean IsClearing = new AtomicBoolean(false);

    String OracleJavaClientPage = "https://docs.oracle.com/javase/8/javase-clienttechnologies.htm";
    /**
     * prefix+jdkVersion+suffix ==> Page
     * jdk 8,11-16
     */
    String openjfxHome = "https://openjfx.io/index.html";
    String openjfxPrefix = "https://openjfx.io/javadoc/";
    String CssDocSuffix = "/javafx.graphics/javafx/scene/doc-files/cssref.html";
    String fxmlDocSuffix = "/javafx.fxml/javafx/fxml/doc-files/introduction_to_fxml.html";
    String css8 = "https://docs.oracle.com/javase/8/javafx/api/javafx/scene/doc-files/cssref.html";
    String fxml8 = "https://docs.oracle.com/javase/8/javafx/api/javafx/fxml/doc-files/introduction_to_fxml.html";
    String api8 = "https://docs.oracle.com/javase/8/javafx/api/";
    /**
     * javafx scene_builder
     */
    String Gluonhq = "https://gluonhq.com/products/scene-builder/";

    Font textDefaultFont = Font.font(16);
    Font boldFont = Font.font(java.awt.Font.DIALOG, FontWeight.BOLD, 16);
    Font normalFont = Font.font(java.awt.Font.DIALOG, 15);
    String newLine = System.lineSeparator();
}
