package io.anonero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.UREncoder
import io.anonero.ui.components.qr.QrCodeColors
import io.anonero.ui.components.qr.QrCodeView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun AnimatedQrCode(
    modifier: Modifier = Modifier, Ur: UR,
    minFragmentLength: Int = 10,
    maxFragmentLength: Int = 60,
    fps: Int = 10,
    size: Dp = 300.dp,
) {
    val frameDelayMillis = 1000L / fps
    var frameString by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(true) {
        scope.launch {
            delay(100)
            val encoder = UREncoder(Ur, maxFragmentLength, minFragmentLength, 0);
            while (true) {
                frameString = encoder.nextPart().toString()
                delay(frameDelayMillis)
            }
        }
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (frameString != null) {
            QrCodeView(
                data = frameString!!,
                colors = QrCodeColors(
                    background = MaterialTheme.colorScheme.background,
                    foreground = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier
                    .size(size)
                    .padding(18.dp),
            )
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 1.dp
            )
        }
    }
}
