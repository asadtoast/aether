plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.asadtoast.aether"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.com.asadtoast.com.asadtoast.aether"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "3.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // retrofit for hydrus api
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)

    // moshi for hydrus to kotli translation
    implementation(libs.moshi.kotlin)

    // coil for image ande GIFs
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // okhttp3 for loggin network errors
    implementation(libs.logging.interceptor)

    //  datastore to configure stuff
    implementation(libs.androidx.datastore.preferences)

    // viewmodel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // icons
    implementation(libs.androidx.compose.material.icons.extended)

    // media3 video player engine
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // auto backup
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.navigation.compose)

    // Koin para Inyección de Dependencias
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    // paging3
    implementation(libs.androidx.paging.compose)
}
