package com.itcodebox.fxtools.components.swing.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.itcodebox.fxtools.components.swing.panels.TitledPanel;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.itcodebox.fxtools.utils.JavaFXToolsBundle.message;

/**
 * @author LeeWyatt
 */
public class AboutDialog extends DialogWrapper {
    private static final int DEFAULT_WIDTH = 580;
    private static final int DEFAULT_HEIGHT = 360;
    private Project project;
    public AboutDialog(Project project) {
        super(true);
        this.project = project;
        setTitle("About");
        setOKButtonText("Close");
        getRootPane().setMinimumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new MigLayout(new LC().flowY().fill().gridGap("0!", "0!").insets("0")));
        panel.add(new TitledPanel(message("aboutDialog.title.tip")) {
            @Override
            protected void addComponentToContentPane(JPanel contentPane) {
                contentPane.add(new JLabel(message("aboutDialog.tips1")));
            }
        }, new CC().growX().pushX());
        panel.add(new TitledPanel(message("aboutDialog.title.colorReference")) {
            @Override
            protected void addComponentToContentPane(JPanel contentPane) {
                contentPane.add(new JLabel(message("aboutDialog.copyRight.text")));
            }
        }, new CC().growX().pushX());

        panel.add(new TitledPanel(message("aboutDialog.title.contact")) {
            @Override
            protected void addComponentToContentPane(JPanel contentPane) {
                contentPane.add(new JLabel("EMail:\tleewyatt7788@gmail.com"),"wrap");
                contentPane.add(new JLabel("JavaFX/Swing QQ群: 715598051"),"wrap");
            }
        }, new CC().growX().pushX());
        panel.add(new TitledPanel(message("aboutDialog.title.cache")) {
            @Override
            protected void addComponentToContentPane(JPanel contentPane) {
                JButton button = new JButton(message("aboutDialog.clearCache.text"));
                button.addActionListener(e->{
                    new ClearCacheDialog(project).show();
                });
                contentPane.add(button,"wrap");
                contentPane.add(new JLabel(message("aboutDialog.clearCache.tip")));
            }
        }, new CC().growX().pushX());


        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }
}
