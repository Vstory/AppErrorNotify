#!/usr/bin/env bash
# =============================================================
# AppErrorsTracking (io.github.sky.apperrors) 统一构建脚本
# -------------------------------------------------------------
# ⚠️⚠️⚠️ 开发前必读 ⚠️⚠️⚠️
#   arm64 环境必须显式指定 JDK17！
#   (java-21 是 JRE-only, 无 javac, 编译报 JAVA_COMPILER 错误)
#   详见: dev-project/README.md「构建」 / 知识库 dev-guide/实战/构建环境踩坑.md §4
#
# 用法:
#   ./build.sh              # 构建 release APK, 输出到 app/build/outputs/apk/release/
#   ./build.sh assemble     # 同默认
#   ./build.sh clean        # 清理构建产物
#
# 产物命名规范 (用户约定):
#   - build/ 内构建产物保留原名 (app/build/outputs/apk/release/app-release.apk)
#   - 构建完成后自动【重命名复制】到 release/ : v{versionName}.{versionCode}.apk
#     例: v1.14.70.apk   (versionName=1.14, versionCode=70)
#   ⚠️ 版本号一律用点分隔, 不用括号 ()  (括号会被 GitHub 转义→重命名)
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
            echo "   (build/ 内保留原名, 不重命名)"
            # 读取版本信息 (版本号规范: v{versionName}.{versionCode}.apk)
            VERSION_NAME=$(aapt dump badging "$APK" 2>/dev/null | grep -oP "versionName='\K[^']+" | head -1)
            VERSION_CODE=$(aapt dump badging "$APK" 2>/dev/null | grep -oP "versionCode='\K[^']+" | head -1)
            aapt dump badging "$APK" 2>/dev/null | grep -E "package:|versionCode|versionName" || true
            if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
                echo "❌ 未能读取版本号 (versionName='$VERSION_NAME' versionCode='$VERSION_CODE'), 中止复制"
                exit 1
            fi
            echo ""
            # 确保 release/ 目录存在
            mkdir -p release
            # 按规范【重命名复制】到 release/ (v{versionName}.{versionCode}.apk)
            OUT_NAME="v${VERSION_NAME}.${VERSION_CODE}.apk"
            echo "  按规范重命名复制 → release/$OUT_NAME"
            cp "$APK" "release/$OUT_NAME"
            echo "  ✅ 已复制: release/$OUT_NAME"
            echo ""
            echo "  可进一步签名后发布到 GitHub (见 知识库/工作流程/子流程/通用规范流程/发布流程.md)"
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
