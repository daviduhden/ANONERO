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
fun DaemonStatus(
    connectionStatus: Wallet.ConnectionStatus?,
    modifier: Modifier = Modifier,
) {
    val color: Color =
        when (connectionStatus) {
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
        label = "color",
    )
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(animatedColor, shape = CircleShape),
        )
    }
}
