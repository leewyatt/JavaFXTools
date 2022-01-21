package com.itcodebox.fxtools.service;

import com.itcodebox.fxtools.utils.PluginConstant;

import java.io.IOException;
import java.nio.file.Files;

/**
 * @author LeeWyatt
 */
public class TempFileService {
    public TempFileService() {
        //初始化临时文件夹,用于保存图片
        if (!Files.exists(PluginConstant.PROJECT_DB_DIRECTORY_PATH)) {
            try {
                Files.createDirectories(PluginConstant.PROJECT_DB_DIRECTORY_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!Files.exists(PluginConstant.TEMP_DIRECTORY_PATH)) {
            try {
                Files.createFile(PluginConstant.TEMP_DIRECTORY_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
