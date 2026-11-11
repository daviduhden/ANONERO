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

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparrowwallet.hummingbird.ResultType
import com.sparrowwallet.hummingbird.URDecoder
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.model.AnonUrRegistryTypes
import io.anonero.model.UnsignedTransaction
import io.anonero.model.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private const val TAG = "QRScanner"

enum class ImportEvents {
    IMPORT_OUTPUTS,
    IMPORT_KEY_IMAGES,
    IMPORT_UNSIGNED_TX,
    IMPORT_SIGNED_TX,
}

class QRScannerVM : ViewModel() {
    suspend fun processUrCode(urResult: URDecoder.Result): Result<ImportEvents> {
        if (urResult.type == ResultType.SUCCESS) {
            var destinationFile: File? = null
            val ur = urResult.ur
            when (ur.type) {
                AnonUrRegistryTypes.XMR_TX_UNSIGNED.type -> {
                    destinationFile =
                        File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_UNSIGNED_TX_FILE)
                }

                AnonUrRegistryTypes.XMR_TX_SIGNED.type -> {
                    destinationFile =
                        File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_SIGNED_TX_FILE)
                }

                AnonUrRegistryTypes.XMR_OUTPUT.type -> {
                    destinationFile =
                        File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_OUTPUT_FILE)
                }

                AnonUrRegistryTypes.XMR_KEY_IMAGE.type -> {
                    destinationFile =
                        File(AnonConfig.context?.cacheDir, AnonConfig.IMPORT_KEY_IMAGE_FILE)
                }

                else -> {
                    Timber.tag(TAG).d("Unknown UR type")
                }
            }
            destinationFile?.writeBytes(ur.toBytes())
            if (destinationFile != null) {
                val wallet = WalletManager.instance?.wallet
                if (wallet == null) {
                    Timber.tag(TAG).e("wallet is null")
                    return Result.failure(
                        Exception(AnonConfig.context?.getString(R.string.wallet_not_open)),
                    )
                }
                val filePath = destinationFile.path
                when (AnonUrRegistryTypes.fromUrTag(ur)) {
                    AnonUrRegistryTypes.XMR_OUTPUT -> {
                        _currentImportState.postValue(AnonUrRegistryTypes.XMR_OUTPUT)
                        try {
                            _loaderState.postValue(true)
                            val status = wallet.importOutputs(filePath)
                            _loaderState.postValue(false)
                            if (status?.lowercase() == "imported") {
                                Timber.tag(TAG).i("Imported outputs")
                                // save the imported outputs
                                wallet.store()
                                return Result.success(ImportEvents.IMPORT_OUTPUTS)
                            } else {
                                Timber.tag(TAG).e("Failed to import outputs: %s", status)
                                return Result.failure(
                                    Exception(
                                        AnonConfig.context?.getString(R.string.import_outputs_failed),
                                    ),
                                )
                            }
                        } catch (error: Exception) {
                            return Result.failure(error)
                        } finally {
                            _currentImportState.postValue(null)
                        }
                    }

                    AnonUrRegistryTypes.XMR_KEY_IMAGE -> {
                        _loaderState.postValue(true)
                        _currentImportState.postValue(AnonUrRegistryTypes.XMR_KEY_IMAGE)
                        try {
                            val status = wallet.importKeyImages(filePath)
                            delay(1_520)
                            Timber.tag(TAG).i("Import key images status $status")
                            if (status?.lowercase() == "imported") {
                                return Result.success(ImportEvents.IMPORT_KEY_IMAGES)
                            } else {
                                Timber.tag(TAG).e("Failed to import key images: %s", status)
                                return Result.failure(
                                    Exception(
                                        AnonConfig.context?.getString(R.string.import_key_images_failed),
                                    ),
                                )
                            }
                        } finally {
                            _loaderState.postValue(false)
                            _currentImportState.postValue(null)
                        }
                    }

                    AnonUrRegistryTypes.XMR_TX_UNSIGNED -> {
                        _currentImportState.postValue(AnonUrRegistryTypes.XMR_TX_UNSIGNED)
                        _loaderState.postValue(true)
                        try {
                            val unsignedTransaction =
                                wallet.loadUnsignedTransaction(filePath)
                            if (unsignedTransaction != null && unsignedTransaction.status == UnsignedTransaction.Status.Status_Ok) {
                                // reject a set that is not exactly one tx
                                val count = unsignedTransaction.txCount
                                if (count != 1L) {
                                    Timber.tag(TAG).e("Unsigned transaction set has %d transactions", count)
                                    return Result.failure(
                                        Exception(
                                            AnonConfig.context?.getString(
                                                R.string.unsigned_tx_count_invalid,
                                                count,
                                            ),
                                        ),
                                    )
                                }
                                return Result.success(ImportEvents.IMPORT_UNSIGNED_TX)
                            } else {
                                Timber.tag(TAG).e("Failed to import unsigned transaction")
                                return Result.failure(
                                    Exception(
                                        AnonConfig.context?.getString(R.string.import_unsigned_failed),
                                    ),
                                )
                            }
                        } finally {
                            _loaderState.postValue(false)
                            _currentImportState.postValue(null)
                        }
                    }

                    AnonUrRegistryTypes.XMR_TX_SIGNED -> {
                        _currentImportState.postValue(null)
                        return Result.success(ImportEvents.IMPORT_SIGNED_TX)
                    }

                    null -> {
                        Timber.tag(TAG).e("Unknown UR type")
                        return Result.failure(
                            Exception(AnonConfig.context?.getString(R.string.invalid_qr_code)),
                        )
                    }
                }
            }
        }
        return Result.failure(
            Exception(AnonConfig.context?.getString(R.string.invalid_qr_code)),
        )
    }

    var _currentImportState: MutableLiveData<AnonUrRegistryTypes?> = MutableLiveData(null)
    var _loaderState: MutableLiveData<Boolean> = MutableLiveData(false)
    val loaderState: LiveData<Boolean> = _loaderState

    @OptIn(ExperimentalCoroutinesApi::class)
    fun reset() {
        _currentImportState.postValue(null)
        _loaderState.postValue(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun URQRScanner(
    showScanner: Boolean = false,
    onQRCodeScanned: (String) -> Unit,
    onURResult: (Result<ImportEvents>) -> Unit,
    onDismiss: () -> Unit = {},
) {
    val qrScannerVM = viewModel<QRScannerVM>()
    val loaderState by qrScannerVM.loaderState.observeAsState(false)
    var detectedUR by remember { mutableStateOf<String?>(null) }
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

    LaunchedEffect(showScanner) {
        if (showScanner) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }
    if (showScanner) {
        ModalBottomSheet(
            scrimColor = MaterialTheme.colorScheme.background,
            contentColor = Color.Transparent,
            sheetState = sheetState,
            contentWindowInsets = {
                WindowInsets(
                    left = 0.dp,
                    top = 0.dp,
                    right = 0.dp,
                    bottom = 0.dp,
                )
            },
            properties =
                ModalBottomSheetProperties(
                    shouldDismissOnBackPress = loaderState == false,
                ),
            shape = MaterialTheme.shapes.large,
            dragHandle = {
                Box(modifier = Modifier.height(0.dp))
            },
            onDismissRequest = {
                onDismiss.invoke()
                if (loaderState == false) {
                    onDismiss.invoke()
                    qrScannerVM.reset()
                }
            },
        ) {
            if (loaderState == false) {
                io.anonero.ui.components.scanner.QRScanner(
                    onDismiss = {
                        onDismiss.invoke()
                        qrScannerVM.reset()
                    },
                    onQRCodeScanned = {
                        onQRCodeScanned.invoke(it)
                        qrScannerVM.reset()
                    },
                    onUrRusult = {
                        detectedUR = it.ur.type
                        qrScannerVM.viewModelScope.launch(Dispatchers.IO) {
                            val result = qrScannerVM.processUrCode(it)
                            withContext(Dispatchers.Main) {
                                onURResult.invoke(result)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val message =
                    when (detectedUR) {
                        AnonUrRegistryTypes.XMR_OUTPUT.type -> stringResource(R.string.importing_outputs)
                        AnonUrRegistryTypes.XMR_KEY_IMAGE.type -> stringResource(R.string.importing_key_images)
                        AnonUrRegistryTypes.XMR_TX_SIGNED.type -> stringResource(R.string.importing_signed_transaction)
                        AnonUrRegistryTypes.XMR_TX_UNSIGNED.type -> stringResource(R.string.importing_unsigned_transaction)
                        else -> ""
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(220.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            message,
                            textAlign = TextAlign.Center,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                ),
                        )
                    }
                }
            }
        }
    }
}
