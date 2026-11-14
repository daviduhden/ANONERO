/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.anonero.di

import android.content.Context
import android.content.SharedPreferences
import io.anonero.AnonConfig
import io.anonero.network.TransportController
import io.anonero.services.AnonWalletHandler
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.store.LogRepository
import io.anonero.store.NodesRepository
import io.anonero.ui.home.SendViewModel
import io.anonero.ui.home.settings.LogViewModel
import io.anonero.ui.home.settings.NetworkSettingsViewModel
import io.anonero.ui.home.settings.NodeSettingsViewModel
import io.anonero.ui.home.settings.ProxySettingsViewModel
import io.anonero.ui.home.settings.SecureWipeViewModel
import io.anonero.ui.onboard.OnboardViewModel
import io.anonero.ui.viewmodels.AppViewModel
import io.anonero.util.WALLET_PREFERENCES
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Provides the single named preference file used by the wallet layer. */
fun provideWalletSharedPrefs(app: Context): SharedPreferences =
    // The Koin qualifier is WALLET_PREFERENCES; the actual file name is AnonConfig.PREFS.
    app.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)

/**
 * Koin graph for process-scoped services and screen-scoped ViewModels.
 *
 * `WalletState`, `AnonWalletHandler`, and `TorService` are singletons because they mirror
 * the single managed wallet and the single embedded Tor runtime. ViewModels are declared
 * with `viewModel` so they survive configuration changes. `WALLET_PREFERENCES` is the Koin
 * qualifier for the shared preference file; do not create a second preference file for
 * those values.
 */
val appModule =
    module {
        single(named(WALLET_PREFERENCES)) { provideWalletSharedPrefs(androidApplication()) }
        single { TorService(get(named(WALLET_PREFERENCES))) }
        single { TransportController(androidApplication(), get(named(WALLET_PREFERENCES)), get()) }
        single { WalletState() }
        single {
            AnonWalletHandler(
                get(named(WALLET_PREFERENCES)),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single { LogRepository(get()) }
        single { NodesRepository(get()) }
        viewModel { AppViewModel(get(), get(), get()) }
        viewModel { OnboardViewModel(get(named(WALLET_PREFERENCES))) }
        viewModel { LogViewModel(get()) }
        viewModel { SendViewModel() }
        viewModel { SecureWipeViewModel(get(), get(named(WALLET_PREFERENCES)), get(), get()) }
        viewModel { NodeSettingsViewModel(get(named(WALLET_PREFERENCES)), get<NodesRepository>(), get()) }
        viewModel { ProxySettingsViewModel(get(), get(), get(named(WALLET_PREFERENCES))) }
        viewModel {
            NetworkSettingsViewModel(
                get(named(WALLET_PREFERENCES)),
                get(),
                get(),
                get(),
                get(),
            )
        }
    }
