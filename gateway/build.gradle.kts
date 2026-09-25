plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.androidgateway"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        disable += "CoarseFineLocation"
    }

    defaultConfig {
        applicationId = "com.example.androidgateway"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.4"
    }
}
