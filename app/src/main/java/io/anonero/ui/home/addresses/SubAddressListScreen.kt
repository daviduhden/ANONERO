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

package io.anonero.ui.home.addresses

import AnonNeroTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.anonero.model.Subaddress
import io.anonero.services.WalletState
import io.anonero.util.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject


class SubAddressListViewModel : ViewModel() {

    private val walletState: WalletState by inject(WalletState::class.java)

    val subAddresses = walletState.subAddresses.asLiveData()
    val hideAmounts = walletState.hideAmountsFlow.asLiveData()
    val offline = walletState.offlineFlow.asLiveData()


    fun getNextAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            walletState.getNewAddress()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SubAddressesScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
    navigateToDetails: (Subaddress) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    BackHandler {
        onBackPress()
    }
    val vm = viewModel<SubAddressListViewModel>()
    val addresses by vm.subAddresses.observeAsState(listOf())
    val hideAmounts by vm.hideAmounts.observeAsState(false)
    val offline by vm.offline.observeAsState(false)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBackPress
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {},
                actions = {
                    IconButton(
                        onClick = {
                            vm.getNextAddress()
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(addresses.size) {
                val address = addresses[it]
                with(sharedTransitionScope) {
                    ListItem(
                        modifier = Modifier
                            .sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "${address.address}:${address.addressIndex}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                            .padding(
                                horizontal = 4.dp,
                                vertical = 6.dp
                            )

                            .clickable {
                                navigateToDetails(address)
                            },
                        headlineContent = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    address.displayLabel, color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    if (hideAmounts || offline) Formats.maskAmount(address.totalAmount)
                                    else Formats.getDisplayAmount(address.totalAmount),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        supportingContent = {
                            SelectionContainer {
                                Text(
                                    address.squashedAddress,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Justify
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Preview(device = "id:pixel_7_pro")
@Composable
private fun SubAddressScreenPrev() {
    AnonNeroTheme {

    }
}
