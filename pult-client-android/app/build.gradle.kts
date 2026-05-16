import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.pult"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.pult"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", localProperties.getProperty("PULT_API_KEY") ?: "\"\"")
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Retrofit for HTTP queries
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    // JSON to object
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    // OkHttp for Interceptor
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // bg jobs
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // qr codes
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
