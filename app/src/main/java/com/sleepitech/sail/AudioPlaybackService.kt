package com.sleepitech.sail

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlin.random.Random

data class StarPosition(val x: Float, val y: Float, val size: Float, val alpha: Float)

data class MenuItem(
    val name: String,
    val colors: List<Color>,
    val backgroundMusic: String?,
    val highlightSound: String?,
    val subMenu: List<SubMenuItem> = emptyList(),
    val subMenuMusic: String? = null,
    val stars: List<StarPosition> = emptyList()
)

data class SubMenuItem(val name: String)

data class AudioManifest(
    val startup_tone: String,
    val startup_greeting: String,
    val menu_item_selected: String,
    val menu_highlight_sounds: Map<String, String>,
    val menu_background_music: Map<String, String>,
    val submenu_background_music: Map<String, String>
)

class AudioPlaybackService : Service() {

    private val TAG = "AudioPlaybackService"
    private val binder = LocalBinder()

    private var backgroundMusicPlayer: MediaPlayer? = null
    private var highlightSoundPlayer: MediaPlayer? = null
    private var startupTonePlayer: MediaPlayer? = null
    private var startupGreetingPlayer: MediaPlayer? = null
    private var menuSelectedPlayer: MediaPlayer? = null
    private var currentMusicName: String? = null

    private lateinit var audioManifest: AudioManifest

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        loadAudioManifest()
    }

    override fun onBind(intent: Intent): IBinder = binder

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

    fun getMenuItems(): List<MenuItem> {
        val sleepSubMenu = listOf(SubMenuItem("Start Sleep"), SubMenuItem("Stories"), SubMenuItem("Soundscapes"), SubMenuItem("Sleep Guidance"))
        return listOf(
            MenuItem("Sleep", listOf(Color(0xFF0d3b66), Color(0xFF1a4b7c), Color(0xFF285c93)),
                audioManifest.menu_background_music["sleep"],
                audioManifest.menu_highlight_sounds["sleep"],
                sleepSubMenu, audioManifest.submenu_background_music["sleep"], stars = generateStars()),
            MenuItem("Calm", listOf(Color(0xFF3b8d99), Color(0xFF4eb0c4), Color(0xFF63cbe0)),
                audioManifest.menu_background_music["calm"],
                audioManifest.menu_highlight_sounds["calm"],
                subMenuMusic = audioManifest.submenu_background_music["calm"], stars = generateStars()),
            MenuItem("Night Wake", listOf(Color(0xFF1e2a3a), Color(0xFF2a394f), Color(0xFF364864)),
                audioManifest.menu_background_music["night_wake"],
                audioManifest.menu_highlight_sounds["night_wake"],
                subMenuMusic = audioManifest.submenu_background_music["night_wake"], stars = generateStars()),
            MenuItem("Breathe", listOf(Color(0xFF78a3a5), Color(0xFF90babf), Color(0xFFa9d1d8)),
                audioManifest.menu_background_music["breathe"],
                audioManifest.menu_highlight_sounds["breathe"],
                subMenuMusic = audioManifest.submenu_background_music["breathe"], stars = generateStars()),
            MenuItem("Awake", listOf(Color(0xFFf4a261), Color(0xFFe76f51), Color(0xFFd94e3d)),
                audioManifest.menu_background_music["awake"],
                audioManifest.menu_highlight_sounds["awake"],
                subMenuMusic = audioManifest.submenu_background_music["awake"], stars = generateStars()),
            MenuItem("Library", listOf(Color(0xFF6d6875), Color(0xFF8b8593), Color(0xFFa9a2b1)),
                audioManifest.menu_background_music["library"],
                audioManifest.menu_highlight_sounds["library"],
                subMenuMusic = audioManifest.submenu_background_music["library"], stars = generateStars())
        )
    }

    private fun generateStars(count: Int = 50): List<StarPosition> {
        return (1..count).map {
            StarPosition(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.5f, // Only in the top half
                size = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.5f
            )
        }
    }

    private fun getRawResourceId(resNameWithExtension: String?): Int {
        if (resNameWithExtension.isNullOrEmpty()) return 0
        val resName = resNameWithExtension.substringBeforeLast('.')
        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            Log.w(TAG, "Audio resource not found for name: '$resNameWithExtension'")
        }
        return resId
    }

    fun playStartupSequence(onCompletion: () -> Unit) {
        val toneResId = getRawResourceId(audioManifest.startup_tone)
        val greetingResId = getRawResourceId(audioManifest.startup_greeting)

        startupGreetingPlayer?.release()
        startupTonePlayer?.release()

        if (greetingResId != 0) {
            startupGreetingPlayer = MediaPlayer.create(this, greetingResId)
            startupGreetingPlayer?.setOnCompletionListener {
                it.release()
                startupGreetingPlayer = null
                onCompletion()
            }
        }

        if (toneResId != 0) {
            startupTonePlayer = MediaPlayer.create(this, toneResId)
            startupTonePlayer?.setOnCompletionListener {
                it.release()
                startupTonePlayer = null
                startupGreetingPlayer?.start() ?: onCompletion()
            }
        }

        startupTonePlayer?.start() ?: startupGreetingPlayer?.start() ?: onCompletion()
    }

    fun playMenuSelectedSound() {
        val resId = getRawResourceId(audioManifest.menu_item_selected)
        if (resId != 0) {
            menuSelectedPlayer?.release()
            menuSelectedPlayer = MediaPlayer.create(this, resId)
            menuSelectedPlayer?.setOnCompletionListener { 
                it.release()
                menuSelectedPlayer = null
            }
            menuSelectedPlayer?.start()
        }
    }

    fun playMainMenuMusic() {
        playBackgroundMusic(audioManifest.menu_background_music["sleep"]) // Assuming all main menu items have the same music
    }

    fun playBackgroundMusic(musicName: String?) {
        if (musicName == currentMusicName && backgroundMusicPlayer?.isPlaying == true) {
            return // Music is already playing
        }
        currentMusicName = musicName

        backgroundMusicPlayer?.stop()
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null

        val resId = getRawResourceId(musicName)
        if (resId != 0) {
            backgroundMusicPlayer = MediaPlayer.create(this, resId)
            backgroundMusicPlayer?.isLooping = true
            backgroundMusicPlayer?.start()
        }
    }

    fun playHighlightSound(soundName: String?) {
        highlightSoundPlayer?.release()
        highlightSoundPlayer = null
        val resId = getRawResourceId(soundName)
        if (resId != 0) {
            highlightSoundPlayer = MediaPlayer.create(this, resId)
            highlightSoundPlayer?.setOnCompletionListener {
                it.release()
                if (highlightSoundPlayer == it) {
                    highlightSoundPlayer = null
                }
            }
            highlightSoundPlayer?.start()
        }
    }

    fun stopBackgroundMusic() {
        backgroundMusicPlayer?.stop()
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null
        currentMusicName = null
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundMusicPlayer?.release()
        highlightSoundPlayer?.release()
        startupTonePlayer?.release()
        startupGreetingPlayer?.release()
        menuSelectedPlayer?.release()
    }
}
