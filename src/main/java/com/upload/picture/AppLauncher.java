package com.upload.picture;

import com.upload.picture.config.StorageConfig;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public static final int START_PORT = 50000;
    public static final int PORT_RANGE = 3;

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

        List<String> springArgs = new ArrayList<>();
        String specifiedPort = null;

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--cli") || arg.equalsIgnoreCase("--headless")) {
                continue;
            }
            if (arg.startsWith("--server.port=") || arg.startsWith("--port=")) {
                specifiedPort = arg.substring(arg.indexOf('=') + 1);
                springArgs.add("--server.port=" + specifiedPort);
            } else {
                springArgs.add(arg);
            }
        }

        if (specifiedPort == null) {
            int port = findAvailablePort();
            if (port == -1) {
                System.err.println("启动失败: 端口范围 " + START_PORT + "-" + (START_PORT + PORT_RANGE - 1) + " 内无可用端口");
                System.exit(1);
            }
            springArgs.add("--server.port=" + port);
            System.out.println("自动分配端口: " + port);
        } else {
            System.out.println("使用指定端口: " + specifiedPort);
        }

        org.springframework.boot.SpringApplication.run(
            UploadPictureApplication.class, springArgs.toArray(new String[0]));
    }

    /**
     * 从 START_PORT 开始扫描，返回第一个可用端口；全部占用则返回 -1
     */
    public static int findAvailablePort() {
        for (int i = 0; i < PORT_RANGE; i++) {
            int port = START_PORT + i;
            try (ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);
                return port;
            } catch (IOException ignored) {
            }
        }
        return -1;
    }
}
