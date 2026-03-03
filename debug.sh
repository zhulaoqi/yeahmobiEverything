#!/bin/bash
# 调试模式脚本 - 编译、运行并保存日志

set -e

# 创建日志目录
mkdir -p logs

LOG_FILE="logs/debug-$(date +%Y%m%d-%H%M%S).log"

echo "🔨 编译项目..."
./mvnw clean verify -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功，启动应用（调试模式）..."
    echo "📋 日志同时保存到：$LOG_FILE"
    echo "🔍 可以使用以下命令实时查看日志："
    echo "   tail -f $LOG_FILE"
    echo "   tail -f $LOG_FILE | grep -E '(Feishu|OAuth|验证码|ERROR|WARNING)'"
    echo ""
    echo "💡 提示：按 Ctrl+C 退出应用"
    echo ""
    
    # 运行应用，同时输出到控制台和文件
    java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar 2>&1 | tee "$LOG_FILE"
else
    echo "❌ 编译失败"
    exit 1
fi
