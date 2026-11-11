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

import AnonNeroTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.anonero.R
import io.anonero.util.LwsConfig
import io.anonero.util.LwsValidationError
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WALLET_USE_TOR
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LwsSettingsScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit = {},
) {
    val prefs = koinInject<android.content.SharedPreferences>(named(WALLET_PREFERENCES))
    val loaded = remember { LwsConfig.load(prefs) }
    var enabled by remember { mutableStateOf(loaded.enabled) }
    var host by remember { mutableStateOf(loaded.host) }
    var portText by remember { mutableStateOf(loaded.port.toString()) }
    var error by remember { mutableStateOf<LwsValidationError?>(null) }
    var requiresTor by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val useTor = prefs.getBoolean(WALLET_USE_TOR, true)

    BackHandler { onBackPress() }

    fun save() {
        error = null
        requiresTor = false
        saved = false
        val port = portText.toIntOrNull() ?: LwsConfig.DEFAULT_PORT
        if (enabled) {
            val validation = LwsConfig.validate(host, port)
            if (validation != null) {
                error = validation
                return
            }
        }
        if (enabled && !prefs.getBoolean(WALLET_USE_TOR, true)) {
            requiresTor = true
            return
        }
        LwsConfig.save(prefs, LwsConfig(enabled, host.trim(), port))
        saved = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lws_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.use_lws)) },
                    supportingContent = {
                        Text(stringResource(R.string.lws_supporting))
                    },
                    trailingContent = {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(stringResource(R.string.lws_host)) },
                    placeholder = { Text("lws.example.onion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.lws_port)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Number,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(R.string.lws_privacy_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    if (useTor) {
                        stringResource(R.string.lws_tor_enabled)
                    } else {
                        stringResource(R.string.lws_tor_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (useTor) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
            item {
                Text(
                    stringResource(R.string.lws_restart_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { validationError ->
                item {
                    Text(
                        stringResource(validationError.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (requiresTor) {
                item {
                    Text(
                        stringResource(R.string.lws_requires_tor),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (saved) {
                item {
                    Text(
                        stringResource(R.string.lws_saved_restart),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun LwsValidationError.messageRes(): Int =
    when (this) {
        LwsValidationError.HOST_EMPTY -> R.string.lws_error_host_empty
        LwsValidationError.HOST_TOO_LONG -> R.string.lws_error_host_too_long
        LwsValidationError.HOST_HAS_SCHEME_OR_PORT -> R.string.lws_error_host_format
        LwsValidationError.HOST_INVALID -> R.string.lws_error_host_invalid
        LwsValidationError.PORT_RANGE -> R.string.lws_error_port_range
    }

@Preview(device = "id:pixel_5")
@Composable
private fun LwsSettingsPreview() {
    AnonNeroTheme { LwsSettingsScreen() }
}
