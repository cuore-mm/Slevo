import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.VariantOutputConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    id("com.google.dagger.hilt.android")

    // Kotlin serialization plugin for type safe routes and navigation arguments
    alias(libs.plugins.kotlin.serialization)

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

val appVersionName = "1.5.1"

android {
    namespace = "com.websarva.wings.android.slevo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.websarva.wings.android.slevo"
        minSdk = 24
        targetSdk = 35
        versionCode = 13
        versionName = appVersionName

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
        create("ci") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".ci"
            versionNameSuffix = "-ci"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Slevo (CI)")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // テストで Room のスキーマ JSON を読み込めるようにする
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // exported schema をテストの assets として参照するようにする（schemas ディレクトリを追加）
    sourceSets {
        // AboutLibraries の生成JSONを raw resource として取り込む
        getByName("main").res.srcDir("$buildDir/generated/aboutlibraries/res")
        getByName("test").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val copyReleaseApkTask = tasks.register<Copy>("copy${variantName}ApkForDistribution") {
            from(variant.artifacts.get(SingleArtifact.APK))
            include("*.apk")
            into(rootProject.layout.projectDirectory.dir("apk"))
            rename { "Slevo-$appVersionName.apk" }
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            finalizedBy(copyReleaseApkTask)
        }
    }

    onVariants(selector().withBuildType("ci")) { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        if (runNumber != null) {
            variant.outputs.forEach { output ->
                if (output.outputType == VariantOutputConfiguration.OutputType.SINGLE) {
                    output.versionCode.set(runNumber)
                }
            }
        }

        val copyCiApkTask = tasks.register<Copy>("copy${variantName}ApkForVerification") {
            from(variant.artifacts.get(SingleArtifact.APK))
            include("*.apk")
            into(rootProject.layout.projectDirectory.dir("apk"))
            rename { "Slevo-${variant.name}.apk" }
        }
        tasks.matching { it.name == "assemble$variantName" }.configureEach {
            finalizedBy(copyCiApkTask)
        }
    }
}

tasks.register("testCiUnitTest") {
    dependsOn("testDebugUnitTest")
}

tasks.named("preBuild") {
    dependsOn("exportLibraryDefinitions")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
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
    ksp(libs.hilt.android.compiler)
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
    implementation(libs.lazycolumn.scrollbar)

    // Telephoto
    implementation(libs.zoomable.image.coil3)

    // Haze
    implementation(libs.haze)

    // AboutLibraries
    implementation(libs.aboutlibraries.compose.m3)

    // Timber
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// KSP に対して Room の schema 出力先を指定
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    collect {
        // ここ以下に置いたJSONをマージできる
        configPath = file("aboutlibs")
    }
    export {
        // Android側で R.raw.aboutlibraries として解決できる出力先へ固定する
        outputFile = file("$buildDir/generated/aboutlibraries/res/raw/aboutlibraries.json")
    }
}
