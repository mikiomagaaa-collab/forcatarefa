plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Assinatura: por padrão usa a chave pública de CI que acompanha o repositório (boa para uso
// pessoal, pois todas as compilações ficam com a mesma assinatura e atualizam umas às outras).
// Para usar a sua própria chave, defina as variáveis CRISTAL_KEYSTORE_FILE,
// CRISTAL_KEYSTORE_PASSWORD, CRISTAL_KEY_ALIAS e CRISTAL_KEY_PASSWORD (o workflow faz isso a
// partir dos secrets do GitHub).
val customKeystore: String? = providers.environmentVariable("CRISTAL_KEYSTORE_FILE").orNull
    ?.takeIf { it.isNotBlank() && file(it).exists() }
val ciRunNumber: Int = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0

android {
    namespace = "app.cristal.cover"
    // As bibliotecas AndroidX atuais exigem compilar contra a API 37. Isso NÃO muda o
    // comportamento do app: quem define o comportamento é o targetSdk (36 = Android 16).
    compileSdk = 37

    defaultConfig {
        applicationId = "app.cristal.cover"
        minSdk = 33
        targetSdk = 36
        // Cada build do GitHub Actions recebe um número maior, então sempre instala por cima.
        versionCode = 140 + ciRunNumber
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // O lint roda no CI como etapa separada e informativa; não deve impedir a geração do APK.
        abortOnError = false
        checkReleaseBuilds = false
        // targetSdk 36 é intencional: o aparelho alvo roda Android 16.
        disable += "OldTargetApi"
        // O ícone adaptativo precisa ficar em mipmap-anydpi-v26: sem o sufixo, o AAPT2 não o encontra.
        disable += "ObsoleteSdkInt"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Os testes de fumaça (Robolectric) abrem as telas de verdade e precisam dos recursos.
            isIncludeAndroidResources = true
            all {
                // Renderiza com o pipeline de hardware do Android em JVM, para exercitar RenderEffect e AGSL.
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
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Recorte do objeto principal da foto (efeito de profundidade), feito no aparelho.
    implementation(libs.mlkit.subject.segmentation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
