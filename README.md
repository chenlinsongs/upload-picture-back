项目根目录/
├── README.md                        ← 本文件
├── create-dmg-simple.sh             ← ⭐ 推荐打包脚本
├── create-dmg-v2.sh                 ← 🔄 备选打包脚本
├── create-dmg.sh                    ← 📦 原始打包脚本
├── start.sh                         ← 🚀 启动脚本
├── upload-picture.service           ← 🔧 systemd 服务配置模板
└── install-service.sh               ← 📋 树莓派开机自启安装脚本

## 环境要求

- JDK 17
- Maven

## 快速开始

```bash
# 编译
mvn clean package -DskipTests

# 启动（命令行模式）
java -jar target/upload-picture-back-1.0-SNAPSHOT.jar

# 启动（JavaFX GUI 模式）
./start.sh
```

## 树莓派开机自启（systemd）

适用于树莓派 500+ 等 Linux 设备，实现开机后自动启动服务。

### 相关文件

| 文件 | 作用 |
|------|------|
| `upload-picture.service` | systemd 服务配置模板，定义了服务如何运行（启动命令、运行用户、日志路径、失败重启策略等）。包含 `__USER__`、`__INSTALL_DIR__` 等占位符，不能直接使用 |
| `install-service.sh` | 一键安装脚本，自动检测 Java 路径和当前用户，将模板中的占位符替换为真实值，把配置文件复制到 `/etc/systemd/system/` 并启用服务 |

### 安装步骤

```bash
# 1. 安装 JDK 17（如果还没有）
sudo apt install openjdk-17-jdk

# 2. 构建 JAR 包
mvn clean package -DskipTests

# 3. 安装并启用开机自启（只需执行一次）
sudo ./install-service.sh
```

### 日常管理命令

```bash
sudo systemctl status upload-picture    # 查看服务状态
sudo systemctl restart upload-picture   # 重启服务（更新 JAR 后执行此命令即可）
sudo systemctl stop upload-picture      # 停止服务
sudo systemctl start upload-picture     # 启动服务
sudo systemctl disable upload-picture   # 取消开机自启
journalctl -u upload-picture -f         # 实时查看系统日志
```

### 卸载服务

```bash
sudo ./install-service.sh uninstall
```

### 注意事项

- 更新 JAR 包后**不需要**重新执行 `install-service.sh`，只需 `sudo systemctl restart upload-picture`
- 如需修改启动参数（如端口），重新执行 `sudo ./install-service.sh` 或直接编辑 `/etc/systemd/system/upload-picture.service` 后执行 `sudo systemctl daemon-reload && sudo systemctl restart upload-picture`
- 应用日志路径：`~/FileUploadManager/logs/spring-boot-logger-log4j2.log`（由 log4j2 管理，自动归档）

## HEIC 支持

解析 HEIC 文件需要在 Mac 上安装 libheif：

```bash
brew install libheif libde265
```

验证安装：

```bash
brew --prefix libheif
which heif-thumbnailer
which heif-convert
```

## 视频缩略图

解析视频需要安装 FFmpeg。代码会按以下顺序自动检测 FFmpeg 路径：

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | `/Users/linsong.chen/document/ffmpeg/ffmpeg` | 自定义路径（当前开发环境） |
| 2 | `/opt/homebrew/bin/ffmpeg` | Apple Silicon Mac (Homebrew) |
| 3 | `/usr/local/bin/ffmpeg` | Intel Mac (Homebrew) |
| 4 | `which ffmpeg` | 系统 PATH 中查找 |

> **注意：** 如果你的 FFmpeg 安装在其他位置，需要修改 `ThumbnailService.java` 中的 `FFMPEG_CUSTOM` 常量，否则视频缩略图会生成失败（显示黑色占位图）。打包为 DMG 后运行时，系统 PATH 受限，`which` 兜底可能也找不到，所以务必确认硬编码路径正确。

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /image/upload | POST | 上传图片 |
| /image/upload-video | POST | 上传视频 |
| /image/upload-livephoto | POST | 上传 Live Photo |
| /image/batch-upload | POST | 批量上传图片 |
| /image/check-exists | POST | 检查文件是否已存在 |
| /image/server-info | GET | 获取服务器信息 |
| /image/roots | GET | 获取媒体根目录列表 |
| /image/roots/{rootId}/browse | GET | 浏览目录（支持分页） |
| /image/thumbnail | GET | 获取缩略图 |
| /image/download | GET | 下载/预览文件（支持断点续传） |
| /image/upload/folders | GET | 获取上传文件夹列表 |
| /image/upload/folders/create | POST | 创建上传文件夹 |
