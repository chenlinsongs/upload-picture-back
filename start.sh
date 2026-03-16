#!/bin/bash

# 文件上传服务启动脚本
#
# 用法:
#   ./start.sh                # GUI 模式（打开 Swing 管理界面）
#   ./start.sh --cli          # CLI 前台模式（日志输出到终端）
#   ./start.sh --cli -d       # CLI 后台模式（日志写入文件，进程在后台运行）
#   ./start.sh --cli --port=8080       # 指定端口
#   ./start.sh --cli -d --port=8080    # 后台 + 指定端口
#   ./start.sh stop            # 停止后台运行的服务
#   ./start.sh status          # 查看服务运行状态
#   ./start.sh log             # 实时查看后台日志

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/upload-picture-back-1.0-SNAPSHOT.jar"
LOG_DIR="$HOME/FileUploadManager/logs"
LOG_FILE="$LOG_DIR/upload.log"
PID_FILE="$LOG_DIR/app.pid"

# 优先使用 JDK 17
detect_jdk() {
    if [ -d "/usr/local/Cellar/openjdk@17" ]; then
        export JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home
    elif command -v /usr/libexec/java_home &> /dev/null; then
        if /usr/libexec/java_home -v 17 &> /dev/null; then
            export JAVA_HOME=$(/usr/libexec/java_home -v 17)
        fi
    fi
}

check_jar() {
    if [ ! -f "$JAR_FILE" ]; then
        echo "错误: 找不到 $JAR_FILE"
        echo "请先运行: mvn clean package -DskipTests"
        exit 1
    fi
}

get_running_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        fi
        rm -f "$PID_FILE"
    fi
    return 1
}

# ========== stop ==========
do_stop() {
    local pid
    pid=$(get_running_pid)
    if [ $? -ne 0 ]; then
        echo "服务未在运行"
        return 1
    fi
    echo "正在停止服务 (PID: $pid)..."
    kill "$pid"
    # 等待进程退出，最多10秒
    for i in $(seq 1 10); do
        if ! kill -0 "$pid" 2>/dev/null; then
            rm -f "$PID_FILE"
            echo "服务已停止"
            return 0
        fi
        sleep 1
    done
    echo "服务未能正常停止，强制终止..."
    kill -9 "$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo "服务已强制停止"
}

# ========== status ==========
do_status() {
    local pid
    pid=$(get_running_pid)
    if [ $? -ne 0 ]; then
        echo "服务未在运行"
        return 1
    fi
    echo "服务运行中 (PID: $pid)"
    echo "日志文件: $LOG_FILE"
}

# ========== log ==========
do_log() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "日志文件不存在: $LOG_FILE"
        exit 1
    fi
    echo "实时查看日志 (Ctrl+C 退出)..."
    echo "=========================================="
    tail -f "$LOG_FILE"
}

# ========== 主逻辑 ==========

case "${1:-}" in
    stop)
        do_stop
        exit $?
        ;;
    status)
        do_status
        exit $?
        ;;
    log)
        do_log
        exit $?
        ;;
esac

detect_jdk
check_jar

echo "=========================================="
echo "  文件上传服务管理器"
echo "=========================================="
echo "JDK 版本: $(java -version 2>&1 | head -n 1)"
echo ""

# 解析参数：分离 -d 和其他参数
DAEMON=false
APP_ARGS=()
for arg in "$@"; do
    if [ "$arg" = "-d" ] || [ "$arg" = "--daemon" ]; then
        DAEMON=true
    else
        APP_ARGS+=("$arg")
    fi
done

# 判断是否为 CLI 模式
IS_CLI=false
for arg in "${APP_ARGS[@]}"; do
    if [ "$arg" = "--cli" ] || [ "$arg" = "--headless" ]; then
        IS_CLI=true
        break
    fi
done

if [ "$DAEMON" = true ]; then
    if [ "$IS_CLI" = false ]; then
        echo "错误: -d (后台模式) 仅在 CLI 模式下可用"
        echo "用法: ./start.sh --cli -d"
        exit 1
    fi

    # 检查是否已在运行
    if get_running_pid > /dev/null; then
        echo "服务已在运行 (PID: $(cat "$PID_FILE"))"
        echo "如需重启，请先执行: ./start.sh stop"
        exit 1
    fi

    mkdir -p "$LOG_DIR"
    echo "后台启动服务..."
    echo "日志文件: $LOG_FILE"

    nohup java -jar "$JAR_FILE" "${APP_ARGS[@]}" >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"

    sleep 2
    if get_running_pid > /dev/null; then
        echo "服务已启动 (PID: $(cat "$PID_FILE"))"
        echo ""
        echo "常用命令:"
        echo "  ./start.sh status  # 查看状态"
        echo "  ./start.sh log     # 查看日志"
        echo "  ./start.sh stop    # 停止服务"
    else
        echo "启动失败，请查看日志: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
else
    # 前台运行（GUI 或 CLI 前台）
    java -jar "$JAR_FILE" "${APP_ARGS[@]}"
fi
