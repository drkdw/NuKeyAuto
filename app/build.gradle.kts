import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Read signing credentials from local.properties (not in git)
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

android {
    namespace = "com.ddw.nukeyauto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ddw.nukeyauto"
        minSdk = 29
        targetSdk = 36
        val versionCodeGenerated = (System.currentTimeMillis() / 1000).toInt()

        versionCode = versionCodeGenerated
        versionName = "1.2.11"
    }

    signingConfigs {
        create("release") {
            val ksFile = localProps.getProperty("KEYSTORE_FILE", "")
            if (ksFile.isNotEmpty()) {
                storeFile = file(ksFile)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // Android framework classes (Log, etc.) return default values instead of throwing
        // RuntimeException when called from JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Android Auto
    implementation(libs.androidx.app)

    // Lifecycle & Architecture
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Networking (Retrofit)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)

    // Security (encrypted storage)
    implementation(libs.androidx.security.crypto)

    // Google Play Billing (support the developer)
    implementation(libs.billing)

    implementation(libs.androidx.swiperefreshlayout)

    // M3 Expressive shape morphing
    implementation(libs.graphics.shapes)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)

}

