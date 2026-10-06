plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself; these pin the Kotlin compiler plugins to the catalog's Kotlin version
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.wrapper {
    gradleVersion = "9.8.0"
}
