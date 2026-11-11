plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.garminytd"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.garminytd"
        minSdk = 26
        targetSdk = 37
        // Releases are git tags vMAJOR.MINOR.PATCH; CI passes the tag as -PrideYtd.version. The code is
        // derived from it so every release installs as an update over the previous one.
        val appVersion = providers.gradleProperty("rideYtd.version").getOrElse("1.0.0")
        val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)?.groupValues?.drop(1)?.map(String::toInt)
            ?: error("rideYtd.version must be MAJOR.MINOR.PATCH, was '$appVersion'")
        require(parts[1] < 100 && parts[2] < 100) { "minor and patch must stay below 100" }
        versionName = appVersion
        versionCode = parts[0] * 10_000 + parts[1] * 100 + parts[2]
    }

    // Release key lives outside the repo: path + passwords come from ~/.gradle/gradle.properties
    // (rideYtd.storeFile / storePassword / keyAlias / keyPassword). Without them, release builds fall
    // back to the debug key so `assembleRelease` still works on any machine.
    val releaseStore = providers.gradleProperty("rideYtd.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("rideYtd.storePassword").get()
                keyAlias = providers.gradleProperty("rideYtd.keyAlias").get()
                keyPassword = providers.gradleProperty("rideYtd.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release build instead of clashing with its signature.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStore != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("rideYtd.storeFile not set: release APK is signed with the DEBUG key")
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        // Bytecode level, not the JDK running the build: Java 17 is what ART fully supports at minSdk 26.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        // Lists en + de in the system's per-app language setting (Android 13+).
        generateLocaleConfig = true
        // The app is translated into these; drop the ~80 languages bundled by AndroidX libraries so
        // system-provided text never shows up in a third language.
        localeFilters += listOf("en", "de")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
