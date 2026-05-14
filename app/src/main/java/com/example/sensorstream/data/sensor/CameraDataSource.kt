package com.example.sensorstream.data.sensor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.sensorstream.data.model.CameraResolution
import com.example.sensorstream.data.model.StreamConfig
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class CameraDataSource {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        config: StreamConfig,
        onFrameReady: (ByteArray, Long) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Setup Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Setup Resolution
            val targetResolution = when (config.cameraResolution) {
                CameraResolution.VGA -> Size(640, 480)
                CameraResolution.HD -> Size(1280, 720)
            }

            // Setup Image Analysis (Frame Extraction)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        val timestampNs = imageProxy.imageInfo.timestamp
                        try {
                            // Offload heavy processing to Default dispatcher to prevent blocking
                            val bitmap = imageProxy.toBitmap()
                            
                            // Compress Bitmap to JPEG - Lower quality for higher speed (45 is good for SLAM)
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 45, outputStream)
                            val jpegBytes = outputStream.toByteArray()
                            
                            // Emit the frame with its hardware timestamp
                            onFrameReady(jpegBytes, timestampNs)
                            
                        } catch (e: Exception) {
                            Log.e("CameraDataSource", "Error processing frame", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )

            } catch(exc: Exception) {
                Log.e("CameraDataSource", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }
}
