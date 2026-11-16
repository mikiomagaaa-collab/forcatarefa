plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Código compartilhado pelos apps do Liquid Flip (teclado, L.I.L.Y AI, Gallery): o reconhecedor do
// Info Brief e os contratos das pontes entre os apps. Sem interface; só Kotlin e o Android.
android {
    namespace = "app.lily.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
