import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "io.betta.pix"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.betta.pix"
        minSdk = 30
        targetSdk = 36
        // A release build is numbered by CI (PIX_VERSION_CODE); a local build is 1.
        versionCode = System.getenv("PIX_VERSION_CODE")?.toInt() ?: 1
        // Pix's own version: a new one is released when this changes on main.
        versionName = "1.0.0"
    }

    // Releases are signed with Pix's key, handed in through the environment. Without it a release build is unsigned.
    signingConfigs {
        create("release") {
            System.getenv("PIX_RELEASE_STORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("PIX_RELEASE_PASSWORD")
            keyAlias = "pix"
            keyPassword = System.getenv("PIX_RELEASE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("PIX_RELEASE_STORE") != null) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is a stub in JVM tests; this is the real one, for parsing fixtures.
    testImplementation("org.json:json:20260814")
}
