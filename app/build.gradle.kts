import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.billiardtracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.billiardtracker"
        minSdk = 28
        targetSdk = 36
        versionCode = 136
        versionName = "1.29.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Dev telemetry: собирать sync/repo/http/nav/lifecycle события и слать на
        // /api/dev-log. Включено пока идёт активная разработка; после стабилизации
        // выключить (или превратить в runtime-тумблер в Настройках).
        buildConfigField("boolean", "ENABLE_DEV_LOG", "true")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("BT_KEYSTORE_PATH")
                ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let { propsFile ->
                    val props = Properties()
                    propsFile.inputStream().use { props.load(it) }
                    props.getProperty("storeFile")
                }
            val keyAliasEnv = System.getenv("BT_KEY_ALIAS") ?: "billiardtracker"
            val storePasswordEnv = System.getenv("BT_STORE_PASSWORD")
            val keyPasswordEnv = System.getenv("BT_KEY_PASSWORD")

            if (storeFilePath != null && storePasswordEnv != null && keyPasswordEnv != null) {
                storeFile = file(storeFilePath)
                keyAlias = keyAliasEnv
                storePassword = storePasswordEnv
                keyPassword = keyPasswordEnv
            }
            // If env not set, signing config remains empty — release build will error clearly,
            // which is fine because unsigned release is not deployable anyway.
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
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Navigation
    implementation(libs.androidx.nav.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Location
    implementation(libs.play.services.location)

    // WorkManager — background sync для offline-first очереди операций.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // Test-only: parse shared/rule-profiles-expected.json for parity assertions.
    // Runtime library only (no @Serializable), so no kotlin-serialization compiler plugin needed here.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
