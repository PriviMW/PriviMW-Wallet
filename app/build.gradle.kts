import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

android {
    namespace = "com.privimemobile"
    compileSdk = 35

    // NDK version for native Opus encoding
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.privimemobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "1.3.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cFlags += listOf("-std=c11", "-O2")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) props.load(propsFile.inputStream())
            storeFile = file(props.getProperty("storeFile", "../privimw-release.jks"))
            storePassword = props.getProperty("storePassword", "")
            keyAlias = props.getProperty("keyAlias", "privimw")
            keyPassword = props.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "PRIVIME_CID", "\"97db059a347227d2d71fd0cb7fb5d993343ab1540d2eaf40fe48d131b611635f\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "PRIVIME_CID", "\"32c6e5836eb5d2d428acce7ca4e262c8bf9f615c142811f7cf4ee4717f8747a9\"")
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "PriviMW-v${variant.versionName}-${variant.buildType.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle + ViewModel
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Storage
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Camera (QR) — zxing-android-embedded (same as official Beam wallet)
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Lottie (animated stickers — TGS format)
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    kapt(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.biometric)
    implementation(libs.appcompat)
}
