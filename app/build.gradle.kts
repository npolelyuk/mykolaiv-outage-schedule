plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.lightmykolaiv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.lightmykolaiv"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.7.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystoreFile = if (!keystorePath.isNullOrBlank()) {
                file(keystorePath)
            } else {
                rootProject.file("keystore/mykolaiv-outage-schedule")
            }

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && !releaseSigning.storePassword.isNullOrBlank()) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0") // Для работы с Gemini
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Для чтения JSON
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // Для связи UI и логики
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
}