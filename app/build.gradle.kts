import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// Keep the existing dev/profile task names. Select one distribution for the entire build.
val distribution = providers.gradleProperty("plutoDistribution").orElse("github").get()
require(distribution in setOf("github", "play")) { "plutoDistribution must be github or play" }
val playDistribution = distribution == "play"
val playSigningFile = file(providers.environmentVariable("PLUTO_PLAY_SIGNING_PROPERTIES")
    .orElse("${System.getProperty("user.home")}/.config/pluto-launcher/play-signing.properties").get())
val playSigning = Properties().apply {
    if (playDistribution && playSigningFile.isFile) playSigningFile.inputStream().use { load(it) }
}

// Published GitHub builds (0.1.2 onwards) carry one certificate; Android refuses updates
// signed with any other key. Where that keystore exists, every release build uses it.
val githubKeystore = file(providers.environmentVariable("PLUTO_GITHUB_KEYSTORE")
    .orElse("${System.getProperty("user.home")}/.config/.android/pluto-release.keystore").get())

android {
    namespace = "dev.pluto.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pluto.launcher"
        minSdk = 30
        targetSdk = 36
        versionCode = 9
        versionName = "0.1.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "GITHUB_UPDATES", (!playDistribution).toString())
    }

    signingConfigs {
        providers.gradleProperty("plutoDebugKey").orNull?.let { path ->
            getByName("debug").storeFile = file(path)
        }
        if (playDistribution && playSigning.isNotEmpty()) {
            create("playUpload") {
                fun required(name: String) = requireNotNull(playSigning.getProperty(name)) {
                    "Missing $name in Play signing properties"
                }
                storeFile = file(required("storeFile"))
                storePassword = required("storePassword")
                keyAlias = required("keyAlias")
                keyPassword = required("keyPassword")
            }
        }
        if (githubKeystore.isFile) {
            create("github") {
                storeFile = githubKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking: Compose in a debug build runs unoptimised and is
            // noticeably janky; daily use should always be on a release build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // GitHub keeps its existing certificate. Play uses a separate non-debug upload key.
            signingConfig = if (playDistribution) signingConfigs.findByName("playUpload")
                else (signingConfigs.findByName("github") ?: signingConfigs.getByName("debug"))
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
    sourceSets {
        getByName("main").java.srcDir("src/$distribution/java")
        getByName("androidTest").java.srcDir("src/${distribution}AndroidTest/java")
        if (playDistribution) {
            getByName("debug").manifest.srcFile("src/play/AndroidManifest.xml")
            getByName("release").manifest.srcFile("src/play/AndroidManifest.xml")
        }
        // Exported Room schemas double as migration-test fixtures.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// A Play bundle must fail closed when the private upload key has not been configured.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        check(!playDistribution || playSigning.isNotEmpty()) {
            "Configure PLUTO_PLAY_SIGNING_PROPERTIES before building a Play release."
        }
    }
}

// The profile plugin copies release sources into its generated build types. Add fixtures
// after that copy so profile/benchmark APKs get them and ordinary release stays clean.
androidComponents.finalizeDsl { extension ->
    for (type in listOf("nonMinifiedRelease", "benchmarkRelease")) {
        extension.sourceSets.getByName(type).apply {
            java.srcDir("src/nonMinifiedRelease/java")
            manifest.srcFile("src/nonMinifiedRelease/AndroidManifest.xml")
        }
    }
}

kotlin {
    jvmToolchain(17)
    sourceSets.getByName("main").kotlin.srcDir("src/$distribution/java")
    sourceSets.getByName("androidTest").kotlin.srcDir("src/${distribution}AndroidTest/java")
}

/*
 * Baseline + startup profiles (AOT-compile the launcher's hot paths at install instead of
 * running them interpreted/JIT; startup profile drives R8's dex layout).
 * Regenerate on an emulator or rooted device (API 33+ Google APIs images need no root):
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 * The generator (:baselineprofile) writes the .txt profiles to src/main/generated/baselineProfiles,
 * which are committed; release builds then ship them and profileinstaller installs them.
 * src/main/baseline-prof.txt holds broad hand-written rules that apply even before that.
 */
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    dexLayoutOptimization = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Composable names in Perfetto traces; no cost unless a trace is being recorded.
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.compose.material.icons.extended)
    // Installs the shipped baseline profile on sideloaded/adb installs too (Play does it itself).
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // DataStore pulls serialization 1.7.3, which the app classpath then pins for androidTest;
    // room-testing's schema bundles need 1.8.x (AbstractMethodError otherwise).
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
