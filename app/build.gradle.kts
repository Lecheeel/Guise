import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.houvven.guise"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = namespace
        minSdk = 26
        targetSdk = 37
        val version = Properties().apply { file("version.properties").inputStream().use { load(it) } }
        versionCode = version.getProperty("version.code").toInt()
        versionName = version.getProperty("version.name")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    signingConfigs {
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            // local.properties is optional; required only for release signing.
            // Create it from local.properties.example to sign release builds.
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                val properties = Properties().apply { localPropsFile.inputStream().use { load(it) } }
                properties.getProperty("sign.store.file")?.let { storeFile = rootProject.file(it) }
                storePassword = properties.getProperty("sign.store.password")
                keyAlias = properties.getProperty("sign.key.alias")
                keyPassword = properties.getProperty("sign.key.password")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.getByName("release").takeIf { it.storeFile != null && it.storePassword != null && it.keyAlias != null && it.keyPassword != null }?.let { signingConfig = it }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":hook"))
    // R8 also needs the framework API on the app's library classpath.
    compileOnly(libs.xposed.api)
    implementation(libs.xposed.service)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // compose
    implementation(libs.androidx.material.icons.extended)
    // lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // destination
    implementation(libs.compose.destinations.core)
    implementation(libs.compose.destinations.bottomSheet)
    ksp(libs.compose.destinations.ksp)
    // koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)

    implementation(libs.kotlin.serialization.json)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.betterandroid.extension.system)
    implementation(libs.libsu.core)
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
