plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Mesma assinatura do Cristal e do teclado: as pontes entre os apps usam permissões de assinatura.
val customKeystore: String? = providers.environmentVariable("CRISTAL_KEYSTORE_FILE").orNull
    ?.takeIf { it.isNotBlank() && file(it).exists() }
val ciRunNumber: Int = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0

android {
    namespace = "app.lily.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lily.gallery"
        minSdk = 33
        targetSdk = 36
        versionCode = 100 + ciRunNumber
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("cristal") {
            if (customKeystore != null) {
                storeFile = file(customKeystore)
                storePassword = providers.environmentVariable("CRISTAL_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CRISTAL_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CRISTAL_KEY_PASSWORD").orNull
            } else {
                storeFile = rootProject.file("keystore/cristal-ci.jks")
                storePassword = "cristal-ci"
                keyAlias = "cristal"
                keyPassword = "cristal-ci"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("cristal")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("cristal")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "OldTargetApi"
        disable += "ObsoleteSdkInt"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
