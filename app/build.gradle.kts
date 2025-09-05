import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")

    // Kotlin serialization plugin for type safe routes and navigation arguments
    kotlin("plugin.serialization") version "2.1.0"

    id("com.google.devtools.ksp")

    // AboutLibraries
    id("com.mikepenz.aboutlibraries.plugin")
}

// local.propertiesからAPIキーを読み込む
val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.websarva.wings.android.slevo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.websarva.wings.android.slevo"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiKey = properties.getProperty("imgbb.api.key") ?: "YOUR_IMGBB_API_KEY"
        buildConfigField("String", "IMGBB_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    applicationVariants.all {
        val variant = this
        if (variant.buildType.name == "release") {
            variant.outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val fileName = "Slevo-${variant.versionName}.apk"
                output.outputFileName = fileName
            }
            variant.assembleProvider.get().doLast {
                val apkDir = rootProject.file("apk")
                apkDir.mkdirs()
                variant.outputs.forEach {
                    val output = it as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    output.outputFile.copyTo(apkDir.resolve(output.outputFileName), overwrite = true)
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.material3)

    //okhttp
    implementation(libs.okhttp3.okhttp)

    // Coil
    implementation(libs.coil3.coil.compose)
    implementation(libs.coil3.coil.gif)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    //hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    //Moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    // JSON serialization library, works with the Kotlin serialization plugin
    implementation(libs.kotlinx.serialization.json)

    //Icons
    implementation(libs.androidx.material.icons.extended)

    //Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    //Jsoup
    implementation(libs.jsoup)

    // HttpLoggingInterceptor
    implementation(libs.okhttp3.logging.interceptor)

    // Preferences DataStore（1.1.5不具合あり）
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // compose.foundation
    implementation(libs.androidx.foundation)

    // Telephoto
    implementation(libs.zoomable.image.coil3)

    // AboutLibraries
    implementation(libs.aboutlibraries.compose.m3)

    // Timber
    implementation(libs.timber)
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}
