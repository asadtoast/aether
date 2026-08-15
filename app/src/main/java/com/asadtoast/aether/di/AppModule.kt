package com.asadtoast.aether.di

import com.asadtoast.aether.data.BackupManager
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.data.WikiRepository
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.HydrusViewModel
import com.asadtoast.aether.viewmodel.WikiViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // singletons
    single {
        val settingsManager = get<SettingsManager>()
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val apiKey = runBlocking {
                    settingsManager.apiKeyFlow.first()
                }
                val request = chain.request().newBuilder()
                    .addHeader("Hydrus-Client-API-Access-Key", apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    single { SettingsManager(androidContext()) }
    single { HydrusRepository(get()) }
    single { WikiRepository(androidContext()) }
    single { BackupManager(androidContext()) }

    // viewmodels
    viewModel { HydrusViewModel(get(), get(), get()) }
    viewModel { DuplicateViewModel(get(), get()) }
    viewModel { WikiViewModel(get(), get(), get(), get()) }
}