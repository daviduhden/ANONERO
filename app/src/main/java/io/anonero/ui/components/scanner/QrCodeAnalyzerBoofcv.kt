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

package io.anonero.ui.components.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.struct.image.GrayU8
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import java.nio.ByteBuffer

/**
 * BoofCV QR decoder for CameraX frames. The camera path uses the YUV luminance
 * plane with its declared row and pixel strides.
 *
 * Calls back on the camera analysis executor. Callers can receive several detections
 * per frame and repeated detections across frames.
 */
class QrCodeAnalyzerBoofcv(
    private val onQrCodesDetected: (qrCode: Result) -> Unit,
) : ImageAnalysis.Analyzer {
    private val detector = FactoryFiducial.qrcode(null, GrayU8::class.java)

    internal fun luminance(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): GrayU8? {
        if (width <= 0 || height <= 0 || pixelStride <= 0) return null
        val rowBytes = (width - 1L) * pixelStride + 1
        if (rowStride < rowBytes || (height - 1L) * rowStride + rowBytes > buffer.remaining()) return null
        val image = GrayU8(width, height)
        val source = buffer.duplicate()
        val start = source.position()
        for (row in 0 until height) {
            val offset = start + row * rowStride
            if (pixelStride == 1) {
                source.position(offset)
                source.get(image.data, row * width, width)
            } else {
                for (column in 0 until width) image.data[row * width + column] = source.get(offset + column * pixelStride)
            }
        }
        return image
    }

    fun invertGrayU8Image(inputImage: GrayU8): GrayU8 {
        val width = inputImage.width
        val height = inputImage.height

        val invertedImage = GrayU8(width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val intensity = inputImage.get(x, y)
                val invertedIntensity = 255 - intensity
                invertedImage.set(x, y, invertedIntensity)
            }
        }

        return invertedImage
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // CameraX supplies YUV_420_888 by default. JPEG buffers contain encoded bytes.
            when (imageProxy.format) {
                ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888 -> Unit
                else -> return
            }
            val plane = imageProxy.planes.firstOrNull() ?: return
            val gray = luminance(plane.buffer, imageProxy.width, imageProxy.height, plane.rowStride, plane.pixelStride) ?: return
            detect(gray)
            detect(invertGrayU8Image(gray))
        } finally {
            imageProxy.close()
        }
    }

    private fun detect(frame: GrayU8) {
        try {
            detector.process(frame)
        } catch (_: RuntimeException) {
            // A failed pass may leave partial or previous detections in the reusable detector.
            return
        }
        for (detection in detector.detections) {
            onQrCodesDetected(Result(detection.message, detection.rawbits, null, BarcodeFormat.QR_CODE))
        }
    }
}
