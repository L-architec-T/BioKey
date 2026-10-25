import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// ── Versioning ────────────────────────────────────────────────────────────────────────────────
// The repo-root VERSION file is shared with the desktop app (one release = one version for
// both); the release workflow checks the pushed tag matches it. versionCode is derived so it
// always grows with the version: major*10000 + minor*100 + patch (0.2.0 -> 200).
val curName = rootProject.file("../VERSION").readText().trim()
val curCode = curName.split(".").map { it.toInt() }.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Release key: CI passes it through BIOKEY_* environment variables (GitHub secrets); locally it
// comes from ~/keystores/biokey/keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
val signingProps = Properties().apply {
    val f = File(System.getProperty("user.home"), "keystores/biokey/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: signingProps.getProperty(prop)

android {
    namespace = "org.pcbu.libreclient"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.pcbu.libreclient"
        minSdk = 26
        // Robolectric 4.13's bundled android-all jars top out at API 34; bump this
        // once a Robolectric release adds 35 support (compileSdk can stay ahead).
        targetSdk = 34
        versionCode = curCode
        versionName = curName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            signingValue("BIOKEY_KEYSTORE_FILE", "storeFile")?.let { storeFile = file(it) }
            storePassword = signingValue("BIOKEY_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("BIOKEY_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("BIOKEY_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // jvmTarget: AGP 9 built-in Kotlin derives it from compileOptions (17).

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Exposes the exported Room schema JSONs (room.schemaLocation below) to
    // MigrationTestHelper via Robolectric. Robolectric's isIncludeAndroidResources reads
    // from the main/debug merged assets output (not a separate "test" sourceSet merge, per
    // AGP's generated test_config.properties), so this must be on "main", not "test" —
    // it only adds a few tiny JSON files to the APK, not a real asset-bundling concern.
    sourceSets {
        getByName("main") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Foundation design system: real Liquid Glass engine (Kyant0/backdrop), same as the other apps.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")

    implementation("androidx.biometric:biometric:1.1.0")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-messaging")

    // F-Droid-friendly QR scanning (no ML Kit / Play Services dependency).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        isTransitive = true
    }

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // JUnit4-based (Robolectric requires the JUnit4 runner, not JUnit5 Jupiter).
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.8.4")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// Publishing: the repo's release workflow (.github/workflows/release.yml) builds, signs and
// attaches biokey.apk to the GitHub release; UpdateManager polls its /releases/latest.
