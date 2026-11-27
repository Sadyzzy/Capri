plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.capri.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.capri.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("ai.picovoice:porcupine-android:3.+")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
