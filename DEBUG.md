# 客户端调试最佳实践

## 调试方式对比

| 方式 | 速度 | 适用场景 | 日志 | 热重载 |
|------|------|---------|------|--------|
| **直接运行 Fat JAR** ⭐ | 快 (3-5秒) | 日常开发测试 | 控制台 | ❌ |
| **Maven JavaFX Plugin** | 快 (5-10秒) | 开发调试 | 控制台 | ❌ |
| **IDE 直接运行** | 最快 (1-2秒) | 开发调试 | IDE 内置 | ✅ (部分) |
| **打包 dmg 安装** | 很慢 (30-60秒) | 发布前测试 | 系统日志 | ❌ |

---

## 方式一：直接运行 Fat JAR（推荐日常调试）

### 快速启动

```bash
# 编译并运行（一条命令）
./mvnw clean verify -DskipTests && \
java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
```

### 带日志输出

```bash
# 保存日志到文件，同时显示在控制台
java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar 2>&1 | tee logs/app-$(date +%Y%m%d-%H%M%S).log
```

### 调试模式启动

```bash
# 开启详细日志（包括 FINE 级别）
java -Djava.util.logging.config.file=logging.properties \
     -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
```

---

## 方式二：使用 JavaFX Maven Plugin

### 直接运行（无需打包）

```bash
# 只编译 client 模块并运行
./mvnw clean compile -pl everything-client && \
./mvnw javafx:run -pl everything-client
```

**优点：**
- 不需要打包 JAR
- 启动速度快
- 适合频繁修改 UI 代码

---

## 方式三：IDE 直接运行（最快）

### IntelliJ IDEA / Cursor

1. **配置运行配置**
   - Main class: `com.yeahmobi.everything.Launcher`
   - VM options: `-Dfile.encoding=UTF-8 -Xmx512m`
   - Working directory: `$PROJECT_DIR$/everything-client`

2. **运行/调试**
   - 快捷键：`Shift + F10` (运行) / `Shift + F9` (调试)
   - 支持断点调试
   - 支持热重载（部分代码修改无需重启）

---

## 日志配置

### 创建日志配置文件

创建 `logging.properties`：

```properties
# 日志级别：SEVERE > WARNING > INFO > FINE > FINER > FINEST
.level=INFO

# 控制台日志
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler

# 控制台格式
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter

# 文件日志（自动轮转）
java.util.logging.FileHandler.level=FINE
java.util.logging.FileHandler.pattern=logs/app-%u-%g.log
java.util.logging.FileHandler.limit=10485760
java.util.logging.FileHandler.count=5
java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter

# 日志格式（显示时间、类名、方法名）
java.util.logging.SimpleFormatter.format=%1$tF %1$tT %4$s %2$s %5$s%6$s%n

# 特定包的日志级别
com.yeahmobi.everything.level=FINE
com.yeahmobi.everything.auth.level=FINE
com.yeahmobi.everything.ui.level=INFO
```

### 使用日志配置

```bash
java -Djava.util.logging.config.file=logging.properties \
     -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
```

---

## 推荐工作流

### 日常开发（UI 修改）

```bash
# 1. 修改 FXML/CSS
# 2. 快速重新运行
./mvnw clean verify -DskipTests && \
java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
```

**估计时间：** 15-20 秒（编译） + 3 秒（启动）

---

### 调试飞书登录/注册

```bash
# 创建日志目录
mkdir -p logs

# 启动并保存详细日志
java -Djava.util.logging.config.file=logging.properties \
     -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar \
     2>&1 | tee logs/debug-$(date +%Y%m%d-%H%M%S).log
```

**查看实时日志：**
```bash
# 过滤关键信息
tail -f logs/app-0-0.log | grep -E "(Feishu|OAuth|验证码|ERROR|WARNING)"
```

---

### 发布前测试（完整流程）

```bash
# 1. 完整构建
./mvnw clean install -DskipTests

# 2. 生成 dmg
./mvnw jpackage:jpackage -pl everything-client

# 3. 安装测试
open everything-client/target/dist/Yeahmobi\ Everything-1.0.0.dmg
```

**估计时间：** 40-50 秒

---

## 快速脚本

### 创建 `dev.sh`（快速开发）

```bash
#!/bin/bash
# 快速开发脚本

echo "🔨 编译项目..."
./mvnw clean verify -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功，启动应用..."
    java -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar
else
    echo "❌ 编译失败"
    exit 1
fi
```

```bash
chmod +x dev.sh
./dev.sh
```

---

### 创建 `debug.sh`（调试模式）

```bash
#!/bin/bash
# 调试模式脚本

mkdir -p logs

LOG_FILE="logs/debug-$(date +%Y%m%d-%H%M%S).log"

echo "🔨 编译项目..."
./mvnw clean verify -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功，启动应用（调试模式）..."
    echo "📋 日志保存到：$LOG_FILE"
    
    java -Djava.util.logging.config.file=logging.properties \
         -jar everything-client/target/yeahmobi-everything-1.0.0-all.jar \
         2>&1 | tee "$LOG_FILE"
else
    echo "❌ 编译失败"
    exit 1
fi
```

```bash
chmod +x debug.sh
./debug.sh
```

---

## 查看日志技巧

### 实时查看所有日志

```bash
tail -f logs/app-0-0.log
```

### 只看错误和警告

```bash
tail -f logs/app-0-0.log | grep -E "(ERROR|WARNING)"
```

### 只看飞书相关

```bash
tail -f logs/app-0-0.log | grep -i feishu
```

### 只看认证相关

```bash
tail -f logs/app-0-0.log | grep -E "(登录|注册|验证码|Session)"
```

### 查看最近的错误

```bash
grep -E "(ERROR|Exception)" logs/app-0-0.log | tail -20
```

---

## 性能对比

| 操作 | Fat JAR | JavaFX Plugin | IDE | dmg 安装 |
|------|---------|---------------|-----|---------|
| 首次编译 | 15s | 15s | 15s | 50s |
| 仅改 Java | 15s | 10s | 3s | 50s |
| 仅改 FXML/CSS | 15s | 5s | 1s | 50s |
| 启动时间 | 3s | 5s | 2s | 3s |

**建议：**
- **频繁修改 UI** → 使用 IDE 直接运行
- **测试完整流程** → 使用 Fat JAR
- **调试网络/数据库** → 使用调试脚本（保存日志）
- **发布前测试** → 打包 dmg

---

## macOS 系统日志查看

如果已经安装了 dmg，可以查看系统日志：

```bash
# 查看控制台日志
log show --predicate 'process == "Yeahmobi Everything"' --last 1h

# 实时查看
log stream --predicate 'process == "Yeahmobi Everything"'
```

---

## 常见问题

### Q: 修改了代码，但运行的还是旧版本？

**A:** 确保重新编译：
```bash
./mvnw clean verify -DskipTests
```

### Q: 日志文件太大怎么办？

**A:** `FileHandler` 已配置自动轮转：
- 单个文件最大 10MB
- 最多保留 5 个文件
- 超出后自动删除旧文件

### Q: 想看更详细的日志？

**A:** 修改 `logging.properties`：
```properties
# 改为 FINE 或 FINEST
com.yeahmobi.everything.level=FINEST
```

### Q: IDE 报错找不到 JavaFX？

**A:** 确保项目已正确导入：
```bash
./mvnw clean install -DskipTests
# 然后在 IDE 中 Reload Maven Project
```
