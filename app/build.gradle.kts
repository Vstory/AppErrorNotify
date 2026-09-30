plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.vstory.apperrors"
    compileSdk = 37
    // arm64 沙箱只装了 Commit451 的 arm64 build-tools 37.0.0（AGP 默认要的 36.0.0 不存在）
    buildToolsVersion = "37.0.0"

    signingConfigs {
        create("universal") {
            keyAlias = "public"
            keyPassword = "123456"
            storeFile = rootProject.file(".secret/universal.p12")
            storePassword = "123456"
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    defaultConfig {
        applicationId = "io.github.vstory.apperrors"
        minSdk = 26
        targetSdk = 37
        versionName = "1.18"
        versionCode = 80
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        all { signingConfig = signingConfigs.getByName("universal") }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint { checkReleaseBuilds = false }

    // 语言裁剪（2026-09-30 用户要求：只保留中文 + 英文）
    // 项目自身只有 values/（英）+ values-zh-rCN（简中）；APK 里另外 80 余种语言全是
    // 依赖库（Material Components / AppCompat）自带的翻译被一起打进包。
    // 这里用【打包过滤】而不是删 values-XX 目录：语言资源来自依赖库，删不掉；
    // 且 app 侧一处过滤即可覆盖全部依赖库语言资源。
    // AGP 9.2.1 的 resConfigs / resourceConfigurations 已 @Deprecated，官方指向本 DSL。
    // ⚠️ 写法有实测约束（2026-09-30，AGP 9.2.1 + aapt2）：localeFilters 的值会被【原样传给
    //    aapt2 的 -c】，所以必须写【资源限定符格式】而不是 BCP-47：
    //        "zh-rCN" ✓    /    "zh-CN" ✗ → AAPT: error: invalid config 'zh-CN' for -c option
    //    且 -c 是【精确匹配】而非语言前缀：写 "zh" 匹配不到 values-zh-rCN ⇒ 中文照样被删掉
    //    （曾误以为 "zh" 够用，实测 APK 里中文消失，只剩 default）。
    //    英文无需列出：项目的英文就在默认 values/ 里（无 locale 限定符），永远保留。
    //    若日后要连库的繁体翻译一起留，加 "zh-rHK"、"zh-rTW"。
    androidResources {
        localeFilters += listOf("zh-rCN")
    }

    // api102 三件套（META-INF/xposed/*）必须合入 APK

}

dependencies {
    // libxposed 现代 API（本地 jar：api=框架提供 compileOnly / interface+service=模块自带 implementation）
    compileOnly(files("libs/libxposed/api.jar"))
    implementation(files("libs/libxposed/interface.jar"))
    implementation(files("libs/libxposed/service.jar"))
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.libsu)
    // ⚠️ 不要给 drawabletoolbox 加 exclude / 改依赖坐标（2026-09-30 踩坑，见构建方案.md）
    // 该坐标在 jitpack 与 maven central 镜像上各有一份**不同的 POM**：
    //   jitpack 版：无 <dependencies> 段（本项目历史上一直用这份 ⇒ 老 support 从未进图）
    //   阿里云版：声明 com.android.support:appcompat-v7:27.1.1 ⇒ 拖入整套 2018 老库
    //             ⇒ 与 androidx.core 自带的 android.support.v4.* 撞 11 对重复类
    // 正确做法是保证依赖仓库顺序里 jitpack 优先（镜像放兜底位），而不是在本文件里 exclude。
    implementation(libs.drawabletoolbox)
    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
