plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ✅ 使用 JUnit 5，去掉 JUnit 4
//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
//    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")

    // 🚫 不推荐混用 JUnit 4，除非你有兼容需求
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(kotlin("test"))

}

//// ✅ 启用 JUnit Platform
//tasks.withType<Test>().configureEach {
//    useJUnitPlatform()
//}