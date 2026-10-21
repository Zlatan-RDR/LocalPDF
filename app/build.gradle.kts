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
        versionCode = 3
        versionName = "0.1.1"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    flavorDimensions += "mode"

    productFlavors {
        create("modern") {
            dimension = "mode"
            targetSdk = 35
            manifestPlaceholders["appLabel"] = "LocalPDF"
        }
        create("legacy") {
            dimension = "mode"
            minSdk = 28
            targetSdk = 28
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-legacy"
            manifestPlaceholders["appLabel"] = "LocalPDF Legacy"
        }
    }

    buildTypes {
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
}
