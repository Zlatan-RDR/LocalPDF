plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localpdf.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localpdf.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.2"
        manifestPlaceholders["appLabel"] = "LocalPDF"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("shared") {
            val keystorePath = System.getenv("LOCALPDF_KEYSTORE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            if (System.getenv("LOCALPDF_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/versions/**"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")

    // On-device translation (English to Arabic) for long tool descriptions
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
}
