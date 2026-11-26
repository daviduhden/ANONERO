/*
 * Copyright (c) 2026, David Uhden Collado
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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.sparrowwallet.hummingbird.UR
import org.junit.Rule
import org.junit.Test

class AnimatedQrCodeSessionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hidesThePreviousPayloadUntilTheReplacementEncoderProducesAFrame() {
        val payload = mutableStateOf(UR.fromBytes("bytes", byteArrayOf(1, 2, 3)))
        val spinner = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { AnimatedQrCode(Ur = payload.value) }
        }

        compose.mainClock.advanceTimeBy(160)
        compose.onNode(spinner).assertDoesNotExist()

        compose.runOnIdle { payload.value = UR.fromBytes("bytes", byteArrayOf(4, 5, 6)) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNode(spinner).assertExists()

        compose.mainClock.advanceTimeBy(160)
        compose.onNode(spinner).assertDoesNotExist()
    }
}
