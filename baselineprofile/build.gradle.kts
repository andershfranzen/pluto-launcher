/*
 * Baseline / startup profile generator and startup benchmark for :app (Macrobenchmark +
 * UiAutomator). Runs on a connected emulator or rooted device:
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.pluto.launcher.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    // The integrator runs this on emulator-5554 (no Gradle-managed device).
    useConnectedDevices = true
}

/*
 * SAFETY: a connected test run installs dev.pluto.launcher on EVERY connected device and
 * UNINSTALLS it (with all its data) afterwards, and the baseline profile plugin makes this
 * module's `assemble` depend on that run. So device tasks only run when they were asked for
 * by name (generateBaselineProfile / connected...) AND ANDROID_SERIAL picks the device;
 * a plain `assemble` / `build` never touches a device.
 */
val requestedByName = gradle.startParameter.taskNames.any {
    it.contains("BaselineProfile", ignoreCase = true) || it.contains("connected", ignoreCase = true)
}
val chosenDevice: String? = System.getenv("ANDROID_SERIAL")
tasks.configureEach {
    if (name.startsWith("connected")) {
        onlyIf("Device tasks run only when requested by name with ANDROID_SERIAL set") {
            if (requestedByName && chosenDevice.isNullOrBlank()) {
                throw GradleException(
                    "Set ANDROID_SERIAL (e.g. emulator-5554): this run installs and then uninstalls " +
                        "dev.pluto.launcher, with its data, on the device it runs on.",
                )
            }
            requestedByName && !chosenDevice.isNullOrBlank()
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
