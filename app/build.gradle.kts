plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
   alias(libs.plugins.compose.compiler)
}

android {
    namespace = "bigtwo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "bigtwo.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 添加测试选项以解决找不到类的问题
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // 配置 Kotlin 源码目录，确保正确识别当前结构
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    //添加 Jetpack Compose 选项
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    //添加Jetpack Compose 依赖
    // 1. Compose BOM：统一管理各个 Compose 库的版本
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // 2. UI 核心模块 —— 这里选择 Material3（最新 Material 设计规范）
    implementation("androidx.compose.material3:material3")

    // 3. Foundation：基础布局、手势等（Material3 已依赖 Foundation，可按需加深）
    implementation("androidx.compose.foundation:foundation")

    // 4. Core UI：输入、布局测量等最基础功能（可选）
    implementation("androidx.compose.ui:ui")

    // 5. Android Studio Preview 支持
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 6. Material Icons（可选，如需图标库）
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // 7. Compose UI 测试（Instrumented/UI 测试）
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 8. Activity 与 ViewModel 集成
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // 9. LiveData & RxJava 运行时 (可选，根据项目需求添加)
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.runtime:runtime-rxjava2")

}