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

package io.anonero.ui.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import io.anonero.R
import io.anonero.services.TorService
import kotlinx.coroutines.flow.runningFold
import org.koin.androidx.compose.koinViewModel

/** Logs only: transport configuration belongs to NetworkSettingsViewModel. */
class ProxySettingsViewModel(
    torService: TorService,
) : ViewModel() {
    val logs: LiveData<List<String>> =
        torService.torLogs
            .runningFold<String, List<String>>(emptyList()) { accumulator, value ->
                (accumulator + value).takeLast(100)
            }.asLiveData()
}

/** Legacy routes share the same explicit transport editor as Network & Nodes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettings(onBackPress: () -> Unit = {}) {
    var showLogs by remember { mutableStateOf(false) }
    NetworkSettings(
        onBackPress = onBackPress,
        onManageNodes = {},
        onManageLws = {},
        transportOnly = true,
        onShowTorLogs = { showLogs = true },
    )
    if (showLogs) {
        ModalBottomSheet(
            onDismissRequest = { showLogs = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            TorLogs()
        }
    }
}

@Composable
fun TorLogs() {
    val listState = rememberLazyListState()
    val proxyViewModel = koinViewModel<ProxySettingsViewModel>()
    val logs by proxyViewModel.logs.observeAsState(initial = emptyList())

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .padding(
                    horizontal = 8.dp,
                ).navigationBarsPadding()
                .fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        item {
            Text(
                stringResource(id = R.string.tor_logs),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 12.dp,
                        ),
            )
        }
        items(logs.size) { index ->
            Text(
                logs[index],
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                maxLines = 30,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 4.dp,
                        ).background(Color.Transparent),
            )
        }
    }
}
