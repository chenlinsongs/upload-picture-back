package com.upload.picture.config;

import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Properties;

/**
 * 统一的存储路径配置中心
 *
 * 本类集中管理所有文件存储相关的路径配置，包括：
 * - 上传基础目录（图片、视频、实况照片）
 * - 自定义文件夹的上传目标目录
 * - 日志目录
 *
 * 配置持久化存储在: ~/FileUploadManager/config.properties
 *
 * 浏览和下载功能的媒体根目录配置，请查看
 * @see MediaBrowseConfig
 */
@Component
public class StorageConfig {

    private static final String APP_NAME = "FileUploadManager";
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/" + APP_NAME + "/";
    private static final String CONFIG_FILE = CONFIG_DIR + "config.properties";

    private final Properties properties;

    public StorageConfig() {
        properties = new Properties();
        loadConfig();
    }

    // ==================== 上传路径 ====================

    /**
     * 上传文件的根目录，所有上传文件都存储在此目录下
     * 默认: ~/FileUploadManager/uploads/
     */
    public String getUploadBaseDir() {
        return properties.getProperty("upload.base.dir", getDefaultUploadBase());
    }

    /** 图片上传目录: {uploadBaseDir}/images/ */
    public String getImageDir() {
        return getUploadBaseDir() + "images/";
    }

    /** 视频上传目录: {uploadBaseDir}/videos/ */
    public String getVideoDir() {
        return getUploadBaseDir() + "videos/";
    }

    /** 实况照片上传目录: {uploadBaseDir}/livephotos/ */
    public String getLivePhotoDir() {
        return getUploadBaseDir() + "livephotos/";
    }

    /**
     * 根据文件夹名称获取上传目标目录
     *
     * @param folder 文件夹名称，为空时返回上传基础目录
     * @return 目标上传目录路径（自动创建）
     */
    public String getTargetUploadDir(String folder) {
        String baseDir = getUploadBaseDir();

        if (folder == null || folder.trim().isEmpty()) {
            return baseDir;
        }

        String safeFolder = folder.trim()
            .replaceAll("[/\\\\:*?\"<>|]", "_")
            .replaceAll("\\.\\.", "");

        if (safeFolder.isEmpty()) {
            return baseDir;
        }

        String targetDir = baseDir + safeFolder + "/";

        File dir = new File(targetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return targetDir;
    }

    // ==================== 日志路径 ====================

    /** 日志目录: ~/FileUploadManager/logs/ */
    public String getLogDir() {
        return CONFIG_DIR + "logs/";
    }

    // ==================== 配置修改 ====================

    public void setUploadBaseDir(String uploadBaseDir) {
        if (!uploadBaseDir.endsWith("/")) {
            uploadBaseDir += "/";
        }
        properties.setProperty("upload.base.dir", uploadBaseDir);
        saveConfig();
    }

    public String getConfigFilePath() {
        return CONFIG_FILE;
    }

    // ==================== 系统属性同步 ====================

    /**
     * 将路径配置同步到系统属性中（供日志框架等使用）
     * 在 Spring 容器启动前由 AppLauncher 调用（GUI 和 CLI 模式均适用）
     */
    public void syncToSystemProperties() {
        String uploadBaseDir = getUploadBaseDir();
        String logDir = getLogDir();

        System.setProperty("app.log.dir", logDir);
        System.setProperty("app.upload.base.dir", uploadBaseDir);
        System.setProperty("app.upload.image.dir", getImageDir());
        System.setProperty("app.upload.video.dir", getVideoDir());
        System.setProperty("app.upload.livephoto.dir", getLivePhotoDir());
    }

    // ==================== 内部方法 ====================

    private String getDefaultUploadBase() {
        return System.getProperty("user.home") + "/" + APP_NAME + "/uploads/";
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("加载配置失败: " + e.getMessage());
            }
        } else {
            properties.setProperty("upload.base.dir", getDefaultUploadBase());
        }
    }

    private void saveConfig() {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "File Upload Manager Configuration");
        } catch (IOException e) {
            System.err.println("配置保存失败: " + e.getMessage());
        }
    }
}
