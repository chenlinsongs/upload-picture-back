#!/bin/bash

# 文件上传服务启动脚本
# 使用JavaFX GUI界面管理Spring Boot服务
# 需要 JDK 17 + JavaFX 21

echo "=========================================="
echo "  文件上传服务管理器"
echo "=========================================="

# 优先使用 JDK 17（如果已安装）
if [ -d "/usr/local/Cellar/openjdk@17" ]; then
    export JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home
    echo "✓ 使用 JDK 17: $JAVA_HOME"
elif command -v /usr/libexec/java_home &> /dev/null; then
    if /usr/libexec/java_home -v 17 &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home -v 17)
        echo "✓ 使用 JDK 17: $JAVA_HOME"
    else
        echo "⚠ 未找到 JDK 17，使用默认 JDK"
    fi
fi

echo "JDK 版本: $(java -version 2>&1 | head -n 1)"
echo ""

# 检查jar文件是否存在
JAR_FILE="target/upload-picture-back-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到 $JAR_FILE"
    echo "请先运行: mvn clean package -DskipTests"
    exit 1
fi

# 启动应用
java -jar "$JAR_FILE" "$@"
