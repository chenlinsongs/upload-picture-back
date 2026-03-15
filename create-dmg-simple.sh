#!/bin/bash

# =============================================================================
# JavaFX Spring Boot 应用打包为 macOS DMG - 最简单方案
# =============================================================================

set -e

echo "========================================"
echo "开始打包 macOS DMG 安装包"
echo "========================================"

# 配置变量
APP_NAME="文件上传服务管理器"
APP_VERSION="1.0"
JAR_FILE="target/upload-picture-back-1.0-SNAPSHOT.jar"
ICON_FILE="src/main/resources/icon.icns"
OUTPUT_DIR="dist"

# 检查 JDK 版本
echo "检查 JDK 版本..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "当前 JDK 版本: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: 需要 JDK 17 或更高版本"
    exit 1
fi

# 检查 JAR 文件
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR 文件不存在，开始构建..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "错误: 构建失败"
        exit 1
    fi
fi

# 清理输出目录
echo "清理输出目录..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# 使用 jpackage 打包
echo "开始打包应用..."

jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --input target \
    --main-jar upload-picture-back-1.0-SNAPSHOT.jar \
    --main-class org.springframework.boot.loader.JarLauncher \
    --dest "$OUTPUT_DIR" \
    --java-options "-Xmx512m" \
    --java-options "-Xms256m" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" \
    --mac-package-name "$APP_NAME" \
    --vendor "Your Name" \
    --copyright "Copyright © 2025" \
    ${ICON_FILE:+--icon "$ICON_FILE"}

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "✓ 打包成功！"
    echo "========================================"
    
    DMG_FILE=$(find "$OUTPUT_DIR" -name "*.dmg" | head -n 1)
    if [ -n "$DMG_FILE" ]; then
        echo "DMG 文件: $DMG_FILE"
        echo "文件大小: $(du -h "$DMG_FILE" | cut -f1)"
    fi
else
    echo "打包失败"
    exit 1
fi
