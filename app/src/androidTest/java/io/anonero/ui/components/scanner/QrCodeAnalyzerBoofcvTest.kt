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
 */

package io.anonero.ui.components.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class QrCodeAnalyzerBoofcvTest {
    private val analyzer = QrCodeAnalyzerBoofcv { error("Unexpected QR detection") }

    @Test
    fun closesUnsupportedFrame() {
        val frame = TestFrame { ImageFormat.UNKNOWN }
        analyzer.analyze(frame.proxy)
        assertEquals(1, frame.closeCount)
    }

    @Test
    fun closesProxyWithoutImage() {
        val frame = TestFrame { ImageFormat.YUV_420_888 }
        analyzer.analyze(frame.proxy)
        assertEquals(1, frame.closeCount)
    }

    @Test
    fun closesFrameWhenAccessThrows() {
        val failure = IllegalStateException("Synthetic frame failure")
        val frame = TestFrame { throw failure }
        val result = runCatching { analyzer.analyze(frame.proxy) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, frame.closeCount)
    }

    private class TestFrame(
        format: () -> Int,
    ) {
        var closeCount = 0
            private set

        val proxy: ImageProxy =
            Proxy.newProxyInstance(
                ImageProxy::class.java.classLoader,
                arrayOf(ImageProxy::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getFormat" -> {
                        format()
                    }

                    "getImage" -> {
                        null
                    }

                    "close" -> {
                        closeCount++
                        null
                    }

                    else -> {
                        error("Unexpected ImageProxy call: ${method.name}")
                    }
                }
            } as ImageProxy
    }
}
