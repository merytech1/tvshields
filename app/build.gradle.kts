plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing (roadmap 9.2). The key never enters the repo: CI restores it from GitHub secrets
// and passes it through these variables. Without them the release APK is built unsigned.
val releaseKeystore = providers.environmentVariable("TVSHIELDS_KEYSTORE").orNull
val releaseKeystorePassword = providers.environmentVariable("TVSHIELDS_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TVSHIELDS_KEY_ALIAS").orElse("tvshields").get()

android {
    namespace = "io.github.merytech1.tvshields"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.merytech1.tvshields"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // rules.md T3: only the ABIs the engine is built for. One APK per ABI plus a universal one
    // (roadmap 9.4); AGP does not allow `ndk.abiFilters` together with ABI splits.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // Without ndk.abiFilters the universal APK would also carry the x86 libraries of AndroidX, and
    // an x86 device would install an app that has no engine.
    packaging {
        jniLibs {
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                // PKCS12 keystores use the store password for the key too.
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
