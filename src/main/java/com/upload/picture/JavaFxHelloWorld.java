package com.upload.picture;

import com.upload.picture.config.StorageConfig;
import com.upload.picture.model.MediaRoot;
import com.upload.picture.service.FileStatisticsService;
import com.upload.picture.config.MediaBrowseConfig;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JavaFX 文件上传服务管理器
 * 支持路径配置、文件统计、根目录管理
 */
public class JavaFxHelloWorld extends Application {
    private String[] commandLineArgs;
    private static final int START_PORT = 50000;
    private static final int PORT_RANGE = 3;
    private int currentPort = -1;
    
    private StorageConfig storageConfig;
    private FileStatisticsService fileStatsService;
    private MediaBrowseConfig rootConfigService;
    
    private Label uploadPathLabel;
    private Label logPathLabel;
    private Label imageCountLabel;
    private Label videoCountLabel;
    private Label livePhotoCountLabel;
    private Label statusLabel;
    private Label portLabel;
    private Label messageLabel;
    private Button serviceButton;
    private Button refreshStatsButton;
    
    private TableView<MediaRoot> rootsTable;
    private ObservableList<MediaRoot> rootsData;
    
    @Override
    public void init() throws Exception {
        storageConfig = new StorageConfig();
        fileStatsService = new FileStatisticsService();
        rootConfigService = new MediaBrowseConfig();
        
        storageConfig.syncToSystemProperties();
        
        String uploadBaseDir = storageConfig.getUploadBaseDir();
        String logDir = storageConfig.getLogDir();
        
        System.out.println("========================================");
        System.out.println("应用路径配置:");
        System.out.println("应用名称: FileUploadManager");
        System.out.println("上传目录: " + uploadBaseDir);
        System.out.println("日志目录: " + logDir);
        System.out.println("========================================");
        
        new File(logDir).mkdirs();
        new File(uploadBaseDir).mkdirs();
        
        commandLineArgs = getParameters().getRaw().toArray(new String[0]);
    }
    
    @Override
    public void start(Stage primaryStage) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        Tab serviceTab = new Tab("服务管理");
        serviceTab.setContent(createServiceManagementContent());
        
        Tab rootsTab = new Tab("根目录配置");
        rootsTab.setContent(createRootsConfigContent());
        
        tabPane.getTabs().addAll(serviceTab, rootsTab);
        
        Scene scene = new Scene(tabPane, 850, 700);
        
        primaryStage.setTitle("文件上传服务管理器");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(850);
        primaryStage.setMinHeight(700);
        
        primaryStage.setOnCloseRequest(event -> handleWindowClose());
        
        updateStatistics();
        loadRoots();
        
        primaryStage.show();
    }
    
    private VBox createServiceManagementContent() {
        VBox root = new VBox(15);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(20));
        
        Label titleLabel = new Label("文件上传服务管理");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        VBox pathSection = createPathSection();
        VBox statsSection = createStatsSection();
        VBox controlSection = createControlSection();
        
        messageLabel = new Label("服务未启动");
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        messageLabel.setWrapText(true);
        
        root.getChildren().addAll(
            titleLabel, createSeparator(), pathSection,
            createSeparator(), statsSection,
            createSeparator(), controlSection, messageLabel
        );
        
        return root;
    }
    
    private BorderPane createRootsConfigContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        
        VBox topBox = new VBox(10);
        Label titleLabel = new Label("媒体根目录配置");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label descLabel = new Label("配置iOS客户端可以浏览的媒体文件夹。配置后需重启服务才能生效。");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        descLabel.setWrapText(true);
        
        topBox.getChildren().addAll(titleLabel, descLabel);
        BorderPane.setMargin(topBox, new Insets(0, 0, 15, 0));
        root.setTop(topBox);
        
        rootsTable = createRootsTableView();
        root.setCenter(rootsTable);
        
        HBox buttonBar = createRootsButtonBar();
        BorderPane.setMargin(buttonBar, new Insets(15, 0, 0, 0));
        root.setBottom(buttonBar);
        
        return root;
    }
    
    private VBox createPathSection() {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("路径配置");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        HBox uploadPathBox = new HBox(10);
        uploadPathBox.setAlignment(Pos.CENTER_LEFT);
        Label uploadLabel = new Label("上传目录:");
        uploadLabel.setStyle("-fx-min-width: 70px;");
        uploadPathLabel = new Label(storageConfig.getUploadBaseDir());
        uploadPathLabel.setStyle("-fx-text-fill: #0066cc;");
        uploadPathLabel.setWrapText(true);
        uploadPathLabel.setMaxWidth(400);
        Button changeUploadPathBtn = new Button("修改");
        changeUploadPathBtn.setOnAction(e -> changeUploadPath());
        Button openUploadDirBtn = new Button("打开");
        openUploadDirBtn.setOnAction(e -> openDirectory(storageConfig.getUploadBaseDir()));
        uploadPathBox.getChildren().addAll(uploadLabel, uploadPathLabel, changeUploadPathBtn, openUploadDirBtn);
        
        HBox logPathBox = new HBox(10);
        logPathBox.setAlignment(Pos.CENTER_LEFT);
        Label logLabel = new Label("日志目录:");
        logLabel.setStyle("-fx-min-width: 70px;");
        logPathLabel = new Label(storageConfig.getLogDir());
        logPathLabel.setStyle("-fx-text-fill: #0066cc;");
        logPathLabel.setWrapText(true);
        logPathLabel.setMaxWidth(400);
        Button openLogDirBtn = new Button("打开");
        openLogDirBtn.setOnAction(e -> openDirectory(storageConfig.getLogDir()));
        logPathBox.getChildren().addAll(logLabel, logPathLabel, openLogDirBtn);
        
        section.getChildren().addAll(sectionTitle, uploadPathBox, logPathBox);
        return section;
    }
    
    private VBox createStatsSection() {
        VBox section = new VBox(10);
        section.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");
        
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label sectionTitle = new Label("文件统计");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        refreshStatsButton = new Button("刷新");
        refreshStatsButton.setOnAction(e -> updateStatistics());
        titleBox.getChildren().addAll(sectionTitle, refreshStatsButton);
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        
        grid.add(new Label("图片:"), 0, 0);
        imageCountLabel = new Label("0");
        imageCountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        grid.add(imageCountLabel, 1, 0);
        
        grid.add(new Label("视频:"), 0, 1);
        videoCountLabel = new Label("0");
        videoCountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        grid.add(videoCountLabel, 1, 1);
        
        grid.add(new Label("实况照片:"), 0, 2);
        livePhotoCountLabel = new Label("0 对 (总文件: 0)");
        livePhotoCountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FF9800;");
        grid.add(livePhotoCountLabel, 1, 2);
        
        section.getChildren().addAll(titleBox, grid);
        return section;
    }
    
    private VBox createControlSection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.CENTER);
        section.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");
        
        Label sectionTitle = new Label("服务控制");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        HBox statusBox = new HBox(20);
        statusBox.setAlignment(Pos.CENTER);
        statusLabel = new Label("● 已停止");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");
        portLabel = new Label("端口: 未分配");
        portLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        statusBox.getChildren().addAll(statusLabel, portLabel);
        
        serviceButton = new Button("开启文件上传服务");
        serviceButton.setStyle("-fx-font-size: 16px; -fx-min-width: 200px; -fx-min-height: 40px;");
        serviceButton.setOnAction(e -> toggleService());
        
        section.getChildren().addAll(sectionTitle, statusBox, serviceButton);
        return section;
    }
    
    private Region createSeparator() {
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: #ddd;");
        return separator;
    }
    
    private void changeUploadPath() {
        if (currentPort != -1) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("警告");
            alert.setHeaderText("无法修改路径");
            alert.setContentText("请先停止服务后再修改上传路径！");
            alert.showAndWait();
            return;
        }
        
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择上传目录");
        
        File currentDir = new File(storageConfig.getUploadBaseDir());
        if (currentDir.exists()) {
            chooser.setInitialDirectory(currentDir.getParentFile());
        }
        
        File selectedDir = chooser.showDialog(serviceButton.getScene().getWindow());
        if (selectedDir != null) {
            String newPath = selectedDir.getAbsolutePath() + "/";
            storageConfig.setUploadBaseDir(newPath);
            storageConfig.syncToSystemProperties();
            
            uploadPathLabel.setText(newPath);
            updateStatistics();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("成功");
            alert.setHeaderText("路径已更新");
            alert.setContentText("上传路径已保存到配置文件");
            alert.showAndWait();
        }
    }
    
    private void openDirectory(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", dir.getAbsolutePath());
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", dir.getAbsolutePath());
            } else {
                pb = new ProcessBuilder("xdg-open", dir.getAbsolutePath());
            }
            
            pb.start();
            
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("无法打开目录");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
    
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
    
    private final ConfigurableApplicationContext[] context = new ConfigurableApplicationContext[1];
    
    private void toggleService() {
        if (context[0] == null || !context[0].isActive()) {
            startService();
        } else {
            stopService();
        }
    }
    
    private void startService() {
        serviceButton.setDisable(true);
        messageLabel.setText("文件上传服务启动中...");
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange;");
        statusLabel.setText("● 启动中...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange; -fx-font-weight: bold;");
        
        new Thread(() -> {
            try {
                int availablePort = findAvailablePort();
                
                if (availablePort == -1) {
                    javafx.application.Platform.runLater(() -> {
                        messageLabel.setText("启动失败: 端口范围 " + START_PORT + "-" + (START_PORT + PORT_RANGE - 1) + " 内无可用端口");
                        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red;");
                        statusLabel.setText("● 已停止");
                        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");
                        serviceButton.setDisable(false);
                    });
                    return;
                }
                
                currentPort = availablePort;
                
                storageConfig.syncToSystemProperties();
                
                String[] newArgs;
                if (commandLineArgs != null && commandLineArgs.length > 0) {
                    newArgs = new String[commandLineArgs.length + 1];
                    System.arraycopy(commandLineArgs, 0, newArgs, 0, commandLineArgs.length);
                    newArgs[commandLineArgs.length] = "--server.port=" + availablePort;
                } else {
                    newArgs = new String[]{"--server.port=" + availablePort};
                }
                
                context[0] = SpringApplication.run(UploadPictureApplication.class, newArgs);
                
                javafx.application.Platform.runLater(() -> {
                    messageLabel.setText("文件上传服务已启动");
                    messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green;");
                    statusLabel.setText("● 运行中");
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green; -fx-font-weight: bold;");
                    portLabel.setText("端口: " + currentPort);
                    portLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green; -fx-font-weight: bold;");
                    serviceButton.setText("关闭文件上传服务");
                    serviceButton.setDisable(false);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    messageLabel.setText("启动失败: " + ex.getMessage());
                    messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red;");
                    statusLabel.setText("● 已停止");
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");
                    serviceButton.setText("开启文件上传服务");
                    serviceButton.setDisable(false);
                });
            }
        }).start();
    }
    
    private void stopService() {
        serviceButton.setDisable(true);
        messageLabel.setText("文件上传服务关闭中...");
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange;");
        statusLabel.setText("● 关闭中...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: orange; -fx-font-weight: bold;");
        
        new Thread(() -> {
            try {
                if (context[0] != null) {
                    context[0].close();
                    context[0] = null;
                }
                currentPort = -1;
                
                javafx.application.Platform.runLater(() -> {
                    updateStatistics();
                    messageLabel.setText("文件上传服务已关闭");
                    messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
                    statusLabel.setText("● 已停止");
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");
                    portLabel.setText("端口: 未分配");
                    portLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
                    serviceButton.setText("开启文件上传服务");
                    serviceButton.setDisable(false);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    messageLabel.setText("关闭失败: " + ex.getMessage());
                    messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red;");
                    serviceButton.setDisable(false);
                });
            }
        }).start();
    }
    
    private void handleWindowClose() {
        if (context[0] != null && context[0].isActive()) {
            System.out.println("窗口关闭，正在关闭 Spring Boot 服务...");
            try {
                context[0].close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private int findAvailablePort() {
        for (int i = 0; i < PORT_RANGE; i++) {
            int port = START_PORT + i;
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return -1;
    }
    
    // ========== 根目录配置 ==========
    
    private TableView<MediaRoot> createRootsTableView() {
        TableView<MediaRoot> table = new TableView<>();
        
        TableColumn<MediaRoot, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(120);
        
        TableColumn<MediaRoot, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);
        
        TableColumn<MediaRoot, String> pathCol = new TableColumn<>("路径");
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pathCol.setPrefWidth(350);
        
        TableColumn<MediaRoot, String> iconCol = new TableColumn<>("图标");
        iconCol.setCellValueFactory(new PropertyValueFactory<>("icon"));
        iconCol.setPrefWidth(100);
        
        TableColumn<MediaRoot, Boolean> readonlyCol = new TableColumn<>("只读");
        readonlyCol.setCellValueFactory(new PropertyValueFactory<>("readonly"));
        readonlyCol.setPrefWidth(60);
        
        table.getColumns().addAll(idCol, nameCol, pathCol, iconCol, readonlyCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        return table;
    }
    
    private HBox createRootsButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        
        Button addBtn = new Button("添加根目录");
        addBtn.setOnAction(e -> showAddRootDialog());
        
        Button editBtn = new Button("编辑");
        editBtn.setOnAction(e -> showEditRootDialog());
        editBtn.disableProperty().bind(rootsTable.getSelectionModel().selectedItemProperty().isNull());
        
        Button deleteBtn = new Button("删除");
        deleteBtn.setOnAction(e -> deleteSelectedRoot());
        deleteBtn.disableProperty().bind(rootsTable.getSelectionModel().selectedItemProperty().isNull());
        deleteBtn.setStyle("-fx-text-fill: red;");
        
        Button refreshBtn = new Button("刷新");
        refreshBtn.setOnAction(e -> loadRoots());
        
        Button testBtn = new Button("测试路径");
        testBtn.setOnAction(e -> testSelectedRootPath());
        testBtn.disableProperty().bind(rootsTable.getSelectionModel().selectedItemProperty().isNull());
        
        buttonBar.getChildren().addAll(addBtn, editBtn, deleteBtn, refreshBtn, testBtn);
        return buttonBar;
    }
    
    private void loadRoots() {
        rootConfigService.reloadRoots();
        List<MediaRoot> roots = rootConfigService.getRoots();
        
        rootsData = FXCollections.observableArrayList(roots);
        rootsTable.setItems(null);
        rootsTable.setItems(rootsData);
        rootsTable.refresh();
    }
    
    private void showAddRootDialog() {
        Dialog<MediaRoot> dialog = new Dialog<>();
        dialog.setTitle("添加媒体根目录");
        dialog.setHeaderText("配置新的媒体根目录");
        
        ButtonType addButtonType = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField nameField = new TextField();
        nameField.setPromptText("例如: 家庭相册");
        
        TextField pathField = new TextField();
        pathField.setPromptText("选择目录路径");
        pathField.setEditable(false);
        
        Button browseDirBtn = new Button("浏览...");
        browseDirBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择媒体根目录");
            File selectedDir = chooser.showDialog(dialog.getOwner());
            if (selectedDir != null) {
                pathField.setText(selectedDir.getAbsolutePath() + "/");
            }
        });
        
        TextField iconField = new TextField("folder.fill");
        TextField descField = new TextField();
        descField.setPromptText("例如: 存放家庭照片和视频");
        CheckBox readonlyCheck = new CheckBox();
        
        grid.add(new Label("名称*:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("路径*:"), 0, 1);
        HBox pathBox = new HBox(5, pathField, browseDirBtn);
        pathField.setPrefWidth(250);
        grid.add(pathBox, 1, 1);
        grid.add(new Label("图标:"), 0, 2);
        grid.add(iconField, 1, 2);
        grid.add(new Label("描述:"), 0, 3);
        grid.add(descField, 1, 3);
        grid.add(new Label("只读:"), 0, 4);
        grid.add(readonlyCheck, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String name = nameField.getText().trim();
                String path = pathField.getText().trim();
                if (name.isEmpty() || path.isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "错误", "名称和路径不能为空！");
                    return null;
                }
                String icon = iconField.getText().trim();
                if (icon.isEmpty()) icon = "folder.fill";
                return rootConfigService.addRoot(name, path, icon, descField.getText().trim(), readonlyCheck.isSelected());
            }
            return null;
        });
        
        Optional<MediaRoot> result = dialog.showAndWait();
        result.ifPresent(root -> {
            if (root != null) {
                showAlert(Alert.AlertType.INFORMATION, "成功", "根目录已添加！\n请重启服务以使配置生效。");
                loadRoots();
            }
        });
    }
    
    private void showEditRootDialog() {
        MediaRoot selected = rootsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        Dialog<MediaRoot> dialog = new Dialog<>();
        dialog.setTitle("编辑媒体根目录");
        dialog.setHeaderText("修改根目录配置");
        
        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField nameField = new TextField(selected.getName());
        TextField pathField = new TextField(selected.getPath());
        pathField.setEditable(false);
        
        Button browseDirBtn = new Button("浏览...");
        browseDirBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择媒体根目录");
            File currentDir = new File(selected.getPath());
            if (currentDir.exists()) chooser.setInitialDirectory(currentDir.getParentFile());
            File selectedDir = chooser.showDialog(dialog.getOwner());
            if (selectedDir != null) pathField.setText(selectedDir.getAbsolutePath() + "/");
        });
        
        TextField iconField = new TextField(selected.getIcon());
        TextField descField = new TextField(selected.getDescription() != null ? selected.getDescription() : "");
        CheckBox readonlyCheck = new CheckBox();
        readonlyCheck.setSelected(selected.isReadonly());
        
        grid.add(new Label("名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("路径:"), 0, 1);
        HBox pathBox = new HBox(5, pathField, browseDirBtn);
        pathField.setPrefWidth(250);
        grid.add(pathBox, 1, 1);
        grid.add(new Label("图标:"), 0, 2);
        grid.add(iconField, 1, 2);
        grid.add(new Label("描述:"), 0, 3);
        grid.add(descField, 1, 3);
        grid.add(new Label("只读:"), 0, 4);
        grid.add(readonlyCheck, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                selected.setName(nameField.getText().trim());
                selected.setPath(pathField.getText().trim());
                selected.setIcon(iconField.getText().trim());
                selected.setDescription(descField.getText().trim());
                selected.setReadonly(readonlyCheck.isSelected());
                return rootConfigService.updateRoot(selected) ? selected : null;
            }
            return null;
        });
        
        Optional<MediaRoot> result = dialog.showAndWait();
        result.ifPresent(root -> {
            if (root != null) {
                javafx.application.Platform.runLater(() -> {
                    loadRoots();
                    showAlert(Alert.AlertType.INFORMATION, "成功", "根目录已更新！");
                });
                notifyServerReload();
            }
        });
    }
    
    private void notifyServerReload() {
        if (context[0] != null && context[0].isActive()) {
            try {
                MediaBrowseConfig service = context[0].getBean(MediaBrowseConfig.class);
                service.reloadRoots();
            } catch (Exception e) {
                System.out.println("无法刷新服务缓存: " + e.getMessage());
            }
        }
    }
    
    private void deleteSelectedRoot() {
        MediaRoot selected = rootsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("删除媒体根目录");
        confirm.setContentText("确定要删除 \"" + selected.getName() + "\" 吗？\n注意：这只会删除配置，不会删除实际文件。");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean deleted = rootConfigService.deleteRoot(selected.getId());
            if (deleted) {
                showAlert(Alert.AlertType.INFORMATION, "成功", "根目录已删除！");
                loadRoots();
            } else {
                showAlert(Alert.AlertType.ERROR, "错误", "删除失败！");
            }
        }
    }
    
    private void testSelectedRootPath() {
        MediaRoot selected = rootsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        File rootDir = new File(selected.getPath());
        if (!rootDir.exists()) {
            showAlert(Alert.AlertType.ERROR, "路径不存在", "路径不存在:\n" + selected.getPath());
            return;
        }
        if (!rootDir.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "不是目录", "路径不是一个有效的目录:\n" + selected.getPath());
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
        
        showAlert(Alert.AlertType.INFORMATION, "路径测试成功",
                 "路径有效！\n\n路径: " + selected.getPath() + "\n文件夹数: " + dirCount + "\n文件数: " + fileCount);
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
