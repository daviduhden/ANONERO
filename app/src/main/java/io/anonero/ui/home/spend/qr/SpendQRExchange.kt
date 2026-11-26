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

package io.anonero.ui.home.spend.qr

import AnonNeroTheme
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparrowwallet.hummingbird.UR
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.AnonUrRegistryTypes
import io.anonero.model.Wallet
import io.anonero.model.WalletManager
import io.anonero.services.WalletState
import io.anonero.ui.components.AnimatedQrCode
import io.anonero.ui.home.graph.routes.ReviewTransactionRoute
import io.anonero.ui.home.spend.ReviewTransactionScreen
import io.anonero.ui.home.spend.isRegularNonEmptyExport
import io.anonero.ui.home.spend.prepareSpendExport
import io.anonero.ui.home.spend.readQrExport
import io.anonero.util.WalletProfile
import io.anonero.util.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Date
import java.util.Locale

@Serializable
data class SpendQRExchangeParam(
    val exportType: ExportType,
    val title: String,
    val ctaText: String,
)

enum class ExportType {
    OUTPUT,
    IMAGE,
    SIGNED_TX,
    UN_SIGNED_TX,
}

private const val TAG = "SpendQRExchange"

/** Scratch export tied to its originating wallet and profile. A null UR allows file export only. */
internal data class SpendQrArtifact(
    val file: File,
    val wallet: Wallet,
    val profile: WalletProfile,
    val ur: UR?,
)

class QRExchangeScreenViewModel : ViewModel() {
    internal suspend fun prepareExport(
        exportType: ExportType,
        wallet: Wallet,
        profile: WalletProfile,
    ): SpendQrArtifact? =
        withContext(Dispatchers.IO) {
            val cacheDir = AnonConfig.context?.cacheDir ?: return@withContext null

            fun isCurrent() = WalletManager.instance?.wallet === wallet && WalletSession.profile == profile
            if (!isCurrent()) return@withContext null
            val filename =
                when (exportType) {
                    ExportType.OUTPUT -> AnonConfig.EXPORT_OUTPUT_FILE
                    ExportType.IMAGE -> AnonConfig.EXPORT_KEY_IMAGE_FILE
                    ExportType.SIGNED_TX -> AnonConfig.EXPORT_SIGNED_TX_FILE
                    ExportType.UN_SIGNED_TX -> AnonConfig.EXPORT_UNSIGNED_TX_FILE
                }
            val file = File(cacheDir, filename)
            try {
                val registryType =
                    when (exportType) {
                        ExportType.OUTPUT -> {
                            if (!prepareSpendExport(file) { wallet.exportOutputs(it.absolutePath, true) }) return@withContext null
                            AnonUrRegistryTypes.XMR_OUTPUT
                        }

                        ExportType.IMAGE -> {
                            if (!prepareSpendExport(file) { wallet.exportKeyImages(it.absolutePath, true) }) return@withContext null
                            AnonUrRegistryTypes.XMR_KEY_IMAGE
                        }

                        ExportType.SIGNED_TX -> {
                            AnonUrRegistryTypes.XMR_TX_SIGNED
                        }

                        ExportType.UN_SIGNED_TX -> {
                            AnonUrRegistryTypes.XMR_TX_UNSIGNED
                        }
                    }
                if (!file.isRegularNonEmptyExport() || !isCurrent()) return@withContext null
                val ur = file.readQrExport()?.let { UR.fromBytes(registryType.type, it) }
                if (!file.isRegularNonEmptyExport() || !isCurrent()) return@withContext null
                SpendQrArtifact(file, wallet, profile, ur)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Timber.tag(TAG).w("Could not prepare spend export")
                null
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRExchangeScreen(
    params: SpendQRExchangeParam,
    onCtaCalled: () -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    onNavigateToHome: () -> Unit,
) {
    val viewModel = viewModel<QRExchangeScreenViewModel>()
    val walletState = koinInject<WalletState>()
    val profile by walletState.profile.collectAsStateWithLifecycle(initialValue = WalletSession.profile)
    val initialWallet = remember(params.exportType) { WalletManager.instance?.wallet }
    val initialProfile = remember(params.exportType) { WalletSession.profile }
    var prepared by remember(params.exportType) { mutableStateOf<SpendQrArtifact?>(null) }
    var loading by remember(params.exportType) { mutableStateOf(true) }
    val artifact = prepared?.takeIf { it.profile == profile && it.wallet === WalletManager.instance?.wallet }
    val ur = artifact?.ur
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val titleStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
        )

    LaunchedEffect(params.exportType) {
        prepared = initialWallet?.let { viewModel.prepareExport(params.exportType, it, initialProfile) }
        loading = false
    }

    // The picker can return after the wallet changes, so the callback rechecks the artifact owner.
    val saveExport =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            val export = artifact
            if (uri != null && export != null) {
                scope.launch(Dispatchers.IO) {
                    val saved =
                        try {
                            if (WalletManager.instance?.wallet !== export.wallet ||
                                WalletSession.profile != export.profile || !export.file.isRegularNonEmptyExport()
                            ) {
                                false
                            } else {
                                Files.newInputStream(export.file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                                    context.contentResolver.openOutputStream(uri)?.use { input.copyTo(it) } != null
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Timber.tag(TAG).w("Could not save spend export")
                            false
                        }
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                if (saved) {
                                    resources.getString(R.string.file_saved)
                                } else {
                                    resources.getString(R.string.could_not_save_file)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }

    BackHandler {
        onBackPressed()
    }

    ModalBottomSheet(
        scrimColor = MaterialTheme.colorScheme.background,
        contentColor = Color.Transparent,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        contentWindowInsets = {
            WindowInsets(
                left = 0.dp,
                top = 0.dp,
                right = 0.dp,
                bottom = 0.dp,
            )
        },
        shape = MaterialTheme.shapes.large,
        dragHandle = {
            Box(modifier = Modifier.height(0.dp))
        },
        onDismissRequest = {
            onBackPressed.invoke()
        },
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Text("")
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                onBackPressed.invoke()
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                    },
                )
            },
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(it)
                        .padding(bottom = 0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = params.title,
                            style = titleStyle,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                        )
                        if (ur != null) {
                            AnimatedQrCode(
                                Ur = ur,
                                modifier =
                                    Modifier
                                        .size(300.dp)
                                        .align(Alignment.CenterHorizontally)
                                        .padding(8.dp),
                                fps = 15,
                            )
                        } else if (loading) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (artifact == null) {
                            Text(stringResource(R.string.could_not_save_file))
                        }
                        TextButton(
                            enabled = artifact != null,
                            onClick = {
                                artifact?.file?.let {
                                    val sdf =
                                        SimpleDateFormat(
                                            "dd_MM_yyyy' 'HH_mm_a",
                                            Locale.getDefault(),
                                        )
                                    saveExport.launch("${sdf.format(Date())}_${it.name}")
                                }
                            },
                            modifier =
                                Modifier
                                    .padding(top = 8.dp)
                                    .align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.export_as_file))
                        }
                    }
                    if (params.ctaText.isNotBlank()) {
                        Button(
                            shape = MaterialTheme.shapes.small,
                            border =
                                BorderStroke(
                                    1.dp,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                ),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ),
                            contentPadding =
                                PaddingValues(
                                    all = 12.dp,
                                ),
                            modifier = Modifier.fillMaxWidth(0.85f),
                            enabled = artifact != null,
                            onClick = {
                                if (params.exportType == ExportType.SIGNED_TX) {
                                    onNavigateToHome()
                                } else {
                                    onCtaCalled.invoke()
                                }
                            },
                        ) {
                            Text(
                                params.ctaText,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color =
                                            MaterialTheme.colorScheme.onBackground.copy(
                                                alpha = 0.8f,
                                            ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRExchangeDialog(
    modifier: Modifier = Modifier,
    params: SpendQRExchangeParam?,
    show: Boolean,
    onDismiss: () -> Unit,
    onCtaCalled: () -> Unit,
    navigateToHome: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )

    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color.Transparent,
            sheetState = sheetState,
            scrimColor = MaterialTheme.colorScheme.background,
            contentColor = Color.Transparent,
            contentWindowInsets = {
                WindowInsets(
                    left = 0.dp,
                    top = 0.dp,
                    right = 0.dp,
                    bottom = 0.dp,
                )
            },
            shape = MaterialTheme.shapes.large,
            dragHandle = {
                Box(modifier = Modifier.height(0.dp))
            },
            properties =
                ModalBottomSheetProperties(
                    shouldDismissOnBackPress = true,
                ),
        ) {
            Box(Modifier.padding(0.dp)) {
                if (params != null) {
                    QRExchangeScreen(
                        params = params,
                        onBackPressed = onDismiss,
                        onCtaCalled = onCtaCalled,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToHome = {
                            navigateToHome.invoke()
                        },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ReviewTransactionScreenPreview() {
    AnonNeroTheme {
        ReviewTransactionScreen(
            ReviewTransactionRoute("address"),
        )
    }
}
