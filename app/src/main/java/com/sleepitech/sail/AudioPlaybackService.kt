package com.sleepitech.sail

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class AudioPlaybackService : Service() {

    private val TAG = "AudioPlaybackService"
    private val binder = LocalBinder()

    private var backgroundMusicPlayer: MediaPlayer? = null
    private var highlightSoundPlayer: MediaPlayer? = null
    private var startupTonePlayer: MediaPlayer? = null
    private var startupGreetingPlayer: MediaPlayer? = null

    private lateinit var audioManifest: AudioManifest

    override fun onCreate() {
        super.onCreate()
        loadAudioManifest()
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun loadAudioManifest() {
        try {
            assets.open("local_audio_manifest.json").use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val type = object : TypeToken<AudioManifest>() {}.type
                    audioManifest = Gson().fromJson(reader, type)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio manifest.", e)
            audioManifest = AudioManifest("", "", "", emptyMap(), emptyMap(), emptyMap())
        }
    }

    fun getRawResourceId(resNameWithExtension: String?): Int {
        if (resNameWithExtension.isNullOrEmpty()) return 0
        val resName = resNameWithExtension.substringBeforeLast('.')
        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            Log.e(TAG, "Audio resource not found: '$resNameWithExtension'.")
        }
        return resId
    }

    fun playStartupSequence(onCompletion: () -> Unit) {
        val toneResId = getRawResourceId(audioManifest.startup_tone)
        val greetingResId = getRawResourceId(audioManifest.startup_greeting)

        val greetingPlayer = if (greetingResId != 0) MediaPlayer.create(this, greetingResId) else null
        greetingPlayer?.setOnCompletionListener { 
            it.release()
            onCompletion()
        }

        val tonePlayer = if (toneResId != 0) MediaPlayer.create(this, toneResId) else null
        tonePlayer?.setOnCompletionListener { 
            it.release()
            greetingPlayer?.start() ?: onCompletion()
        }

        tonePlayer?.start() ?: greetingPlayer?.start() ?: onCompletion()
    }

    fun playBackgroundMusic(resId: Int) {
        backgroundMusicPlayer?.release()
        if (resId != 0) {
            backgroundMusicPlayer = MediaPlayer.create(this, resId)
            backgroundMusicPlayer?.isLooping = true
            backgroundMusicPlayer?.start()
        } else {
            backgroundMusicPlayer = null
        }
    }

    fun playHighlightSound(resId: Int) {
        highlightSoundPlayer?.release()
        if (resId != 0) {
            highlightSoundPlayer = MediaPlayer.create(this, resId)
            highlightSoundPlayer?.setOnCompletionListener { it.release() }
            highlightSoundPlayer?.start()
        } else {
            highlightSoundPlayer = null
        }
    }

    fun stopBackgroundMusic() {
        backgroundMusicPlayer?.stop()
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        backgroundMusicPlayer?.release()
        highlightSoundPlayer?.release()
        startupTonePlayer?.release()
        startupGreetingPlayer?.release()
    }
}
