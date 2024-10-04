package com.hiennv.flutter_callkit_incoming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.provider.Settings

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
        println("playSound: Starting playSound function")
        this.data = intent?.extras
        println("playSound: Intent extras: ${this.data}")
        val sound = this.data?.getString(
            CallkitIncomingBroadcastReceiver.EXTRA_CALLKIT_RINGTONE_PATH,
            ""
        )

        println("playSound: Attempting to play sound: $sound")

        if (sound.isNullOrEmpty()) {
            println("playSound: Sound is null or empty, trying default ringtone")
            playDefaultRingtone()
            return
        }

        try {
            val uri = getRingtoneUri(sound)
            if (uri != null) {
                println("playSound: Custom ringtone URI found: $uri")
                playRingtone(uri)
            } else {
                println("playSound: getRingtoneUri returned null, trying default")
                playDefaultRingtone()
            }
        } catch (e: Exception) {
            println("playSound: Error occurred: ${e.message}")
            e.printStackTrace()
            playDefaultRingtone()
        }
    }

    private fun playDefaultRingtone() {
        println("playDefaultRingtone: Attempting to play default ringtone")
        try {
            val defaultUri = RingtoneManager.getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_RINGTONE)
            if (defaultUri != null) {
                println("playDefaultRingtone: Default ringtone URI found: $defaultUri")
                playRingtone(defaultUri)
            } else {
                println("playDefaultRingtone: Default ringtone URI is null")
                throw Exception("Default ringtone URI is null")
            }
        } catch (e: Exception) {
            println("playDefaultRingtone: Error occurred: ${e.message}")
            e.printStackTrace()
            playSimpleTone()
        }
    }

    private fun playRingtone(uri: Uri) {
        println("playRingtone: Attempting to play ringtone with URI: $uri")
        try {
            val player = MediaPlayer()
            player.setDataSource(applicationContext, uri)
            println("playRingtone: DataSource set")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            println("playRingtone: AudioAttributes set")
            player.prepare()
            println("playRingtone: MediaPlayer prepared")
            player.start()
            println("playRingtone: Playback started")

            player.setOnCompletionListener { mp ->
                println("playRingtone: Playback completed")
                mp.release()
            }
        } catch (e: Exception) {
            println("playRingtone: Error occurred: ${e.message}")
            e.printStackTrace()
            playSimpleTone()
        }
    }

    private fun playSimpleTone() {
        println("playSimpleTone: Playing simple tone as last resort")
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_RING, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 3000)
            println("playSimpleTone: Tone generation started")
            Handler(Looper.getMainLooper()).postDelayed({
                toneGen.release()
                println("playSimpleTone: ToneGenerator released")
            }, 3000)
        } catch (e: Exception) {
            println("playSimpleTone: Error occurred: ${e.message}")
            e.printStackTrace()
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaPlayer?.setDataSource(assetFileDescriptor)
            }
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