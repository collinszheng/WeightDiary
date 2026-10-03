// 顶层构建文件。AGP 9 自带 Kotlin 支持，不需要再显式应用 kotlin-android 插件。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
