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
            VERSION_NAME=$(aapt dump badging "$APK" 2>/dev/null | grep -oP "versionName='\K[^']+" | head -1)
            VERSION_CODE=$(aapt dump badging "$APK" 2>/dev/null | grep -oP "versionCode='\K[^']+" | head -1)
            aapt dump badging "$APK" 2>/dev/null | grep -E "package:|versionCode|versionName" || true
            if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
                echo "❌ 未能读取版本号 (versionName='$VERSION_NAME' versionCode='$VERSION_CODE'), 中止复制"
                exit 1
            fi
            echo ""
            mkdir -p release
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
