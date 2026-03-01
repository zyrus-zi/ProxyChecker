plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.proxychecker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.proxychecker"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            // Включает сжатие кода и удаление неиспользуемых частей библиотек
            isMinifyEnabled = true
            // Удаляет неиспользуемые ресурсы (картинки, xml), которые не вызываются в коде
            isShrinkResources = true
            // Оптимизация кода
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Корутины
    implementation(libs.kotlinx.coroutines.android)

    // Сеть (OkHttp) для проверки HTTP/SOCKS5
    implementation(libs.okhttp)

    // Парсинг Telegram каналов
    implementation(libs.jsoup)
}