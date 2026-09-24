plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ironlog.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Readiness contracts (readiness + readinessdata) are platform-neutral Kotlin
    // types from :shared. They are part of this module's public repository API,
    // so they are exposed with `api` instead of being hidden behind `implementation`.
    api(project(":shared"))
    api(libs.kotlinx.datetime)

    implementation(libs.core.ktx)
    implementation(libs.paging.common)
}
