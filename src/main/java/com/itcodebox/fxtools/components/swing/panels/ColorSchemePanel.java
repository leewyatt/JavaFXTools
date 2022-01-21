package com.itcodebox.fxtools.components.swing.panels;

import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBFont;
import com.itcodebox.fxtools.components.ShowPaintPicker;
import com.itcodebox.fxtools.utils.ColorScheme;
import com.itcodebox.fxtools.utils.ColorScheme01;
import com.itcodebox.fxtools.utils.ColorScheme02;
import com.itcodebox.fxtools.utils.LinearGradientConstant;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.awt.*;

import static com.itcodebox.fxtools.utils.JavaFXToolsBundle.message;

/**
 * @author LeeWyatt
 */
public class ColorSchemePanel extends JBSplitter {

    private final JBScrollPane colorScrollPane = new JBScrollPane();
    private final ColorHBox colorHBox;
    private final ColorVBox colorVBox;
    //private final ColorTipsPanel colorTipsPanel;
    private final LinearGradientBox linearGradientBox;
    private final JBTextArea descTextArea;

    public ColorSchemePanel(ShowPaintPicker showPaintPicker) {
        super(false, 0.3F);
        setLayout(new BorderLayout());
        colorHBox = new ColorHBox(showPaintPicker);
        colorHBox.setBorder(BorderFactory.createLineBorder(JBColor.WHITE, 15));

        colorVBox = new ColorVBox(showPaintPicker);
        colorVBox.setBorder(BorderFactory.createLineBorder(JBColor.WHITE, 15));

        //colorTipsPanel = new ColorTipsPanel();

        linearGradientBox = new LinearGradientBox(showPaintPicker);
        linearGradientBox.setBorder(BorderFactory.createLineBorder(JBColor.WHITE, 15));
        linearGradientBox.setLinearGradientInfos(LinearGradientConstant.GradientList);
        descTextArea = new JBTextArea();
        descTextArea.setLineWrap(true);
        descTextArea.setFont(JBFont.label().biggerOn(2));
        descTextArea.setEditable(false);

        setFirstComponent(new JBScrollPane(initTree()));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(colorScrollPane);
        JBScrollPane textAreaPane = new JBScrollPane(descTextArea, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        textAreaPane.setBorder(new JBEmptyBorder(5));
        textAreaPane.setPreferredSize(new Dimension(60 * 7, 65));
        textAreaPane.setMaximumSize(new Dimension(60 * 7, 65));
        textAreaPane.setBackground(JBColor.WHITE);

        //TODO 屏蔽描述组件
        //panel.add(textAreaPane, BorderLayout.NORTH);
        setSecondComponent(panel);

    }

    private Tree initTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(message("colorSchemePanel.rootNode.text"));
        root.add(initColorSchemeNode(message("colorSchemePanel.hueNode.text"), ColorScheme01.HueScheme));
        root.add(initColorSchemeNode(message("colorSchemePanel.impressionNode.text"), ColorScheme01.ImpressionScheme));
        root.add(initColorSchemeNode(message("colorSchemePanel.themeNode.text"), ColorScheme02.ThemeScheme));
        DefaultMutableTreeNode linearGradientNode = new DefaultMutableTreeNode(message("colorSchemePanel.gradientNode.text"));
        root.add(linearGradientNode);
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        Tree tree = new Tree(treeModel);
        DefaultTreeSelectionModel selectionModel = new DefaultTreeSelectionModel();
        selectionModel.setSelectionMode(DefaultTreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setSelectionModel(selectionModel);

        TreePath linearGradientNodePath = new TreePath(linearGradientNode.getPath());
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (!tree.isSelectionEmpty() && path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                //if (node.isRoot()) {
                //    colorScrollPane.setViewportView(colorTipsPanel);
                //    descTextArea.setText(null);
                //    return;
                //}
                Object userObject = node.getUserObject();
                if (userObject instanceof ColorScheme) {
                    ColorScheme cs = (ColorScheme) userObject;
                    if (cs.isVertical()) {
                        colorVBox.setWebColors(cs.getWebColorAry(), cs.getTitleList());
                    } else {
                        colorHBox.setWebColors(cs.getWebColorAry());
                    }
                    colorScrollPane.setViewportView(cs.isVertical() ? colorVBox : colorHBox);
                    descTextArea.setText(cs.getDesc());
                    descTextArea.setCaretPosition(0);
                    //更新UI, 否则滚动条,不能及时响应
                    colorScrollPane.updateUI();
                }
                if (path.equals(linearGradientNodePath)) {
                    colorScrollPane.setViewportView(linearGradientBox);
                    descTextArea.setText(null);
                    descTextArea.setCaretPosition(0);
                }

            }
        });
        //tree.expandPath(new TreePath(root.getChildAt(0)));
        //tree.expandPath(new TreePath(root.getChildAt(0).getChildAt(0)));
        //tree.setSelectionPath(new TreePath(root));
        tree.setExpandsSelectedPaths(true);
        tree.setSelectionPath(new TreePath( root.getChildAt(0)));
        tree.setSelectionPath(new TreePath(root.getChildAt(0).getChildAt(0)));
        return tree;
    }

    private DefaultMutableTreeNode initColorSchemeNode(String colorSchemeTitle, ColorScheme[] colorSchemes) {
        DefaultMutableTreeNode hueColorSchemeNode = new DefaultMutableTreeNode(colorSchemeTitle);
        for (ColorScheme colorScheme : colorSchemes) {
            hueColorSchemeNode.add(new DefaultMutableTreeNode(colorScheme));
        }
        return hueColorSchemeNode;
    }

}
