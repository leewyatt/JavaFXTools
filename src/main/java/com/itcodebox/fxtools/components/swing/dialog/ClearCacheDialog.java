package com.itcodebox.fxtools.components.swing.dialog;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBEmptyBorder;
import com.itcodebox.fxtools.utils.PluginConstant;
import icons.PluginIcons;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * @author LeeWyatt
 */
public class ClearCacheDialog extends DialogWrapper {
    private static final int DEFAULT_WIDTH = 350;
    private static final int DEFAULT_HEIGHT = 160;
    private final Project project;

    public ClearCacheDialog(Project project) {
        super(true);
        this.project = project;
        setTitle("Cache");
        setOKButtonText("Clear Cache");
        setCancelButtonText("Cancel");
        getRootPane().setMinimumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        File thumbDir = PluginConstant.TEMP_DIRECTORY_PATH.toFile();
        //统计缓存大小
        JLabel sizeLabel = new JLabel();
        sizeLabel.setText(StringUtil.formatFileSize(FileUtils.sizeOfDirectory(thumbDir)));

        //统计缓存文件数量
        JLabel amountLabel = new JLabel();
        amountLabel.setText(FileUtils.listFiles(thumbDir, null, true).size() + "");

        JPanel panel = FormBuilder
                .createFormBuilder()
                .addLabeledComponent(new JLabel("Total size of cache files:", PluginIcons.Data, JLabel.LEFT), sizeLabel)
                .addLabeledComponent(new JLabel("The number of cache files:", AllIcons.Actions.GroupByPrefix, JLabel.LEFT), amountLabel)
                .getPanel();
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), new JBEmptyBorder(0, 5, 0, 5)));
        //panel.setBackground(JBColor.WHITE);
        return panel;
    }

    @Override
    protected void doOKAction() {
        File thumbDir = PluginConstant.TEMP_DIRECTORY_PATH.toFile();
        if (!thumbDir.exists()) {
            super.doOKAction();
            return;
        }
        File[] files = thumbDir.listFiles();
        if (files == null || files.length == 0) {
            super.doOKAction();
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Clear cache", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                PluginConstant.IsClearing.set(true);
                try {
                    FileUtils.cleanDirectory(PluginConstant.TEMP_DIRECTORY_PATH.toFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                PluginConstant.IsClearing.set(false);
                //不需要就把这个工具类删除了
                //NotifyUtil.showInfoNotification(project, PluginConstant.NOTIFICATION_CLEAR_CACHE, "Cleaned Up","The cache has been cleaned up.");
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showInfoMessage("The cache has been cleaned up.", "Cleaned Up");
                });

            }
        });

        super.doOKAction();
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }
}
