package com.itcodebox.fxtools.utils;

import com.intellij.util.ui.JBUI;
import javafx.collections.ObservableList;

import java.awt.*;
import java.net.URL;

/**
 * @author LeeWyatt
 */
public class CustomUIUtil {
    public static final Insets EMPTY_INSETS = JBUI.emptyInsets();

    public static void addStylesheets(ObservableList<String> stylesheets,String... names) {
        if (stylesheets == null || names.length == 0) {
            return;
        }
        for (String name : names) {
            URL resource = CustomUIUtil.class.getResource(name);
            if (resource != null) {
                stylesheets.add(resource.toExternalForm());
            }
        }
    }
}
