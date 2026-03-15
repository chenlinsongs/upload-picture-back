#!/bin/bash

# =============================================================================
# JavaFX 应用打包为 macOS DMG 文件的脚本 (Spring Boot 版本)
# =============================================================================

set -e

echo "========================================"
echo "开始打包 macOS DMG 安装包 (Spring Boot)"
echo "========================================"

# 配置变量
APP_NAME="文件上传服务管理器"
APP_VERSION="1.0"
MAIN_CLASS="com.upload.picture.JavaFxHelloWorld"
ICON_FILE="src/main/resources/icon.icns"
OUTPUT_DIR="dist"
TEMP_DIR="target/jpackage-input"

# 检查 JDK 版本
echo "检查 JDK 版本..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "当前 JDK 版本: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: 需要 JDK 17 或更高版本，当前版本为 $JAVA_VERSION"
    exit 1
fi

# 清理并重新构建项目
echo ""
echo "步骤 1: 重新构建项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误: Maven 构建失败"
    exit 1
fi

# 检查 JAR 文件
ORIGINAL_JAR="target/upload-picture-back-1.0-SNAPSHOT.jar.original"
FAT_JAR="target/upload-picture-back-1.0-SNAPSHOT.jar"

if [ ! -f "$ORIGINAL_JAR" ]; then
    echo "错误: 找不到原始 JAR 文件: $ORIGINAL_JAR"
    exit 1
fi

# 准备 jpackage 输入目录
echo ""
echo "步骤 2: 准备打包输入目录..."
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR/libs"

echo "复制应用 JAR..."
cp "$ORIGINAL_JAR" "$TEMP_DIR/app.jar"

echo "提取依赖库..."
mvn dependency:copy-dependencies -DoutputDirectory="$TEMP_DIR/libs" -DincludeScope=runtime

LIB_COUNT=$(ls -1 "$TEMP_DIR/libs" | wc -l | tr -d ' ')
echo "共提取 $LIB_COUNT 个依赖库"

# 清理旧的输出目录
echo ""
echo "步骤 3: 清理旧的输出目录..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# 构建 jpackage 命令
echo ""
echo "步骤 4: 开始打包应用..."

JPACKAGE_CMD="jpackage \
    --input \"$TEMP_DIR\" \
    --name \"$APP_NAME\" \
    --main-jar app.jar \
    --main-class $MAIN_CLASS \
    --type dmg \
    --app-version $APP_VERSION \
    --dest $OUTPUT_DIR \
    --java-options '-Xmx512m' \
    --java-options '-Xms256m' \
    --java-options '-Dfile.encoding=UTF-8' \
    --java-options '-Djava.awt.headless=false' \
    --java-options '--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED' \
    --mac-package-name \"$APP_NAME\" \
    --vendor \"Your Name\" \
    --copyright \"Copyright © 2025\""

if [ -f "$ICON_FILE" ]; then
    echo "使用应用图标: $ICON_FILE"
    JPACKAGE_CMD="$JPACKAGE_CMD --icon $ICON_FILE"
fi

echo ""
echo "执行打包命令（可能需要几分钟）..."
eval $JPACKAGE_CMD

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "✓ 打包成功！"
    echo "========================================"
    
    DMG_FILE=$(find "$OUTPUT_DIR" -name "*.dmg" | head -n 1)
    
    if [ -n "$DMG_FILE" ]; then
        echo "DMG 文件位置: $DMG_FILE"
        DMG_SIZE=$(du -h "$DMG_FILE" | cut -f1)
        echo "DMG 文件大小: $DMG_SIZE"
    fi
    
    echo "清理临时文件..."
    rm -rf "$TEMP_DIR"
    echo "✓ 清理完成"
else
    echo ""
    echo "========================================"
    echo "✗ 打包失败"
    echo "========================================"
    exit 1
fi
