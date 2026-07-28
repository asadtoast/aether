plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.asadtoast.aether"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.com.asadtoast.com.asadtoast.aether"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    // moshi for hydrus to kotli translation
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")

    // coil for image ande GIFs
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // okhttp3 for loggin network errors
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    //  datastore to configure stuff
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // icons
    implementation("androidx.compose.material:material-icons-extended")

    // media3 video player engine
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.0")

    // auto backup
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Koin para Inyección de Dependencias
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")
    implementation("io.insert-koin:koin-androidx-workmanager:3.5.3")

    // paging3
    implementation("androidx.paging:paging-compose:3.2.1")
}
