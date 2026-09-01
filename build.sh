#!/usr/bin/env bash
set -e

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export PATH="$JAVA_HOME/bin:$PATH"

if ! java -version 2>&1 | grep -q "17\."; then
    echo "❌ 需要 JDK17, 当前: $(java -version 2>&1 | head -1)"
    echo "   请确认 /usr/lib/jvm/java-17-openjdk-arm64 存在"
    exit 1
fi
echo "✅ 使用 JDK17: $(java -version 2>&1 | head -1)"

MODULE_NAME="AppErrorNotify"

ACTION="${1:-patch}"   # 默认 patch (bump versionCode+1), 对齐 RikkaTune
BUMP="$ACTION"
case "$ACTION" in
    assemble|clean) : ;;
    patch|minor|major) ACTION="assemble" ;;   # bump 类型 → 实际走 assemble
    *) echo "❌ 未知命令: $ACTION (支持 patch|minor|major|assemble|clean)"; exit 1 ;;
esac

case "$ACTION" in
    assemble)
        GRADLE_FILE="app/build.gradle.kts"
        VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE" | head -1)
        VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE" | head -1)
        if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
            echo "❌ 未能读取 build.gradle.kts 版本号 (versionName='$VERSION_NAME' versionCode='$VERSION_CODE')"
            exit 1
        fi
        echo "当前版本: ${VERSION_NAME}(${VERSION_CODE})"
        case "$BUMP" in
            patch)   VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → patch: versionCode+1" ;;
            minor)   VERSION_NAME=$(echo "$VERSION_NAME" | awk -F. '{printf "%d.%d.0", $1, $2+1}') ; VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → minor: 次版本+1" ;;
            major)   VERSION_NAME=$(echo "$VERSION_NAME" | awk -F. '{printf "%d.0.0", $1+1}') ; VERSION_CODE=$((VERSION_CODE + 1)) ; echo "  → major: 主版本+1" ;;
        esac
        echo "  新版本: ${VERSION_NAME}(${VERSION_CODE})"
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
            aapt dump badging "$APK" 2>/dev/null | grep -E "package:|versionCode|versionName" || true
            echo ""
            OUT_NAME="${MODULE_NAME}_${VERSION_NAME}(${VERSION_CODE}).apk"
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
