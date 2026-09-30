#!/usr/bin/env bash
# =============================================================
# AppErrorsTracking (io.github.vstory.apperrors) 统一构建脚本
# -------------------------------------------------------------
# ⚠️⚠️⚠️ 开发前必读 ⚠️⚠️⚠️
#   arm64 环境必须显式指定 JDK17！
#   (java-21 是 JRE-only, 无 javac, 编译报 JAVA_COMPILER 错误)
#   详见: dev-project/README.md「构建」 / 知识库 dev-guide/实战/构建环境踩坑.md §4
#
# 用法:
#   ./build.sh              # 默认 patch: versionCode+1, versionName 不变, 构建发布版(统一命名)
#   ./build.sh minor        # 次版本+1, versionCode+1 (1.14 → 1.15)
#   ./build.sh major        # 主版本+1, versionCode+1 (1.14 → 2.0)
#   ./build.sh assemble     # 不 bump, 只构建(读当前版本, 不改版本号)
#   ./build.sh clean        # 清理构建产物
# 版本来源: app/build.gradle.kts 的 versionName/versionCode (单一来源, bump 后写回)
#
# 产物命名规范 (AppErrorNotify 特例, 2026-09-02 用户确认方案B):
#   - build/ 内构建产物保留原名 (app/build/outputs/apk/release/app-release.apk)
#   - 构建完成后自动【重命名复制】到 dev-project/releases/ : ${MODULE_NAME}_${VERSION_NAME}(${VERSION_CODE}).apk
#     例: AppErrorNotify_1.14(70).apk
#   - ⚠️ 本项目构建层【不分 debug/release】: 调试日志由 UI 开关(ConfigData.isEnableDebug)运行时控制,
#     源码 Debug.d 调用【永不注释】(正式版也保留, 否则 UI 开关作废),
#     产物统一命名, 无 _debug/_release 后缀. 勿套用 RikkaTune 的 Debug.d 扫描判定!
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

# 📌 模块名（输出 APK 文件名，参照 RikkaTune）
MODULE_NAME="AppErrorNotify"

# 解析命令 + bump 类型 (参照 RikkaTune)
ACTION="${1:-patch}"   # 默认 patch (bump versionCode+1), 对齐 RikkaTune
BUMP="$ACTION"
# assemble/clean 是动作; 其余当 bump 类型(每次构建都 bump)
case "$ACTION" in
    assemble|clean) : ;;
    patch|minor|major) ACTION="assemble" ;;   # bump 类型 → 实际走 assemble
    *) echo "❌ 未知命令: $ACTION (支持 patch|minor|major|assemble|clean)"; exit 1 ;;
esac

case "$ACTION" in
    assemble)
        # ---------- 版本管理 (参照 RikkaTune: patch|minor|major) ----------
        # 版本单一来源: app/build.gradle.kts 的 versionName/versionCode
        # 读当前版本 (从 build.gradle.kts 提取, 保留版本来源一致)
        GRADLE_FILE="app/build.gradle.kts"
        VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE" | head -1)
        VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE" | head -1)
        if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
            echo "❌ 未能读取 build.gradle.kts 版本号 (versionName='$VERSION_NAME' versionCode='$VERSION_CODE')"
            exit 1
        fi
        echo "当前版本: ${VERSION_NAME}(${VERSION_CODE})"
        # bump 版本 (除非 assemble 显式构建, 不在 bump)
        case "$BUMP" in
            patch)   VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → patch: versionCode+1" ;;
            minor)   VERSION_NAME=$(echo "$VERSION_NAME" | awk -F. '{printf "%d.%d", $1, $2+1}') ; VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → minor: 次版本+1" ;;
            major)   VERSION_NAME=$(echo "$VERSION_NAME" | awk -F. '{printf "%d.0", $1+1}') ; VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → major: 主版本+1" ;;
        esac
        echo "  新版本: ${VERSION_NAME}(${VERSION_CODE})"
        # 写回 build.gradle.kts (版本单一来源, Gradle 编译时读它)
        sed -i "s/versionName\s*=\s*\"[^\"]*\"/versionName = \"$VERSION_NAME\"/" "$GRADLE_FILE"
        sed -i "s/versionCode\s*=\s*[0-9]*/versionCode = $VERSION_CODE/" "$GRADLE_FILE"
        echo "  ✅ 已更新 $GRADLE_FILE"
        echo "▶ 构建 release APK..."
        ./gradlew :app:assembleRelease --console=plain --no-daemon
        APK=app/build/outputs/apk/release/app-release.apk
        if [ -f "$APK" ]; then
            echo ""
            echo "✅ 构建成功: $APK"
            echo "   (build/ 内保留原名, 不重命名)"
            # 确认产物版本 (编译后应与 build.gradle.kts 一致)
            aapt dump badging "$APK" 2>/dev/null | grep -E "package:|versionCode|versionName" || true
            echo ""
            # ---------- 产物命名 (AppErrorNotify 特例: 不分 debug/release) ----------
            # ⚠️ 本项目调试日志由 UI 开关(ConfigData.isEnableDebug)运行时控制,
            #    Debug.d 调用永不注释 → 不再扫源码判 _debug/_release (2026-09-02 用户确认方案B)
            OUT_NAME="${MODULE_NAME}_${VERSION_NAME}(${VERSION_CODE}).apk"
            # 确保 dev-project/releases/ 目录存在
            mkdir -p dev-project/releases
            echo "  按规范重命名复制 → dev-project/releases/$OUT_NAME"
            cp "$APK" "dev-project/releases/$OUT_NAME"
            echo "  ✅ 已复制: dev-project/releases/$OUT_NAME"
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
