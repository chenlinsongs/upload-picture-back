#!/bin/bash

# =============================================================================
# JavaFX 应用打包为 macOS DMG 文件的脚本
# =============================================================================

set -e

echo "========================================"
echo "开始打包 macOS DMG 安装包"
echo "========================================"

# 配置变量
APP_NAME="文件上传服务管理器"
APP_VERSION="1.0"
JAR_FILE="target/upload-picture-back-1.0-SNAPSHOT.jar"
MAIN_CLASS="com.upload.picture.JavaFxHelloWorld"
ICON_FILE="src/main/resources/icon.icns"
OUTPUT_DIR="dist"

# 检查 JDK 版本
echo "检查 JDK 版本..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "当前 JDK 版本: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: 需要 JDK 17 或更高版本，当前版本为 $JAVA_VERSION"
    exit 1
fi

# 检查 jar 文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR 文件不存在: $JAR_FILE"
    echo "请先运行 'mvn clean package -DskipTests' 构建项目"
    exit 1
fi

# 清理旧的输出目录
echo "清理旧的输出目录..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# 提取依赖到 libs 目录
echo "提取项目依赖..."
rm -rf target/libs
mkdir -p target/libs
mvn dependency:copy-dependencies -DoutputDirectory=target/libs -DincludeScope=runtime

# 构建 jpackage 命令
echo "开始打包应用..."

ORIGINAL_JAR="upload-picture-back-1.0-SNAPSHOT.jar.original"

JPACKAGE_CMD="jpackage \
    --input target \
    --name \"$APP_NAME\" \
    --main-jar $ORIGINAL_JAR \
    --main-class $MAIN_CLASS \
    --type dmg \
    --app-version $APP_VERSION \
    --dest $OUTPUT_DIR \
    --java-options '-Xmx512m' \
    --java-options '-Dfile.encoding=UTF-8' \
    --java-options '--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED' \
    --mac-package-name \"$APP_NAME\" \
    --vendor \"Your Name\" \
    --copyright \"Copyright © 2025\""

if [ -f "$ICON_FILE" ]; then
    echo "使用应用图标: $ICON_FILE"
    JPACKAGE_CMD="$JPACKAGE_CMD --icon $ICON_FILE"
else
    echo "提示: 未找到图标文件 $ICON_FILE，将使用默认图标"
fi

echo "执行打包命令..."
eval $JPACKAGE_CMD

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "✓ 打包成功！"
    echo "========================================"
    echo "DMG 文件位置: $OUTPUT_DIR/$APP_NAME-$APP_VERSION.dmg"
    
    DMG_SIZE=$(du -h "$OUTPUT_DIR"/*.dmg | cut -f1)
    echo "DMG 文件大小: $DMG_SIZE"
else
    echo ""
    echo "========================================"
    echo "✗ 打包失败"
    echo "========================================"
    exit 1
fi
