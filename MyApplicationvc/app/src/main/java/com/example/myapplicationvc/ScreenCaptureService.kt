
package com.example.myapplicationvc

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isCrashDetected = false

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode != -1 && data != null) {
            startForegroundWithNotification()
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            startScreenCapture()
        }

        return START_NOT_STICKY
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                processImage(image)
                image.close()
            }
        }, handler)
    }

    private fun processImage(image: Image) {
        val bitmap = imageToBitmap(image)
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val pixel = bitmap.getPixel(centerX, centerY)

        // Simple red color detection (adjust threshold as needed)
        if (android.graphics.Color.red(pixel) > 200 && !isCrashDetected) {
            isCrashDetected = true
            recognizeText(bitmap)
        } else if (android.graphics.Color.red(pixel) < 100) {
            isCrashDetected = false
        }
    }

    private fun recognizeText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val multiplier = extractMultiplier(visionText.text)
                if (multiplier != null) {
                    saveCrashData(multiplier)
                }
            }
            .addOnFailureListener { e ->
                // Handle text recognition failure
            }
    }

    private fun extractMultiplier(text: String): Double? {
        val regex = "(\d+\.\d+)x".toRegex()
        val matchResult = regex.find(text)
        return matchResult?.groups?.get(1)?.value?.toDoubleOrNull()
    }

    private fun saveCrashData(multiplier: Double) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val csvLine = "$timestamp,$multiplier\n"
        try {
            val file = File(filesDir, "crashes.csv")
            FileOutputStream(file, true).bufferedWriter().use { writer ->
                writer.append(csvLine)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(SERVICE_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val name = "Screen Capture"
        val descriptionText = "Notification for screen capture service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture")
            .setContentText("Capturing screen...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val SERVICE_ID = 1
        private const val CHANNEL_ID = "ScreenCaptureChannel"
    }
}
