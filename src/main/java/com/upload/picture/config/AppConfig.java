package com.upload.picture.config;

import java.io.*;
import java.util.Properties;

/**
 * 应用配置管理
 * 负责持久化存储和读取应用配置
 */
public class AppConfig {
    
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/FileUploadManager/";
    private static final String CONFIG_FILE = CONFIG_DIR + "config.properties";
    
    private Properties properties;
    
    public AppConfig() {
        properties = new Properties();
        loadConfig();
    }
    
    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                System.out.println("加载配置成功: " + CONFIG_FILE);
            } catch (IOException e) {
                System.err.println("加载配置失败: " + e.getMessage());
            }
        } else {
            System.out.println("配置文件不存在，将使用默认配置");
            setDefaultConfig();
        }
    }
    
    public void saveConfig() {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "File Upload Manager Configuration");
            System.out.println("配置保存成功: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("配置保存失败: " + e.getMessage());
        }
    }
    
    private void setDefaultConfig() {
        String userHome = System.getProperty("user.home");
        String defaultUploadBase = userHome + "/FileUploadManager/uploads/";
        properties.setProperty("upload.base.dir", defaultUploadBase);
    }
    
    public String getUploadBaseDir() {
        String userHome = System.getProperty("user.home");
        String defaultUploadBase = userHome + "/FileUploadManager/uploads/";
        return properties.getProperty("upload.base.dir", defaultUploadBase);
    }
    
    public void setUploadBaseDir(String uploadBaseDir) {
        if (!uploadBaseDir.endsWith("/")) {
            uploadBaseDir += "/";
        }
        properties.setProperty("upload.base.dir", uploadBaseDir);
        saveConfig();
    }
    
    public String getLogDir() {
        String userHome = System.getProperty("user.home");
        return userHome + "/FileUploadManager/logs/";
    }
    
    public String getConfigFilePath() {
        return CONFIG_FILE;
    }
}
