#!/usr/bin/env bash
# =============================================================
# AppErrorsTracking (io.github.sky.apperrors) 统一构建脚本
# -------------------------------------------------------------
# ⚠️⚠️⚠️ 开发前必读 ⚠️⚠️⚠️
#   arm64 环境必须显式指定 JDK17！
#   (java-21 是 JRE-only, 无 javac, 编译报 JAVA_COMPILER 错误)
#   详见: dev-guide/README.md「构建」 / 知识库 dev-guide/实战/构建环境踩坑.md §4
#
# 用法:
#   ./build.sh              # 构建 release APK, 输出到 app/build/outputs/apk/release/
#   ./build.sh assemble     # 同默认
#   ./build.sh clean        # 清理构建产物
# =============================================================
set -e

# 强制使用 JDK17 (arm64 环境唯一可用完整 JDK)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export PATH="$JAVA_HOME/bin:$PATH"

# 验证 JDK 版本
if ! java -version 2>&1 | grep -q "17\."; then
    echo "❌ 需要 JDK17, 当前: $(java -version 2>&1 | head -1)"
    echo "   请确认 /usr/lib/jvm/java-17-openjdk-arm64 存在"
    exit 1
fi
echo "✅ 使用 JDK17: $(java -version 2>&1 | head -1)"

ACTION="${1:-assemble}"

case "$ACTION" in
    assemble)
        echo "▶ 构建 release APK..."
        ./gradlew :app:assembleRelease --console=plain --no-daemon
        APK=app/build/outputs/apk/release/app-release.apk
        if [ -f "$APK" ]; then
            echo ""
            echo "✅ 构建成功: $APK"
            aapt dump badging "$APK" 2>/dev/null | grep -E "package:|versionCode|versionName" || true
            echo ""
            echo "  可复制到 release/ :"
            echo "    cp $APK release/AppErrorNotify_\$(aapt dump badging $APK 2>/dev/null | grep versionName | sed 's/.*=//;s/ .*//')_\$(aapt dump badging $APK 2>/dev/null | grep versionCode | sed 's/.*=//;s/ .*//')_unsigned_sign.apk"
        else
            echo "❌ 构建失败, 未找到 APK"
            exit 1
        fi
        ;;
    clean)
        echo "▶ 清理构建产物..."
        ./gradlew :app:clean --console=plain --no-daemon
        echo "✅ 清理完成"
        ;;
    *)
        echo "❌ 未知命令: $ACTION"
        echo "   用法: ./build.sh [assemble|clean]"
        exit 1
        ;;
esac
