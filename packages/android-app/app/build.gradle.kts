plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String = localProperties.getProperty(name, "")

fun escapeBuildConfigString(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.yepanywhere.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yepanywhere.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "YEP_RELAY_AUTH_MODE", "\"relay\"")
        buildConfigField("String", "YEP_RELAY_URL", "\"wss://relay.yepanywhere.local\"")
        buildConfigField("String", "YEP_RELAY_USERNAME", "\"\"")
        buildConfigField(
            "String",
            "TEST_RELAY_URL",
            "\"${escapeBuildConfigString(localProperty("test.relay.url"))}\"",
        )
        buildConfigField(
            "String",
            "TEST_RELAY_IDENTITY",
            "\"${escapeBuildConfigString(localProperty("test.relay.identity"))}\"",
        )
        buildConfigField(
            "String",
            "TEST_RELAY_PASSWORD",
            "\"${escapeBuildConfigString(localProperty("test.relay.password"))}\"",
        )
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared-core"))
    implementation(project(":shared-ui"))
    implementation(project(":android-data"))

    implementation(libs.androidx.activity.compose)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.tweetnacl.java)

    debugImplementation(compose.uiTooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}




