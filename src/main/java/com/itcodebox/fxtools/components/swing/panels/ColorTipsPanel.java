package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.ui.components.JBTextArea;

/**
 * @author LeeWyatt
 */
public class ColorTipsPanel extends JBTextArea {

    public ColorTipsPanel() {
        //JPanel topPanel = new JPanel(new MigLayout(new LC().flowY().fill().gridGap("0!", "0!").insets("0")));
        //
        //topPanel.add(new TitledPanel("How to select colors on the Color Tools page") {
        //    @Override
        //    protected void addComponentToContentPane(JPanel contentPane) {
        //        JBTextField label = new JBTextField("Method 1. Move the mouse to the color block and double-click the left button to select the color."
        //                + System.lineSeparator() +
        //                "Method 2. Move the mouse to the color block, click the right button, and select the [Edit ...] menu item. "
        //        );
        //
        //    }
        //}, new CC().growX().pushX());


        setEditable(false);
        setLineWrap(true);
        setFont(getFont().deriveFont(14F));
        String newLine = System.lineSeparator();
        String content =
                "How to select colors on the Color Tools page ?" + newLine +
                        "  Method 1. Move the mouse to the color block and double-click the left button to select the color." + newLine +
                        "  Method 2. Move the mouse to the color block, click the right button, and select the [Choose/Edit ...] menu item." + newLine + newLine +
                        "Copyright: " + newLine +
                        "  [Color Scheme] Part of the data comes from the Internet. Due to mutual reprinting, no accurate source has been found. " +
                        "For the time being, it can only be traced back to the web course on the official website of Lanzhou University in 2002. " +
                        "If there is infringement, you can contact me to delete it." + newLine +
                        " Email: leewyatt7788@gmail.com" + newLine +
                        " Email: leewyatt@163.com" + newLine +
                        " JavaFX/Swing QQ群: 715598051 " ;

        setText(content);

    }
}
