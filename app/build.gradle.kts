import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val localSigningProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull ?: localSigningProperties.getProperty(property)

val releaseAbis = providers.gradleProperty("releaseAbis").orNull
    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
require(releaseAbis == null || (releaseAbis.isNotEmpty() && releaseAbis.all {
    it in setOf("x86", "arm64-v8a", "armeabi-v7a")
})) { "releaseAbis must contain only x86, arm64-v8a, or armeabi-v7a" }

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
            // minSdk 26 supports APK Signature Scheme v2 and newer.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            signingValue("SIGNING_KEYSTORE_PATH", "sign.store.file")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("SIGNING_STORE_PASSWORD", "sign.store.password")
            keyAlias = signingValue("SIGNING_KEY_ALIAS", "sign.key.alias")
            keyPassword = signingValue("SIGNING_KEY_PASSWORD", "sign.key.password")
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
            val releaseSigning = signingConfigs.getByName("release").takeIf {
                it.storeFile != null && it.storePassword != null && it.keyAlias != null && it.keyPassword != null
            }
            if (providers.gradleProperty("requireReleaseSigning").orNull == "true") {
                require(releaseSigning != null && releaseSigning.storeFile!!.isFile) {
                    "Release publishing requires a complete release signing configuration"
                }
            }
            releaseSigning?.let { signingConfig = it }
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
    androidResources {
        // The application currently provides English and Simplified Chinese.
        localeFilters += listOf("en", "zh-rCN")
    }
    splits {
        abi {
            isEnable = releaseAbis != null
            isUniversalApk = false
            reset()
            releaseAbis?.let { include(*it.toTypedArray()) }
        }
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
