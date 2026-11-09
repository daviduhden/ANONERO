package io.anonero.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.anonero.model.Wallet
import io.anonero.ui.theme.DangerColor
import io.anonero.ui.theme.DarkOrange


@Composable
fun DaemonStatus(connectionStatus: Wallet.ConnectionStatus?, modifier: Modifier = Modifier) {


    val color: Color = when (connectionStatus) {
        null -> {
            DarkOrange
        }

        Wallet.ConnectionStatus.ConnectionStatus_Disconnected -> {
            Color.Red
        }

        Wallet.ConnectionStatus.ConnectionStatus_Connected -> {
            Color.Green
        }

        Wallet.ConnectionStatus.ConnectionStatus_WrongVersion -> {
            DangerColor
        }

    }


    val animatedColor by animateColorAsState(
        color,
        label = "color"
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(animatedColor, shape = CircleShape)
        )
    }
}