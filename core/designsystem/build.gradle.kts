import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val appDisplayName: String = Properties().run {
    rootProject.file("branding.properties").reader(Charsets.UTF_8).use { load(it) }
    getProperty("appDisplayName")?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("branding.properties: appDisplayName fehlt")
}

android {
    namespace = "com.ironlog.core.designsystem"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        resValue("string", "app_name", appDisplayName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.haze)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}
