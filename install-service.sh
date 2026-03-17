#!/bin/bash

# 树莓派开机自启安装脚本
# 用法:
#   sudo ./install-service.sh              # 安装并启用开机自启
#   sudo ./install-service.sh uninstall    # 卸载服务

set -e

SERVICE_NAME="upload-picture"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_TEMPLATE="$SCRIPT_DIR/upload-picture.service"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

if [ "$(id -u)" -ne 0 ]; then
    echo "错误: 请使用 sudo 运行此脚本"
    echo "用法: sudo $0"
    exit 1
fi

detect_java_home() {
    if [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME" ]; then
        echo "$JAVA_HOME"
        return
    fi
    local java_bin
    java_bin=$(which java 2>/dev/null || true)
    if [ -n "$java_bin" ]; then
        java_bin=$(readlink -f "$java_bin")
        echo "${java_bin%/bin/java}"
        return
    fi
    for dir in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/java-17-* /usr/lib/jvm/default-java; do
        if [ -d "$dir" ]; then
            echo "$dir"
            return
        fi
    done
    echo ""
}

get_real_user() {
    if [ -n "$SUDO_USER" ]; then
        echo "$SUDO_USER"
    else
        echo "pi"
    fi
}

do_install() {
    local real_user
    real_user=$(get_real_user)

    local java_home
    java_home=$(detect_java_home)
    if [ -z "$java_home" ]; then
        echo "错误: 找不到 Java，请先安装 JDK 17"
        echo "  sudo apt install openjdk-17-jdk"
        exit 1
    fi

    local jar_file="$SCRIPT_DIR/target/upload-picture-back-1.0-SNAPSHOT.jar"
    if [ ! -f "$jar_file" ]; then
        echo "错误: 找不到 $jar_file"
        echo "请先构建项目: mvn clean package -DskipTests"
        exit 1
    fi

    local user_home
    user_home=$(eval echo "~$real_user")
    local log_dir="$user_home/FileUploadManager/logs"

    mkdir -p "$log_dir"
    chown "$real_user:$real_user" "$log_dir"

    echo "=========================================="
    echo "  安装开机自启服务"
    echo "=========================================="
    echo "运行用户:  $real_user"
    echo "用户主目录: $user_home"
    echo "项目目录:  $SCRIPT_DIR"
    echo "JAR 文件:  $jar_file"
    echo "JAVA_HOME: $java_home"
    echo "日志文件:  $log_dir/upload.log"
    echo "=========================================="

    cp "$SERVICE_TEMPLATE" "$SERVICE_FILE"
    sed -i "s|__USER__|$real_user|g" "$SERVICE_FILE"
    sed -i "s|__INSTALL_DIR__|$SCRIPT_DIR|g" "$SERVICE_FILE"
    sed -i "s|__JAVA_HOME__|$java_home|g" "$SERVICE_FILE"
    sed -i "s|__USER_HOME__|$user_home|g" "$SERVICE_FILE"

    systemctl daemon-reload
    systemctl enable "$SERVICE_NAME"
    systemctl start "$SERVICE_NAME"

    sleep 2
    if systemctl is-active --quiet "$SERVICE_NAME"; then
        echo ""
        echo "服务安装成功并已启动!"
        echo ""
        echo "常用命令:"
        echo "  sudo systemctl status $SERVICE_NAME   # 查看状态"
        echo "  sudo systemctl stop $SERVICE_NAME      # 停止服务"
        echo "  sudo systemctl start $SERVICE_NAME     # 启动服务"
        echo "  sudo systemctl restart $SERVICE_NAME   # 重启服务"
        echo "  sudo systemctl disable $SERVICE_NAME   # 取消开机自启"
        echo "  journalctl -u $SERVICE_NAME -f         # 实时查看系统日志"
        echo "  tail -f $log_dir/upload.log            # 查看应用日志"
    else
        echo ""
        echo "服务已安装但启动可能失败，请检查:"
        echo "  sudo systemctl status $SERVICE_NAME"
        echo "  journalctl -u $SERVICE_NAME --no-pager -n 50"
    fi
}

do_uninstall() {
    echo "正在卸载服务..."
    systemctl stop "$SERVICE_NAME" 2>/dev/null || true
    systemctl disable "$SERVICE_NAME" 2>/dev/null || true
    rm -f "$SERVICE_FILE"
    systemctl daemon-reload
    echo "服务已卸载完成"
}

case "${1:-}" in
    uninstall|remove)
        do_uninstall
        ;;
    *)
        do_install
        ;;
esac
