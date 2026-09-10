import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}

val lifeTraceSigningProperties = Properties()
val lifeTraceSigningFile = rootProject.file(".private/signing/signing.properties")
if (lifeTraceSigningFile.isFile) {
    lifeTraceSigningFile.inputStream().use { input -> lifeTraceSigningProperties.load(input) }
}

android {
    namespace = "com.lifetrace.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifetrace.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://trace.wmy-cloud.cn/update.json\"")
    }

    signingConfigs {
        if (lifeTraceSigningFile.isFile) {
            create("lifetrace") {
                storeFile = rootProject.file(lifeTraceSigningProperties.getProperty("storeFile"))
                storePassword = lifeTraceSigningProperties.getProperty("storePassword")
                keyAlias = lifeTraceSigningProperties.getProperty("keyAlias")
                keyPassword = lifeTraceSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("lifetrace")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("lifetrace")?.let { signingConfig = it }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.maplibre.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
