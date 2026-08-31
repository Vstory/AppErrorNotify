plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.fankes.apperrors"
    compileSdk = 37

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
        applicationId = "com.fankes.apperrors"
        minSdk = 26
        targetSdk = 37
        versionName = "1.9"
        versionCode = 32
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
