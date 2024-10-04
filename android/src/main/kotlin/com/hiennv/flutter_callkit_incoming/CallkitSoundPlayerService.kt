package com.hiennv.flutter_callkit_incoming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.text.TextUtils

class CallkitSoundPlayerService : Service() {

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    private var mediaPlayer: MediaPlayer? = null
    private var data: Bundle? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.prepare()
        this.playSound(intent)
        this.playVibrator()
        return START_STICKY;
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
    }

    private fun prepare() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
    }

    private fun playVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
        when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> {
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 1000L, 1000L), 0))
                } else {
                    vibrator?.vibrate(longArrayOf(0L, 1000L, 1000L), 0)
                }
            }
        }
    }

    private fun playSound(intent: Intent?) {
        this.data = intent?.extras
        val sound = this.data?.getString(
            CallkitIncomingBroadcastReceiver.EXTRA_CALLKIT_RINGTONE_PATH,
            ""
        )
        
        println("loading sound", "Debug message")
        println(sound, "Debug message")
        try {
        val uri = sound?.let { getRingtoneUri(it) }
            ?: getSafeDefaultRingtoneUri()
            ?: getFallbackRingtoneUri()
    
       
            mediaPlayer(uri)
        } catch (e: Exception) {
            println("error on playSound", "Debug message")
            e.printStackTrace()
            playFallbackSound()
        }
    }
    
    private fun getSafeDefaultRingtoneUri(): Uri? {
        return try {
            RingtoneManager.getActualDefaultRingtoneUri(
                this@CallkitSoundPlayerService,
                RingtoneManager.TYPE_RINGTONE
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun getFallbackRingtoneUri(): Uri {
        return Uri.parse("android.resource://${packageName}/${R.raw.default_ringtone}")
    }
    
    private fun playFallbackSound() {
        try {
            val fallbackUri = getFallbackRingtoneUri()
            mediaPlayer(fallbackUri)
        } catch (e: Exception) {
            e.printStackTrace()
            // If all else fails, maybe use a simple ToneGenerator as a last resort
            playSimpleTone()
        }
    }
    
    private fun playSimpleTone() {
        val toneGen = ToneGenerator(AudioManager.STREAM_RING, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_RING_BACK, 3000)
    }

    private fun mediaPlayer(uri: Uri) {
        mediaPlayer = MediaPlayer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val attribution = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setLegacyStreamType(AudioManager.STREAM_RING)
                    .build()
            mediaPlayer?.setAudioAttributes(attribution)
        } else {
            mediaPlayer?.setAudioStreamType(AudioManager.STREAM_RING)
        }
        val assetFileDescriptor = applicationContext.getContentResolver().openAssetFileDescriptor(uri, "r")
        if (assetFileDescriptor != null) {
            mediaPlayer?.setDataSource(assetFileDescriptor)
        } else {
            mediaPlayer?.setDataSource(applicationContext, uri)
        }
        mediaPlayer?.prepare()
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    private fun getRingtoneUri(fileName: String) = try {
        if (TextUtils.isEmpty(fileName)) {
            RingtoneManager.getActualDefaultRingtoneUri(
                    this@CallkitSoundPlayerService,
                    RingtoneManager.TYPE_RINGTONE
            )
        }
        val resId = resources.getIdentifier(fileName, "raw", packageName)
        if (resId != 0) {
            Uri.parse("android.resource://${packageName}/$resId")
        } else {
            if (fileName.equals("system_ringtone_default", true)) {
                RingtoneManager.getActualDefaultRingtoneUri(
                        this@CallkitSoundPlayerService,
                        RingtoneManager.TYPE_RINGTONE
                )
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(
                        this@CallkitSoundPlayerService,
                        RingtoneManager.TYPE_RINGTONE
                )
            }
        }
    } catch (e: Exception) {
        try {
            if (fileName.equals("system_ringtone_default", true)) {
                RingtoneManager.getActualDefaultRingtoneUri(
                        this@CallkitSoundPlayerService,
                        RingtoneManager.TYPE_RINGTONE
                )
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(
                        this@CallkitSoundPlayerService,
                        RingtoneManager.TYPE_RINGTONE
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}