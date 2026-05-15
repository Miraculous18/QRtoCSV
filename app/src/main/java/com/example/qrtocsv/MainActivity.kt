package com.example.qrtocsv

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: androidx.camera.view.PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var executor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var csvManager: CSVManager
    private var isScanning = false
    private var canScanAgain = true
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var successOverlay: View
    private lateinit var ivSuccessIcon: ImageView
    private lateinit var soundPlayer: SoundPlayer

    private lateinit var btnFlashlight: ImageButton
    private var isFlashlightOn = false
    private var cameraControl: CameraControl? = null
    private lateinit var cameraProvider: ProcessCameraProvider

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация
        cameraPreview = findViewById(R.id.cameraPreview)
        executor = Executors.newSingleThreadExecutor()
        csvManager = CSVManager(this)
        soundPlayer = SoundPlayer(this)

        // Инициализация оверлея успеха (теперь он в XML)
        successOverlay = findViewById(R.id.successOverlay)
        ivSuccessIcon = successOverlay.findViewById(R.id.ivSuccessIcon)
        successOverlay.setOnClickListener {
            hideSuccessOverlay()
        }
        successOverlay.visibility = View.GONE

        btnFlashlight = findViewById(R.id.btnFlashlight)
        btnFlashlight.setOnClickListener {
            toggleFlashlight()
        }

        // Настройка сканера QR кодов
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        // Кнопки
        findViewById<android.widget.Button>(R.id.btnScan).setOnClickListener {
            if (!isScanning) {
                if (canScanAgain) {
                    startScanning()
                } else {
                    Toast.makeText(this, "Подождите 3 секунды перед следующим сканированием", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopScanning()
            }
        }

        findViewById<android.widget.Button>(R.id.btnViewData).setOnClickListener {
            showDataDialog()
        }

        requestPermissions()
    }

    private fun toggleFlashlight() {
        if (!isFlashlightOn) {
            turnOnFlashlight()
        } else {
            turnOffFlashlight()
        }
    }

    private fun turnOnFlashlight() {
        try {
            cameraControl?.enableTorch(true)
            isFlashlightOn = true
            btnFlashlight.setImageResource(R.drawable.ic_flashlight_on)
            Toast.makeText(this, "Фонарик включен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка включения фонарика", Toast.LENGTH_SHORT).show()
        }
    }

    private fun turnOffFlashlight() {
        try {
            cameraControl?.enableTorch(false)
            isFlashlightOn = false
            btnFlashlight.setImageResource(R.drawable.ic_flashlight_off)
            Toast.makeText(this, "Фонарик выключен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка выключения фонарика", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.CAMERA)

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 100)
        } else {
            startCamera()
        }
    }

    private fun showSuccessOverlay(success: Boolean) {
        runOnUiThread {
            // Устанавливаем иконку
            ivSuccessIcon.setImageResource(R.drawable.ic_success)

            // Показываем overlay
            successOverlay.visibility = View.VISIBLE
            successOverlay.bringToFront() // Важно: поверх всего

            // Анимация
            try {
                val animation = AnimationUtils.loadAnimation(this, R.anim.success_animation)
                ivSuccessIcon.startAnimation(animation)
            } catch (_: Exception) {
                // Простая программная анимация
                ivSuccessIcon.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .alpha(1f)
                    .setDuration(400)
                    .withEndAction {
                        ivSuccessIcon.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }

            // Звук
            soundPlayer.playScanSuccessSound()

            // Автоматически скрываем через 3 секунды
            Handler(Looper.getMainLooper()).postDelayed({
                hideSuccessOverlay()
            }, 3000)
        }
    }

    private fun hideSuccessOverlay() {
        runOnUiThread {
            successOverlay.visibility = View.GONE
            ivSuccessIcon.clearAnimation()

            // ВОССТАНАВЛИВАЕМ ОРИГИНАЛЬНЫЙ ЦВЕТ ИКОНКИ
            ivSuccessIcon.clearColorFilter()

            // Восстанавливаем оригинальный фон (опционально)
            successOverlay.setBackgroundColor("#800c8000".toColorInt())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Разрешения необходимы для работы приложения", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Сохраняем cameraControl для управления фонариком
                cameraControl = camera.cameraControl

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Ошибка запуска камеры: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(imageProxy: androidx.camera.core.ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        rawValue?.let {
                            CoroutineScope(Dispatchers.Main).launch {
                                onQRCodeDetected(it)
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun onQRCodeDetected(data: String) {
        if (!isScanning || !canScanAgain) return

        stopScanning()
        canScanAgain = false

        // Проверяем дубликат
        if (csvManager.isDuplicate(data)) {
            Toast.makeText(this, "Этот QR-код уже был отсканирован", Toast.LENGTH_SHORT).show()
            showDuplicateOverlay() // Показываем оверлей для дубликата
            handler.postDelayed({
                canScanAgain = true
                startScanning()
            }, 3000)
            return
        }

        val success = csvManager.saveQRCodeData(data)
        showSuccessOverlay(success)

        if (success) {
            Toast.makeText(this, "QR-код сохранен", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }

        handler.postDelayed({
            canScanAgain = true
            startScanning()
        }, 3000)
    }

    private fun showDuplicateOverlay() {
        runOnUiThread {
            // Используем ту же иконку, но меняем цвет
            ivSuccessIcon.setImageResource(R.drawable.ic_success)
            ivSuccessIcon.setColorFilter("#FF9800".toColorInt(), android.graphics.PorterDuff.Mode.SRC_IN)

            successOverlay.visibility = View.VISIBLE
            successOverlay.bringToFront()

            // Меняем фон на оранжевый (опционально)
            successOverlay.setBackgroundColor("#80FF9800".toColorInt())

            Handler(Looper.getMainLooper()).postDelayed({
                hideSuccessOverlay()
            }, 3000)
        }
    }

    private fun startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения на камеру", Toast.LENGTH_SHORT).show()
            return
        }

        if (!canScanAgain) {
            return
        }

        isScanning = true
        findViewById<android.widget.Button>(R.id.btnScan).text = "Остановить"
    }

    private fun stopScanning() {
        isScanning = false
        canScanAgain = true
        findViewById<android.widget.Button>(R.id.btnScan).text = "Сканировать"
    }

    private fun showDataDialog() {
        val records = csvManager.readAllRecords()
        val filePath = csvManager.getCSVFilePath()

        if (records.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Данные")
                .setMessage("Нет сохраненных данных\n\nФайл: $filePath")
                .setPositiveButton("OK", null)
                .setNeutralButton("Очистить файл") { dialog, _ ->
                    dialog.dismiss()
                    showClearCSVConfirmation()
                }
                .show()
        } else {
            val message = StringBuilder()
            message.append("Всего записей: ${records.size}\n")
            message.append("Файл: $filePath")

            AlertDialog.Builder(this)
                .setTitle("Сохраненные данные")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Очистить файл") { dialog, _ ->
                    dialog.dismiss()
                    showClearCSVConfirmation()
                }
                .setNegativeButton("Поделиться") { dialog, _ ->
                    dialog.dismiss()
                    shareCSVFile()
                }
                .show()
        }
    }

    private fun shareCSVFile() {
        try {
            val filePath = csvManager.getCSVFilePath()
            val file = File(filePath)

            if (!file.exists() || file.length() == 0L) {
                Toast.makeText(this, "Файл CSV не найден или пустой", Toast.LENGTH_SHORT).show()
                return
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "QR Codes Data")
                putExtra(Intent.EXTRA_TEXT, "Данные отсканированных QR-кодов")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Поделиться CSV файлом"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при отправке файла: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showClearCSVConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Очистка CSV файла")
            .setMessage("Вы уверены, что хотите очистить CSV файл? Все данные будут удалены.")
            .setPositiveButton("Да, очистить") { dialog, _ ->
                dialog.dismiss()
                csvManager.clearCSVFile()
                Toast.makeText(this, "Файл очищен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        turnOffFlashlight() // Выключаем фонарик при выходе
        executor.shutdown()
        barcodeScanner.close()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopScanning()
        }
        handler.removeCallbacksAndMessages(null)
    }
}