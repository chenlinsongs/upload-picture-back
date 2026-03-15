package com.upload.picture;

import com.upload.picture.config.StorageConfig;

import java.io.File;
import java.util.Arrays;

/**
 * 应用统一入口
 *
 * 支持两种启动模式：
 *   1. GUI 模式（默认）: java -jar app.jar [--gui]
 *      打开 Swing 管理界面，通过界面控制 Spring Boot 服务的启停
 *
 *   2. CLI 模式: java -jar app.jar --cli [--port=50000]
 *      直接在命令行启动 Spring Boot 服务，无需图形界面
 *      适用于树莓派等无桌面环境的服务器
 */
public class AppLauncher {

    public static void main(String[] args) {
        boolean cliMode = Arrays.stream(args).anyMatch(
            a -> a.equalsIgnoreCase("--cli") || a.equalsIgnoreCase("--headless")
        );

        initConfig();

        if (cliMode) {
            startCli(args);
        } else {
            startGui(args);
        }
    }

    /**
     * 在 Spring 启动前加载配置并同步系统属性
     * 无论 GUI 还是 CLI 模式都需要执行，确保上传目录和日志目录正确初始化
     */
    private static void initConfig() {
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.syncToSystemProperties();

        String uploadBaseDir = storageConfig.getUploadBaseDir();
        String logDir = storageConfig.getLogDir();

        new File(logDir).mkdirs();
        new File(uploadBaseDir).mkdirs();

        System.out.println("========================================");
        System.out.println("  文件上传服务管理器");
        System.out.println("========================================");
        System.out.println("上传目录: " + uploadBaseDir);
        System.out.println("日志目录: " + logDir);
        System.out.println("配置文件: " + storageConfig.getConfigFilePath());
        System.out.println("========================================");
    }

    private static void startGui(String[] args) {
        System.out.println("启动模式: GUI（Swing 图形界面）");
        SwingApp.launch(args);
    }

    private static void startCli(String[] args) {
        System.out.println("启动模式: CLI（命令行直接启动）");

        String[] springArgs = Arrays.stream(args)
            .filter(a -> !a.equalsIgnoreCase("--cli") && !a.equalsIgnoreCase("--headless"))
            .toArray(String[]::new);

        org.springframework.boot.SpringApplication.run(UploadPictureApplication.class, springArgs);
    }
}
