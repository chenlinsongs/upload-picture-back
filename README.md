项目根目录/
├── README.md                        ← 本文件
├── create-dmg-simple.sh             ← ⭐ 推荐打包脚本
├── create-dmg-v2.sh                 ← 🔄 备选打包脚本
├── create-dmg.sh                    ← 📦 原始打包脚本
└── start.sh                         ← 🚀 启动脚本

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
