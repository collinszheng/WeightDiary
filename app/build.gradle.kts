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

/**
 * 版本号。后缀走 Gradle 参数，**不写死** —— 同一份源码既能出测试版也能出正式版：
 *
 * ```bash
 * ./gradlew distRelease -PversionSuffix=-beta   # → 1.1-beta，产物 WeightDiary-1.1-beta.apk
 * ./gradlew distRelease                         # → 1.1
 * ```
 *
 * 产物名是从 [appVersionName] 拼出来的（见文件末尾的 `distRelease`），所以文件名会自带后缀，
 * 测试版不会覆盖掉正式版那个 `WeightDiary-1.1.apk`。
 *
 * ⚠️ **`versionCode` 的排序规则：测试版必须低于它之后的正式版。**
 * Android 的包管理器只拦降级 —— 装更低的 code 会被拒（`INSTALL_FAILED_VERSION_DOWNGRADE`），
 * 而唯一的绕法是先卸载，**卸载会清掉用户数据**。对一个以数据安全为核心的项目，
 * 这是最不能接受的失败方式。所以：
 *
 * ```
 * 1.0        code 1   （已发布）
 * 1.1-beta   code 2   （已发布为 pre-release）
 * 1.1 正式   code 3   ← 当前
 * 1.2-beta   code 4   ← 下一轮从这里继续
 * ```
 *
 * code 相等是允许的（签名一致即可），只是没有「更新」信号；而本项目走 GitHub Releases
 * 手动下载、本来就没有自动更新机制，所以这一条影响很小。
 */
val versionSuffix: String = providers.gradleProperty("versionSuffix").orNull.orEmpty()
val appVersionName = "1.1" + versionSuffix
val appVersionCode = 3

android {
    namespace = "com.weightdiary.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.weightdiary.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // androidTest 只有一个迁移测试（见 src/androidTest）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // androidTest 目前只有一个 Room 迁移测试。
    // 刻意**不引入** androidx.room:room-testing：它的 MigrationTestHelper 会与
    // lifecycle 带的 kotlinx-serialization-core:1.7.3 冲突（AGP consistent resolution
    // 会把 app 侧版本作为 strict 约束复制过来），而修它要抬生产的依赖版本。
    // 迁移测试改成「裸建 v1 库 + 用生产配置打开」，见 src/androidTest。
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
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