package com.upload.picture;

import com.upload.picture.config.MediaBrowseConfig;
import com.upload.picture.config.StorageConfig;
import com.upload.picture.model.MediaRoot;
import com.upload.picture.service.FileStatisticsService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Swing 图形界面 — 文件上传服务管理器
 *
 * 功能：
 * - 服务管理：启停 Spring Boot 服务、查看路径配置、文件统计
 * - 根目录配置：管理 iOS 客户端可浏览的媒体目录（增删改查）
 */
public class SwingApp {

    private static final int START_PORT = 50000;
    private static final int PORT_RANGE = 3;

    private final String[] commandLineArgs;
    private final StorageConfig storageConfig;
    private final FileStatisticsService fileStatsService;
    private final MediaBrowseConfig mediaBrowseConfig;

    private JFrame frame;
    private int currentPort = -1;
    private ConfigurableApplicationContext springContext;

    // 服务管理 Tab 控件
    private JLabel uploadPathLabel;
    private JLabel logPathLabel;
    private JLabel imageCountLabel;
    private JLabel videoCountLabel;
    private JLabel livePhotoCountLabel;
    private JLabel statusLabel;
    private JLabel portLabel;
    private JLabel messageLabel;
    private JButton serviceButton;

    // 根目录配置 Tab 控件
    private JTable rootsTable;
    private RootsTableModel rootsTableModel;

    public SwingApp(String[] args) {
        this.commandLineArgs = args;
        this.storageConfig = new StorageConfig();
        this.fileStatsService = new FileStatisticsService();
        this.mediaBrowseConfig = new MediaBrowseConfig();
    }

    public static void launch(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new SwingApp(args).createAndShowGUI();
        });
    }

    private void createAndShowGUI() {
        frame = new JFrame("文件上传服务管理器");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleWindowClose();
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("服务管理", createServiceManagementPanel());
        tabbedPane.addTab("预览目录配置", createRootsConfigPanel());

        frame.setContentPane(tabbedPane);
        frame.setSize(850, 700);
        frame.setMinimumSize(new Dimension(750, 600));
        frame.setLocationRelativeTo(null);

        updateStatistics();
        loadRoots();

        frame.setVisible(true);
    }

    // ==================== 服务管理 Tab ====================

    private JPanel createServiceManagementPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("文件上传服务管理");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(15));
        panel.add(createPathSection());
        panel.add(Box.createVerticalStrut(10));
        panel.add(createStatsSection());
        panel.add(Box.createVerticalStrut(10));
        panel.add(createControlSection());
        panel.add(Box.createVerticalStrut(10));

        messageLabel = new JLabel("服务未启动");
        messageLabel.setForeground(Color.GRAY);
        messageLabel.setFont(messageLabel.getFont().deriveFont(14f));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(messageLabel);

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel createPathSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "路径配置",
            TitledBorder.LEFT, TitledBorder.TOP,
            section.getFont().deriveFont(Font.BOLD)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 上传目录
        gbc.gridx = 0; gbc.gridy = 0;
        section.add(new JLabel("上传目录:"), gbc);

        uploadPathLabel = new JLabel(storageConfig.getUploadBaseDir());
        uploadPathLabel.setForeground(new Color(0, 102, 204));
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        section.add(uploadPathLabel, gbc);

        JPanel uploadBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton changeBtn = new JButton("修改");
        changeBtn.addActionListener(e -> changeUploadPath());
        JButton openUploadBtn = new JButton("打开");
        openUploadBtn.addActionListener(e -> openDirectory(storageConfig.getUploadBaseDir()));
        uploadBtnPanel.add(changeBtn);
        uploadBtnPanel.add(openUploadBtn);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        section.add(uploadBtnPanel, gbc);

        // 日志目录
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        section.add(new JLabel("日志目录:"), gbc);

        logPathLabel = new JLabel(storageConfig.getLogDir());
        logPathLabel.setForeground(new Color(0, 102, 204));
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        section.add(logPathLabel, gbc);

        JButton openLogBtn = new JButton("打开");
        openLogBtn.addActionListener(e -> openDirectory(storageConfig.getLogDir()));
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        section.add(openLogBtn, gbc);

        return section;
    }

    private JPanel createStatsSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "文件统计",
            TitledBorder.LEFT, TitledBorder.TOP,
            section.getFont().deriveFont(Font.BOLD)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        section.add(new JLabel("图片:"), gbc);
        imageCountLabel = new JLabel("0");
        imageCountLabel.setFont(imageCountLabel.getFont().deriveFont(Font.BOLD, 16f));
        imageCountLabel.setForeground(new Color(76, 175, 80));
        gbc.gridx = 1;
        section.add(imageCountLabel, gbc);

        gbc.gridx = 2;
        section.add(new JLabel("视频:"), gbc);
        videoCountLabel = new JLabel("0");
        videoCountLabel.setFont(videoCountLabel.getFont().deriveFont(Font.BOLD, 16f));
        videoCountLabel.setForeground(new Color(33, 150, 243));
        gbc.gridx = 3;
        section.add(videoCountLabel, gbc);

        gbc.gridx = 4;
        section.add(new JLabel("实况照片:"), gbc);
        livePhotoCountLabel = new JLabel("0 对");
        livePhotoCountLabel.setFont(livePhotoCountLabel.getFont().deriveFont(Font.BOLD, 16f));
        livePhotoCountLabel.setForeground(new Color(255, 152, 0));
        gbc.gridx = 5;
        section.add(livePhotoCountLabel, gbc);

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> updateStatistics());
        gbc.gridx = 6; gbc.weightx = 1; gbc.anchor = GridBagConstraints.EAST;
        section.add(refreshBtn, gbc);

        return section;
    }

    private JPanel createControlSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "服务控制",
            TitledBorder.LEFT, TitledBorder.TOP,
            section.getFont().deriveFont(Font.BOLD)));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        statusLabel = new JLabel("● 已停止");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        portLabel = new JLabel("端口: 未分配");
        portLabel.setForeground(Color.GRAY);
        portLabel.setFont(portLabel.getFont().deriveFont(14f));
        statusPanel.add(statusLabel);
        statusPanel.add(portLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        serviceButton = new JButton("开启文件上传服务");
        serviceButton.setFont(serviceButton.getFont().deriveFont(16f));
        serviceButton.setPreferredSize(new Dimension(220, 40));
        serviceButton.addActionListener(e -> toggleService());
        buttonPanel.add(serviceButton);

        section.add(statusPanel);
        section.add(buttonPanel);

        return section;
    }

    // ==================== 预览目录配置 Tab ====================

    private JPanel createRootsConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("媒体预览目录配置");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        JLabel descLabel = new JLabel("配置 iOS 客户端可以浏览的媒体文件夹。配置后需重启服务才能生效。");
        descLabel.setForeground(Color.GRAY);
        topPanel.add(titleLabel);
        topPanel.add(Box.createVerticalStrut(5));
        topPanel.add(descLabel);
        panel.add(topPanel, BorderLayout.NORTH);

        rootsTableModel = new RootsTableModel();
        rootsTable = new JTable(rootsTableModel);
        rootsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rootsTable.setRowHeight(24);
        rootsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        rootsTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        rootsTable.getColumnModel().getColumn(2).setPreferredWidth(350);
        rootsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        rootsTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        JScrollPane scrollPane = new JScrollPane(rootsTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton addBtn = new JButton("添加根目录");
        addBtn.addActionListener(e -> showAddRootDialog());
        JButton editBtn = new JButton("编辑");
        editBtn.addActionListener(e -> showEditRootDialog());
        JButton deleteBtn = new JButton("删除");
        deleteBtn.setForeground(Color.RED);
        deleteBtn.addActionListener(e -> deleteSelectedRoot());
        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> loadRoots());
        JButton testBtn = new JButton("测试路径");
        testBtn.addActionListener(e -> testSelectedRootPath());

        buttonBar.add(addBtn);
        buttonBar.add(editBtn);
        buttonBar.add(deleteBtn);
        buttonBar.add(refreshBtn);
        buttonBar.add(testBtn);
        panel.add(buttonBar, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== 服务控制逻辑 ====================

    private void toggleService() {
        if (springContext == null || !springContext.isActive()) {
            startService();
        } else {
            stopService();
        }
    }

    private void startService() {
        serviceButton.setEnabled(false);
        messageLabel.setText("文件上传服务启动中...");
        messageLabel.setForeground(Color.ORANGE);
        statusLabel.setText("● 启动中...");
        statusLabel.setForeground(Color.ORANGE);

        new Thread(() -> {
            try {
                int availablePort = findAvailablePort();
                if (availablePort == -1) {
                    SwingUtilities.invokeLater(() -> {
                        messageLabel.setText("启动失败: 端口范围 " + START_PORT + "-" + (START_PORT + PORT_RANGE - 1) + " 内无可用端口");
                        messageLabel.setForeground(Color.RED);
                        statusLabel.setText("● 已停止");
                        statusLabel.setForeground(Color.RED);
                        serviceButton.setEnabled(true);
                    });
                    return;
                }

                currentPort = availablePort;
                storageConfig.syncToSystemProperties();

                String[] springArgs = buildSpringArgs(availablePort);
                springContext = SpringApplication.run(UploadPictureApplication.class, springArgs);

                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("文件上传服务已启动");
                    messageLabel.setForeground(new Color(0, 128, 0));
                    statusLabel.setText("● 运行中");
                    statusLabel.setForeground(new Color(0, 128, 0));
                    portLabel.setText("端口: " + currentPort);
                    portLabel.setForeground(new Color(0, 128, 0));
                    serviceButton.setText("关闭文件上传服务");
                    serviceButton.setEnabled(true);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("启动失败: " + ex.getMessage());
                    messageLabel.setForeground(Color.RED);
                    statusLabel.setText("● 已停止");
                    statusLabel.setForeground(Color.RED);
                    serviceButton.setText("开启文件上传服务");
                    serviceButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void stopService() {
        serviceButton.setEnabled(false);
        messageLabel.setText("文件上传服务关闭中...");
        messageLabel.setForeground(Color.ORANGE);
        statusLabel.setText("● 关闭中...");
        statusLabel.setForeground(Color.ORANGE);

        new Thread(() -> {
            try {
                if (springContext != null) {
                    springContext.close();
                    springContext = null;
                }
                currentPort = -1;

                SwingUtilities.invokeLater(() -> {
                    updateStatistics();
                    messageLabel.setText("文件上传服务已关闭");
                    messageLabel.setForeground(Color.GRAY);
                    statusLabel.setText("● 已停止");
                    statusLabel.setForeground(Color.RED);
                    portLabel.setText("端口: 未分配");
                    portLabel.setForeground(Color.GRAY);
                    serviceButton.setText("开启文件上传服务");
                    serviceButton.setEnabled(true);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    messageLabel.setText("关闭失败: " + ex.getMessage());
                    messageLabel.setForeground(Color.RED);
                    serviceButton.setEnabled(true);
                });
            }
        }).start();
    }

    private String[] buildSpringArgs(int port) {
        List<String> args = new ArrayList<>();
        if (commandLineArgs != null) {
            for (String arg : commandLineArgs) {
                if (!arg.equalsIgnoreCase("--gui")) {
                    args.add(arg);
                }
            }
        }
        args.add("--server.port=" + port);
        return args.toArray(new String[0]);
    }

    // ==================== 路径操作 ====================

    private void changeUploadPath() {
        if (currentPort != -1) {
            JOptionPane.showMessageDialog(frame,
                "请先停止服务后再修改上传路径！", "警告", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择上传目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File currentDir = new File(storageConfig.getUploadBaseDir());
        if (currentDir.exists()) {
            chooser.setCurrentDirectory(currentDir.getParentFile());
        }

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String newPath = chooser.getSelectedFile().getAbsolutePath() + "/";
            storageConfig.setUploadBaseDir(newPath);
            storageConfig.syncToSystemProperties();
            uploadPathLabel.setText(newPath);
            updateStatistics();
            JOptionPane.showMessageDialog(frame,
                "上传路径已保存到配置文件", "成功", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openDirectory(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Desktop.getDesktop().open(dir);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                "无法打开目录: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== 统计刷新 ====================

    private void updateStatistics() {
        String uploadBaseDir = storageConfig.getUploadBaseDir();
        Map<String, Object> stats = fileStatsService.getAllStatistics(uploadBaseDir);

        int images = (Integer) stats.get("images");
        int videos = (Integer) stats.get("videos");
        @SuppressWarnings("unchecked")
        Map<String, Integer> livePhotos = (Map<String, Integer>) stats.get("livePhotos");
        int livePairs = livePhotos.get("pairs");
        int liveTotal = livePhotos.get("total");

        imageCountLabel.setText(String.valueOf(images));
        videoCountLabel.setText(String.valueOf(videos));
        livePhotoCountLabel.setText(String.format("%d 对 (总文件: %d)", livePairs, liveTotal));
    }

    // ==================== 根目录 CRUD ====================

    private void loadRoots() {
        mediaBrowseConfig.reloadRoots();
        List<MediaRoot> roots = mediaBrowseConfig.getRoots();
        rootsTableModel.setData(roots);
    }

    private void showAddRootDialog() {
        JTextField nameField = new JTextField(20);
        JTextField pathField = new JTextField(20);
        pathField.setEditable(false);
        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择媒体根目录");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath() + "/");
            }
        });

        JTextField iconField = new JTextField("folder.fill", 20);
        JTextField descField = new JTextField(20);
        JCheckBox readonlyCheck = new JCheckBox();

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseBtn, BorderLayout.EAST);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("名称*:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("路径*:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(pathPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("图标:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(iconField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("描述:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(descField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("只读:"), gbc);
        gbc.gridx = 1;
        formPanel.add(readonlyCheck, gbc);

        int result = JOptionPane.showConfirmDialog(frame, formPanel,
            "添加媒体根目录", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String path = pathField.getText().trim();
            if (name.isEmpty() || path.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                    "名称和路径不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String icon = iconField.getText().trim();
            if (icon.isEmpty()) icon = "folder.fill";

            MediaRoot added = mediaBrowseConfig.addRoot(name, path, icon, descField.getText().trim(), readonlyCheck.isSelected());
            if (added != null) {
                JOptionPane.showMessageDialog(frame,
                    "根目录已添加！", "成功", JOptionPane.INFORMATION_MESSAGE);
                loadRoots();
                notifyServerReload();
            }
        }
    }

    private void showEditRootDialog() {
        int selectedRow = rootsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(frame, "请先选择一个根目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MediaRoot selected = rootsTableModel.getRootAt(selectedRow);

        JTextField nameField = new JTextField(selected.getName(), 20);
        JTextField pathField = new JTextField(selected.getPath(), 20);
        pathField.setEditable(false);
        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择媒体根目录");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            File currentDir = new File(selected.getPath());
            if (currentDir.exists()) chooser.setCurrentDirectory(currentDir.getParentFile());
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath() + "/");
            }
        });

        JTextField iconField = new JTextField(selected.getIcon(), 20);
        JTextField descField = new JTextField(selected.getDescription() != null ? selected.getDescription() : "", 20);
        JCheckBox readonlyCheck = new JCheckBox();
        readonlyCheck.setSelected(selected.isReadonly());

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseBtn, BorderLayout.EAST);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("名称:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("路径:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(pathPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("图标:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(iconField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("描述:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        formPanel.add(descField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("只读:"), gbc);
        gbc.gridx = 1;
        formPanel.add(readonlyCheck, gbc);

        int result = JOptionPane.showConfirmDialog(frame, formPanel,
            "编辑媒体根目录", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            selected.setName(nameField.getText().trim());
            selected.setPath(pathField.getText().trim());
            selected.setIcon(iconField.getText().trim());
            selected.setDescription(descField.getText().trim());
            selected.setReadonly(readonlyCheck.isSelected());

            if (mediaBrowseConfig.updateRoot(selected)) {
                JOptionPane.showMessageDialog(frame, "根目录已更新！", "成功", JOptionPane.INFORMATION_MESSAGE);
                loadRoots();
                notifyServerReload();
            }
        }
    }

    private void deleteSelectedRoot() {
        int selectedRow = rootsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(frame, "请先选择一个根目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MediaRoot selected = rootsTableModel.getRootAt(selectedRow);
        int confirm = JOptionPane.showConfirmDialog(frame,
            "确定要删除 \"" + selected.getName() + "\" 吗？\n注意：这只会删除配置，不会删除实际文件。",
            "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            if (mediaBrowseConfig.deleteRoot(selected.getId())) {
                JOptionPane.showMessageDialog(frame, "根目录已删除！", "成功", JOptionPane.INFORMATION_MESSAGE);
                loadRoots();
                notifyServerReload();
            } else {
                JOptionPane.showMessageDialog(frame, "删除失败！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void testSelectedRootPath() {
        int selectedRow = rootsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(frame, "请先选择一个根目录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MediaRoot selected = rootsTableModel.getRootAt(selectedRow);
        File rootDir = new File(selected.getPath());

        if (!rootDir.exists()) {
            JOptionPane.showMessageDialog(frame,
                "路径不存在:\n" + selected.getPath(), "路径不存在", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!rootDir.isDirectory()) {
            JOptionPane.showMessageDialog(frame,
                "路径不是一个有效的目录:\n" + selected.getPath(), "不是目录", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File[] files = rootDir.listFiles();
        int fileCount = 0, dirCount = 0;
        if (files != null) {
            for (File file : files) {
                if (!file.getName().startsWith(".")) {
                    if (file.isDirectory()) dirCount++;
                    else fileCount++;
                }
            }
        }

        JOptionPane.showMessageDialog(frame,
            "路径有效！\n\n路径: " + selected.getPath() + "\n文件夹数: " + dirCount + "\n文件数: " + fileCount,
            "路径测试成功", JOptionPane.INFORMATION_MESSAGE);
    }

    private void notifyServerReload() {
        if (springContext != null && springContext.isActive()) {
            try {
                MediaBrowseConfig service = springContext.getBean(MediaBrowseConfig.class);
                service.reloadRoots();
            } catch (Exception e) {
                System.out.println("无法刷新服务缓存: " + e.getMessage());
            }
        }
    }

    // ==================== 窗口关闭 ====================

    private void handleWindowClose() {
        if (springContext != null && springContext.isActive()) {
            int confirm = JOptionPane.showConfirmDialog(frame,
                "服务正在运行中，确定要关闭吗？",
                "确认关闭", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            System.out.println("窗口关闭，正在关闭 Spring Boot 服务...");
            try {
                springContext.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        frame.dispose();
        System.exit(0);
    }

    // ==================== 端口检测 ====================

    private int findAvailablePort() {
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

    // ==================== 表格模型 ====================

    private static class RootsTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"ID", "名称", "路径", "图标", "只读"};
        private List<MediaRoot> data = new ArrayList<>();

        public void setData(List<MediaRoot> roots) {
            this.data = new ArrayList<>(roots);
            fireTableDataChanged();
        }

        public MediaRoot getRootAt(int row) {
            return data.get(row);
        }

        @Override
        public int getRowCount() { return data.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            MediaRoot root = data.get(row);
            switch (col) {
                case 0: return root.getId();
                case 1: return root.getName();
                case 2: return root.getPath();
                case 3: return root.getIcon();
                case 4: return root.isReadonly() ? "是" : "否";
                default: return "";
            }
        }
    }
}
