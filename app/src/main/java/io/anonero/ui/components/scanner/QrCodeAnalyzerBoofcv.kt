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
import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.struct.image.GrayU8
import com.google.zxing.BarcodeFormat
import com.google.zxing.ChecksumException
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * BoofCV QR decoder for CameraX frames. The camera path uses the YUV luminance
 * plane directly, including its row padding.
 *
 * Calls back on the camera analysis executor. Callers can receive several detections
 * per frame and repeated detections across frames.
 */
class QrCodeAnalyzerBoofcv(
    private val onQrCodesDetected: (qrCode: Result) -> Unit,
) : ImageAnalysis.Analyzer {
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    private val yuvFormats = mutableListOf(ImageFormat.YUV_420_888)

    init {
        yuvFormats.addAll(
            listOf(
                ImageFormat.YUV_422_888,
                ImageFormat.YUV_444_888,
                ImageFormat.JPEG,
            ),
        )
    }

    private val detector = FactoryFiducial.qrcode(null, GrayU8::class.java)

    fun imageToGrayU8(image: Image?): GrayU8 {
        val planes = image?.planes
        val yBuffer = planes?.get(0)?.buffer
        val yData = ByteArray(yBuffer?.remaining() ?: 0)
        yBuffer?.get(yData)

        val width = image?.width ?: 0
        val height = image?.height ?: 0
        val grayU8 = GrayU8(width, height)
        grayU8.data = yData
        // BoofCV must advance by the camera row stride to skip alignment padding.
        grayU8.stride = planes?.get(0)?.rowStride ?: 0

        return grayU8
    }

    private var debugI = 0

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

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (debugI % 100 == 10) {
                Timber.tag("QrCodeAnalyzerBoofcv.kt").d("$debugI analyze(image: ImageProxy)")
            }
            debugI++
            if (imageProxy.format !in yuvFormats) return
            val image = imageProxy.image ?: return
            val imgGrayU8: GrayU8 = imageToGrayU8(image)

            try {
                detector.process(imgGrayU8)
            } catch (e: NotFoundException) {
            } catch (e: ChecksumException) {
            } catch (e: Exception) {
            }
            for (det in detector.detections) {
                onQrCodesDetected(Result(det.message, det.rawbits, null, BarcodeFormat.QR_CODE))
            }

            // Accept light-on-dark QR codes as well as standard dark-on-light codes.
            val invImgGrayU8: GrayU8 = invertGrayU8Image(imgGrayU8)

            try {
                detector.process(invImgGrayU8)
            } catch (e: NotFoundException) {
            } catch (e: ChecksumException) {
            } catch (e: Exception) {
            }

            for (det in detector.detections) {
                onQrCodesDetected(Result(det.message, det.rawbits, null, BarcodeFormat.QR_CODE))
            }
        } finally {
            // CameraX needs the proxy released even if an early return or a callback aborts analysis.
            imageProxy.close()
        }
    }
}
