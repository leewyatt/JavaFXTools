package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.HorizontalBox;
import com.intellij.util.ui.JBUI;
import com.itcodebox.fxtools.utils.PluginConstant;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * @author LeeWyatt
 */
public class MainPanel extends JPanel {
    private ToolWindow toolWindow;
    private Project project;

    public MainPanel(Project project, ToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        this.project = project;

        JBTabbedPane pane = new JBTabbedPane();
        pane.addTab("Color Tools", new ColorToolPanel(project, toolWindow));
        pane.addTab("Font Tools", new FontToolPanel(project));
        pane.addTab("SVG Tools", new SvgToolPanel(project));
        //pane.addTab("CSS Tools", new CssToolPanel(project));
        setLayout(new BorderLayout());
        add(pane,BorderLayout.CENTER);

        ComboBox<Integer> jdkComboBox = new ComboBox<Integer>(new DefaultComboBoxModel<Integer>(new Integer[]{8, 11, 12, 13, 14, 15, 16}));
        jdkComboBox.setPreferredSize(new Dimension(60,30));
        jdkComboBox.setSelectedIndex(jdkComboBox.getItemCount()-1);
        LinkLabel<String> apiLink = new LinkLabel<>(" API Doc ",null, new LinkListener<String>() {
            @Override
            public void linkSelected(LinkLabel<String> aSource, String aLinkData) {
                String linkUrl = "";
                int jdkVersion = jdkComboBox.getSelectedItem() == null ? 16 : (int) (jdkComboBox.getSelectedItem());
                if (Objects.equals(8, jdkVersion)) {
                    linkUrl = PluginConstant.api8;
                } else {
                    linkUrl = PluginConstant.openjfxPrefix + jdkVersion;
                }
                BrowserUtil.browse(linkUrl);
            }
        }, "");
        LinkLabel<String> cssLink = new LinkLabel<>(" CSS Doc ",null, new LinkListener<String>() {
            @Override
            public void linkSelected(LinkLabel<String> aSource, String aLinkData) {
                String linkUrl = "";
                int jdkVersion = jdkComboBox.getSelectedItem() == null ? 16 : (int) (jdkComboBox.getSelectedItem());
                if (Objects.equals(8, jdkVersion)) {
                    linkUrl = PluginConstant.css8;
                } else {
                    linkUrl = PluginConstant.openjfxPrefix + jdkVersion + PluginConstant.CssDocSuffix;
                }
                BrowserUtil.browse(linkUrl);
            }
        }, "");

        LinkLabel<String> fxmlLink = new LinkLabel<>(" FXML Doc ",null, new LinkListener<String>() {
            @Override
            public void linkSelected(LinkLabel<String> aSource, String aLinkData) {
                String linkUrl = "";
                int jdkVersion = jdkComboBox.getSelectedItem() == null ? 16 : (int) (jdkComboBox.getSelectedItem());
                if (Objects.equals(8, jdkVersion)) {
                    linkUrl = PluginConstant.fxml8;
                } else {
                    linkUrl = PluginConstant.openjfxPrefix + jdkVersion + PluginConstant.fxmlDocSuffix;
                }
                BrowserUtil.browse(linkUrl);
            }
        }, "");

        LinkLabel<String> openjfxHomeLink = new LinkLabel<>(" Openjfx ",null, (aSource, aLinkData) -> BrowserUtil.browse(PluginConstant.openjfxHome), "");
        LinkLabel<String>  gluonLink = new LinkLabel<>(" Gluon ",null, (aSource, aLinkData) -> BrowserUtil.browse(PluginConstant.Gluonhq), "");

        JButton hideBtn = new JButton("Hide >>");
        hideBtn.addActionListener(e->{
            toolWindow.hide();
        });
        HorizontalBox box = new HorizontalBox();
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), JBUI.Borders.empty(3, 1)));
        boxAddAll(box, hideBtn,jdkComboBox,apiLink,cssLink,fxmlLink,openjfxHomeLink,gluonLink);
        add(box, BorderLayout.NORTH);
    }

    private void boxAddAll(HorizontalBox box, Component... components) {
        for (Component component : components) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(component, BorderLayout.CENTER);
            box.add(panel);
            box.add(Box.createHorizontalGlue());
        }
    }
}
