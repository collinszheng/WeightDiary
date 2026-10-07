import java.util.Properties

// 签名口令放在项目根的 keystore.properties（已 gitignore）。
// 文件不在时不做签名 —— 这样别人 clone 下来照样能跑 assembleDebug，只是出不了正式包。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val appVersionName = "1.1"

android {
    namespace = "com.weightdiary.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.weightdiary.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8：混淆 + 删无用代码
            isMinifyEnabled = true
            // 资源压缩，配合 minify 一起用才有意义
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // DebugSeed 用 BuildConfig.DEBUG 做守卫
        buildConfig = true
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // 导出 Room schema，用于后续 Migration 校验
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.health.connect.client)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
/**
 * 把正式包拷到项目根的 dist/，并起一个纯英文的名字。
 *
 * 构建本身的产物已经是 app-release.apk（本来就是英文），这里只是给交付物一个带版本号的名字。
 * 中文文件名在 Windows / adb / 手机文件管理器之间转手会踩编码坑，所以产物一律用英文名。
 */
tasks.register<Copy>("distRelease") {
    group = "distribution"
    description = "把签名后的正式包拷到 dist/WeightDiary-<版本>.apk"
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    // 用字符串重载而不是 rename { } 闭包：闭包会捕获 Gradle 脚本对象，
    // 配置缓存无法序列化，构建会直接失败
    rename("app-release\\.apk", "WeightDiary-$appVersionName.apk")
}