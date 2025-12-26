package com.sleepitech.sail

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sleepitech.sail.ui.theme.SailTheme
import java.io.InputStreamReader
import kotlin.math.sin
import kotlin.random.Random

sealed class Screen {
    object SailHome : Screen()
    object MainMenu : Screen()
    data class SubMenu(val parentMenu: MenuItem) : Screen()
}

data class StarPosition(val x: Float, val y: Float, val size: Float, val alpha: Float)

data class MenuItem(
    val name: String,
    val colors: List<Color>,
    val backgroundMusicResId: Int,
    val highlightSoundResId: Int,
    val subMenu: List<SubMenuItem> = emptyList(),
    val subMenuMusicResId: Int = 0,
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

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var currentScreen by mutableStateOf<Screen>(Screen.SailHome)
    private var selectedMenuIndex by mutableStateOf(0)
    private var selectedSubMenuIndex by mutableStateOf(0)
    private var lastDpadEvent by mutableStateOf(0L)

    private lateinit var menuItems: List<MenuItem>
    private lateinit var audioManifest: AudioManifest

    private var startupTonePlayer: MediaPlayer? = null
    private var startupGreetingPlayer: MediaPlayer? = null
    private var backgroundMusicPlayer: MediaPlayer? = null
    private var highlightSoundPlayer: MediaPlayer? = null
    private var isStartupSequenceCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadAudioManifest()
        setupMenuItems()
        setupBackButton()

        enableEdgeToEdge()
        setContent {
            SailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val screen = currentScreen) {
                        is Screen.SailHome -> {
                            SailHomeScreen(modifier = Modifier.padding(innerPadding))
                        }
                        is Screen.MainMenu -> {
                            val menuItem = menuItems.getOrNull(selectedMenuIndex)
                            menuItem?.let { HomeScreen(modifier = Modifier.padding(innerPadding), menuItem = it) }
                        }
                        is Screen.SubMenu -> {
                            SubMenuScreen(modifier = Modifier.padding(innerPadding), menu = screen.parentMenu, selectedIndex = selectedSubMenuIndex)
                        }
                    }
                }
            }
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    is Screen.SubMenu -> {
                        currentScreen = Screen.MainMenu
                        selectedSubMenuIndex = 0
                        playBackgroundMusicForCurrentMenu()
                    }
                    is Screen.MainMenu -> {
                        currentScreen = Screen.SailHome
                        backgroundMusicPlayer?.stop()
                    }
                    else -> finish()
                }
            }
        })
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

    private fun setupMenuItems() {
        val sleepSubMenu = listOf(SubMenuItem("Start Sleep"), SubMenuItem("Stories"), SubMenuItem("Soundscapes"), SubMenuItem("Sleep Guidance"))

        menuItems = listOf(
            MenuItem("Sleep", listOf(Color(0xFF0d3b66), Color(0xFF1a4b7c), Color(0xFF285c93)),
                getRawResourceId(audioManifest.menu_background_music["sleep"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["sleep"]),
                sleepSubMenu, getRawResourceId(audioManifest.submenu_background_music["sleep"])),
            MenuItem("Calm", listOf(Color(0xFF3b8d99), Color(0xFF4eb0c4), Color(0xFF63cbe0)),
                getRawResourceId(audioManifest.menu_background_music["calm"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["calm"]),
                subMenuMusicResId = getRawResourceId(audioManifest.submenu_background_music["calm"])),
            MenuItem("Night Wake", listOf(Color(0xFF1e2a3a), Color(0xFF2a394f), Color(0xFF364864)),
                getRawResourceId(audioManifest.menu_background_music["night_wake"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["night_wake"]),
                subMenuMusicResId = getRawResourceId(audioManifest.submenu_background_music["night_wake"])),
            MenuItem("Breathe", listOf(Color(0xFF78a3a5), Color(0xFF90babf), Color(0xFFa9d1d8)),
                getRawResourceId(audioManifest.menu_background_music["breathe"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["breathe"]),
                subMenuMusicResId = getRawResourceId(audioManifest.submenu_background_music["breathe"])),
            MenuItem("Awake", listOf(Color(0xFFf4a261), Color(0xFFe76f51), Color(0xFFd94e3d)),
                getRawResourceId(audioManifest.menu_background_music["awake"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["awake"]),
                subMenuMusicResId = getRawResourceId(audioManifest.submenu_background_music["awake"])),
            MenuItem("Library", listOf(Color(0xFF6d6875), Color(0xFF8b8593), Color(0xFFa9a2b1)),
                getRawResourceId(audioManifest.menu_background_music["library"]),
                getRawResourceId(audioManifest.menu_highlight_sounds["library"]),
                subMenuMusicResId = getRawResourceId(audioManifest.submenu_background_music["library"]))
        )
    }

    private fun getRawResourceId(resNameWithExtension: String?): Int {
        if (resNameWithExtension.isNullOrEmpty()) return 0
        val resName = resNameWithExtension.substringBeforeLast('.')
        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            Log.e(TAG, "Audio resource not found: '$resNameWithExtension'.")
        }
        return resId
    }

    override fun onStart() {
        super.onStart()
        if (!isStartupSequenceCompleted) {
            playStartupSequence()
        }
    }

    private fun playStartupSequence() {
        val onSequenceFinish = { isStartupSequenceCompleted = true }

        val toneResId = getRawResourceId(audioManifest.startup_tone)
        val greetingResId = getRawResourceId(audioManifest.startup_greeting)

        if (greetingResId != 0) {
            startupGreetingPlayer = MediaPlayer.create(this, greetingResId)
            startupGreetingPlayer?.setOnCompletionListener { onSequenceFinish() }
        }

        if (toneResId != 0) {
            startupTonePlayer = MediaPlayer.create(this, toneResId)
            startupTonePlayer?.setOnCompletionListener {
                startupGreetingPlayer?.start() ?: onSequenceFinish()
            }
        }

        startupTonePlayer?.start() ?: startupGreetingPlayer?.start() ?: onSequenceFinish()
    }

    override fun onStop() {
        super.onStop()
        releaseMediaPlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayers()
    }

    private fun releaseMediaPlayers() {
        startupTonePlayer?.release()
        startupTonePlayer = null
        startupGreetingPlayer?.release()
        startupGreetingPlayer = null
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null
        highlightSoundPlayer?.release()
        highlightSoundPlayer = null
    }

    private fun playBackgroundMusicForCurrentMenu() {
        backgroundMusicPlayer?.release()
        backgroundMusicPlayer = null

        val resId = when(val screen = currentScreen) {
            is Screen.MainMenu -> {
                Log.d(TAG, "playBackgroundMusic: Screen is MainMenu, index: $selectedMenuIndex")
                menuItems.getOrNull(selectedMenuIndex)?.backgroundMusicResId
            }
            is Screen.SubMenu -> {
                Log.d(TAG, "playBackgroundMusic: Screen is SubMenu, parent: ${screen.parentMenu.name}")
                screen.parentMenu.subMenuMusicResId
            }
            else -> {
                Log.d(TAG, "playBackgroundMusic: Screen is other, no music.")
                null
            }
        }

        Log.d(TAG, "playBackgroundMusic: Final resource ID is $resId")

        if (resId != null && resId != 0) {
            Log.d(TAG, "playBackgroundMusic: Playing music.")
            backgroundMusicPlayer = MediaPlayer.create(this, resId)
            backgroundMusicPlayer?.isLooping = true
            backgroundMusicPlayer?.start()
        } else {
            Log.d(TAG, "playBackgroundMusic: No valid music resource ID. Stopping music.")
        }
    }

    private fun playHighlightSound() {
        highlightSoundPlayer?.release()
        highlightSoundPlayer = null
        val resId = menuItems.getOrNull(selectedMenuIndex)?.highlightSoundResId
        if (resId != null && resId != 0) {
            highlightSoundPlayer = MediaPlayer.create(this, resId)
            highlightSoundPlayer?.setOnCompletionListener { it.release() }
            highlightSoundPlayer?.start()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK != 0 && event.action == MotionEvent.ACTION_MOVE) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDpadEvent < 300) return true

            val yValue = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (yValue == -1.0f) { // D-pad Up
                handleDpadUp()
                lastDpadEvent = currentTime
            } else if (yValue == 1.0f) { // D-pad Down
                handleDpadDown()
                lastDpadEvent = currentTime
            }
        }
        return true
    }

    private fun handleDpadUp() {
        when (currentScreen) {
            is Screen.SailHome -> {
                currentScreen = Screen.MainMenu
                selectedMenuIndex = menuItems.lastIndex
                playHighlightSound()
                playBackgroundMusicForCurrentMenu()
            }
            is Screen.MainMenu -> {
                selectedMenuIndex = (selectedMenuIndex - 1 + menuItems.size) % menuItems.size
                playHighlightSound()
                playBackgroundMusicForCurrentMenu()
            }
            is Screen.SubMenu -> {
                val screen = currentScreen as Screen.SubMenu
                selectedSubMenuIndex = (selectedSubMenuIndex - 1 + screen.parentMenu.subMenu.size) % screen.parentMenu.subMenu.size
            }
        }
    }

    private fun handleDpadDown() {
        when (currentScreen) {
            is Screen.SailHome -> {
                currentScreen = Screen.MainMenu
                selectedMenuIndex = 0
                playHighlightSound()
                playBackgroundMusicForCurrentMenu()
            }
            is Screen.MainMenu -> {
                selectedMenuIndex = (selectedMenuIndex + 1) % menuItems.size
                playHighlightSound()
                playBackgroundMusicForCurrentMenu()
            }
            is Screen.SubMenu -> {
                val screen = currentScreen as Screen.SubMenu
                selectedSubMenuIndex = (selectedSubMenuIndex + 1) % screen.parentMenu.subMenu.size
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A && currentScreen is Screen.MainMenu) {
            val selectedMenu = menuItems[selectedMenuIndex]
            if (selectedMenu.subMenu.isNotEmpty()) {
                currentScreen = Screen.SubMenu(selectedMenu)
                playBackgroundMusicForCurrentMenu()
                val resId = getRawResourceId(audioManifest.menu_item_selected)
                if (resId != 0) MediaPlayer.create(this, resId)?.start()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun SailHomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        WaveAnimationBackground(colors = listOf(Color(0xFF87CEEB), Color(0xFF4682B4), Color(0xFF000080)), stars = emptyList())
        Text(
            text = "sail",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 100.sp,
                fontWeight = FontWeight.W300,
                color = Color.White.copy(alpha = 0.9f)
            )
        )
    }
}

@Composable
fun SubMenuScreen(modifier: Modifier = Modifier, menu: MenuItem, selectedIndex: Int) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WaveAnimationBackground(colors = menu.colors, stars = menu.stars)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            menu.subMenu.forEachIndexed { index, item ->
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.W300,
                        color = Color.White.copy(alpha = if (index == selectedIndex) 0.9f else 0.7f)
                    ),
                    fontSize = 40.sp
                )
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, menuItem: MenuItem) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        WaveAnimationBackground(colors = menuItem.colors, stars = menuItem.stars)
        Text(
            text = menuItem.name,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 100.sp,
                fontWeight = FontWeight.W300,
                color = Color.White.copy(alpha = 0.9f)
            )
        )
    }
}

@Composable
fun WaveAnimationBackground(colors: List<Color>, stars: List<StarPosition>) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 5000, easing = LinearEasing)), label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)), label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 7000, easing = LinearEasing)), label = "phase3"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw Sky Gradient
        val skyBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF000033), colors[0].copy(alpha = 0.5f)),
            startY = 0f,
            endY = height / 2f
        )
        drawRect(brush = skyBrush)

        // Draw Stars
        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size,
                center = Offset(star.x * width, star.y * height)
            )
        }

        drawWave(width, height, 50f, 0.01f, phase1, colors[0].copy(alpha = 0.5f))
        drawWave(width, height, 70f, 0.008f, phase2, colors[1].copy(alpha = 0.5f))
        drawWave(width, height, 90f, 0.006f, phase3, colors[2].copy(alpha = 0.5f))
    }
}

fun DrawScope.drawWave(width: Float, height: Float, amplitude: Float, frequency: Float, phase: Float, color: Color) {
    val waveHeight = height / 2f
    val path = Path()
    path.moveTo(0f, waveHeight)
    for (x in 0..width.toInt()) {
        path.lineTo(x.toFloat(), waveHeight + amplitude * sin(x * frequency + phase))
    }
    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    drawPath(path = path, color = color)
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    val previewMenuItem = MenuItem("Sleep", listOf(Color.Blue, Color.Cyan, Color.Gray), 0, 0, stars = (1..50).map {
        StarPosition(
            x = Random.nextFloat(),
            y = Random.nextFloat() * 0.5f, // Only in the top half
            size = Random.nextFloat() * 2f + 1f,
            alpha = Random.nextFloat() * 0.5f + 0.5f
        )
    })
    SailTheme {
        HomeScreen(menuItem = previewMenuItem)
    }
}
