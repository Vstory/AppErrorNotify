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


    // api102 三件套（META-INF/xposed/*）必须合入 APK

}

dependencies {
    // libxposed 现代 API（本地 jar：api=框架提供 compileOnly / interface+service=模块自带 implementation）
    compileOnly(files("libs/libxposed/api.jar"))
    implementation(files("libs/libxposed/interface.jar"))
    implementation(files("libs/libxposed/service.jar"))
    implementation(libs.betterandroid.ui.extension)
    implementation(libs.libsu)
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
