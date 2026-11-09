/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
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

package io.anonero.ui.onboard

import AnonNeroTheme
import AnonOutlineButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.window.Popup
import io.anonero.R
import kotlin.math.roundToInt


data class RestorePayload(
    val seed: List<String>,
    val restoreHeight: Long?,
)

@Composable
fun RestoreWallet(
    onBackPressed: () -> Unit = {},
    oNextPressed: (RestorePayload) -> Unit = {},
    onboardViewModel: OnboardViewModel?,
) {

    var restoreHeight by remember {
        mutableStateOf(onboardViewModel?.getRestorePayload()?.restoreHeight?.toString() ?: "")
    }
    var restoreHeightError by remember {
        mutableStateOf<Int?>(
            null
        )
    }
     var seed by remember {
        mutableStateOf(
            onboardViewModel?.getRestorePayload()?.seed?.fastJoinToString (
                separator = " "
            ) ?: ""
        )
    }
    val seedList = remember(seed) { SeedRestoreInput.words(seed) }
    val invalidSeed = seed.isNotBlank() && !SeedRestoreInput.hasSupportedCount(seedList)
    val focusManager = LocalFocusManager.current

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    modifier = Modifier
                        .size(24.dp),
                    onClick = {
                        onBackPressed()
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_anon),
                    contentDescription = stringResource(R.string.app_logo),
                    modifier = Modifier
                        .size(120.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.import_polyseed),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier
                    .weight(.8f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.enter_seed),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    supportingContent = {
                        OutlinedTextField(
                            value = seed,
                            shape = MaterialTheme.shapes.medium,
                            onValueChange = {
                                seed = it
                            },
                            isError = invalidSeed,
                            supportingText = {
                                if (invalidSeed) {
                                    Text(stringResource(R.string.invalid_seed), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            maxLines = 5,
                            minLines = 5,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Text,
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.restore_height),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    supportingContent = {
                        OutlinedTextField(
                            value = restoreHeight,
                            shape = MaterialTheme.shapes.medium,
                            onValueChange = {
                                restoreHeight = it
                                restoreHeightError = null
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                keyboardType = KeyboardType.Number
                            ),
                            isError = restoreHeightError != null,
                            supportingText = {
                                if (restoreHeightError != null)
                                    Text(
                                        stringResource(restoreHeightError!!),
                                        color = MaterialTheme.colorScheme.error
                                    )

                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )
            }
            val enabled = SeedRestoreInput.hasSupportedCount(seedList)
            AnonOutlineButton (
                onClick = {
                    if (enabled) {
                         if (seedList.size == 25 && restoreHeight.isBlank()) {
                            restoreHeightError = R.string.restore_height_required
                            return@AnonOutlineButton
                        }
                        val heightText = restoreHeight.trim()
                        val height = heightText.toLongOrNull()
                        if (!SeedRestoreInput.isValidHeightInput(heightText)) {
                            restoreHeightError = R.string.invalid_restore_height
                            return@AnonOutlineButton
                        }
                        oNextPressed(
                            RestorePayload(
                                seed = seedList,
                                restoreHeight = height,
                            )
                        )
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp
                    ),

            ) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun SetupNodeComposablePreview() {
    AnonNeroTheme {
        RestoreWallet(
            onBackPressed = {},
            oNextPressed = { },
            onboardViewModel = null
        )
    }
}


@Composable
fun CustomAutocompleteTextField(
    query: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    label: String = "",
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit
) {
    var isPopupExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val textFieldCoordinates =
        remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val localDensity = LocalDensity.current

    Box(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChanged(it)
                isPopupExpanded = true
            },
            label = { if (label.isNotEmpty()) Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { layoutCoordinates ->
                    textFieldCoordinates.value = layoutCoordinates.localToWindow(Offset.Zero)
                }
                .focusRequester(focusRequester),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    query.takeIf { it.isNotEmpty() }?.let { onSuggestionSelected("") }
                }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                isPopupExpanded = false
            })
        )

        if (isPopupExpanded && suggestions.isNotEmpty() && textFieldCoordinates.value != null) {
            val position = textFieldCoordinates.value!!
            Popup(
                offset = IntOffset(
                    x = position.x.roundToInt(),
                    y = (position.y + with(localDensity) { /* field height */ 56.dp.toPx() }).roundToInt()
                )
            ) {
                Surface(
                    modifier = Modifier
                        .width(300.dp)
                ) {
                    LazyColumn {
                        items(suggestions.size) { index ->
                            val suggestion = suggestions[index]
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = suggestion,
                                    )
                                },
                                onClick = {
                                    onSuggestionSelected(suggestion)
                                    isPopupExpanded = false
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
