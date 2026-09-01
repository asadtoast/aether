package com.asadtoast.aether.di

import com.asadtoast.aether.data.BackupManager
import com.asadtoast.aether.data.HydrusRepository
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.data.WikiRepository
import com.asadtoast.aether.viewmodel.DuplicateViewModel
import com.asadtoast.aether.viewmodel.GroupDuplicateViewModel
import com.asadtoast.aether.viewmodel.AetherViewModel
import com.asadtoast.aether.viewmodel.WikiViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // singletons
    single { SettingsManager(androidContext()) }
    single { HydrusRepository(get()) }
    single { WikiRepository(androidContext()) }
    single { BackupManager(androidContext()) }

    // viewmodels
    viewModel { AetherViewModel(get(), get(), get()) }
    viewModel { DuplicateViewModel(get(), get()) }
    viewModel { GroupDuplicateViewModel(get(), get()) }
    viewModel { WikiViewModel(get(), get(), get(), get()) }
}