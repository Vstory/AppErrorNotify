import java.util.Properties

plugins {
    id("com.android.application")
}

// 签名：与主模块同一套固定密钥（CI 从 secrets 落盘到 local.properties）。
// ⚠️ 缺项必须炸掉：AGP 在 signingConfig 缺失时不报错，而是静默产出 -unsigned.apk。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.vstory.test.crashstorm"
    // 与主模块一致：runner 上的 SDK 就是 37，避免多下一个平台版本
    compileSdk = 37

    signingConfigs {
        create("universal") {
            val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .filter { localProps.getProperty(it).isNullOrBlank() }
            require(missing.isEmpty()) { "local.properties 缺少签名项: $missing" }
            storeFile = rootProject.file(localProps.getProperty("storeFile"))
            storePassword = localProps.getProperty("storePassword")
            keyAlias = localProps.getProperty("keyAlias")
            keyPassword = localProps.getProperty("keyPassword")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.vstory.test.crashstorm"
        minSdk = 26
        // targetSdk 31：避免通知运行时权限 + 自动授予精确闹钟（self-relaunch 风暴必需）
        targetSdk = 31
        // ciRun 由 CI 注入（github.run_number）：+1000 保证 versionCode 单调递增，覆盖安装不被拒。
        // versionName 的后缀与主仓库同一套（1.1+ci.<提交短号>，见 build.yml 的 -PappVersionSuffix）：
        //   并装多个 ci 包时靠短号区分是哪个提交，用 run_number 分不出来。
        versionCode = (findProperty("ciRun") as String?)?.toIntOrNull()?.plus(1000) ?: 2
        versionName = "1.1" + (findProperty("appVersionSuffix")?.let { "+$it" } ?: "")
    }

    buildTypes {
        all { signingConfig = signingConfigs.getByName("universal") }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // targetSdk 31 是刻意为之（规避通知运行时权限 + 精确闹钟自动授予），
    // 而 lint 的 ExpiredTargetSdkVersion 是 Play 上架规则 —— 与本测试包无关，
    // 不关掉会直接阻断 assembleRelease（调的是 lintVitalRelease）。
    lint {
        checkReleaseBuilds = false
    }
}
