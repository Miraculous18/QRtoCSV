package com.example.qrtocsv

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator

class SoundPlayer(private val context: Context) {

    // Добавляем AudioManager для работы с громкостью
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun playScanSuccessSound() {
        try {
            // Получаем текущую громкость для потока уведомлений (NOTIFICATION)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

            // Рассчитываем громкость от 0.0 до 1.0
            val volumePercent = currentVolume.toFloat() / maxVolume.toFloat()

            // Если громкость 0, не играем звук
            if (currentVolume == 0) {
                return
            }

            // Преобразуем в громкость для ToneGenerator (0-100)
            val toneVolume = (volumePercent * 100).toInt()

            // Простой звуковой сигнал с учетом громкости телефона
            val toneGenerator = ToneGenerator(ToneGenerator.TONE_DTMF_0, toneVolume)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)

            // Останавливаем через 300 мс
            android.os.Handler(context.mainLooper).postDelayed({
                toneGenerator.release()
            }, 300)

        } catch (e: Exception) {
            e.printStackTrace()

            // Альтернатива: системный звук уведомления с учетом громкости
            try {
                val mediaPlayer = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                mediaPlayer?.apply {
                    // Настраиваем громкость в соответствии с системной
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                    val volumePercent = currentVolume.toFloat() / maxVolume.toFloat()

                    // Устанавливаем громкость для левого и правого канала
                    setVolume(volumePercent, volumePercent)

                    setOnCompletionListener { it.release() }
                    start()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
                // Если и это не работает, просто игнорируем
            }
        }
    }

}