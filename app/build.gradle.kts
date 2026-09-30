plugins {
    id("com.android.application")
}

android {
    namespace = "com.vstory.test.crashstorm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vstory.test.crashstorm"
        minSdk = 26
        // targetSdk 31：避免通知运行时权限 + 自动授予精确闹钟（self-relaunch 风暴必需）
        targetSdk = 31
        versionCode = 2
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
