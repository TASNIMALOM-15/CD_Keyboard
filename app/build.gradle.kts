plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.bkeyboard"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.bkeyboard"
        minSdk = 21            // 32-bit ARM (armeabi-v7a) সহ পুরনো ফোন সাপোর্ট
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
// কোনো লাইব্রেরি নেই: শুধু Android framework => ছোট APK, সব ABI (armeabi-v7a, arm64, x86) এ চলে
