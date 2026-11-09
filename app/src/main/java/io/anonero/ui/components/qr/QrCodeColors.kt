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

package io.anonero.ui.components.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Defines the color palette for a QR code.
 */
data class QrCodeColors(
    val background: Color, val foreground: Color
) {
    companion object {
        @Composable
        fun default() = QrCodeColors(
            background = Color.White,
            foreground = Color.Black
        )
    }
}
