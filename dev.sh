#!/bin/bash
# 快速开发脚本 - 编译并运行

set -e  # 遇到错误立即退出

echo "🔨 编译项目..."
./mvnw clean verify -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功，启动应用..."
    echo "💡 提示：按 Ctrl+C 退出应用"
    echo ""
    java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
else
    echo "❌ 编译失败"
    exit 1
fi
