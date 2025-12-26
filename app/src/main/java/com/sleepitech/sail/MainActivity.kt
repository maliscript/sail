package com.sleepitech.sail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import com.sleepitech.sail.ui.theme.SailTheme
import kotlin.math.sin
import kotlin.random.Random

sealed class Screen {
    object SailHome : Screen()
    object MainMenu : Screen()
    data class SubMenu(val parentMenu: MenuItem) : Screen()
}

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var currentScreen by mutableStateOf<Screen>(Screen.SailHome)
    private var selectedMenuIndex by mutableStateOf(0)
    private var selectedSubMenuIndex by mutableStateOf(0)
    private var lastDpadEvent by mutableStateOf(0L)
    private var menuItems by mutableStateOf<List<MenuItem>>(emptyList())

    private var audioService: AudioPlaybackService? = null
    private var isServiceBound by mutableStateOf(false)
    private var isStartupSequenceCompleted by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            audioService = binder.getService()
            isServiceBound = true
            menuItems = audioService?.getMenuItems() ?: emptyList()
            if (currentScreen == Screen.SailHome && !isStartupSequenceCompleted) {
                playStartupSequence()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackButton()
        enableEdgeToEdge()

        setContent {
            SailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (menuItems.isNotEmpty()) {
                        when (val screen = currentScreen) {
                            is Screen.SailHome -> {
                                SailHomeScreen(modifier = Modifier.padding(innerPadding))
                            }

                            is Screen.MainMenu -> {
                                val menuItem = menuItems.getOrNull(selectedMenuIndex)
                                menuItem?.let {
                                    HomeScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        menuItem = it
                                    )
                                }
                            }

                            is Screen.SubMenu -> {
                                SubMenuScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    menu = screen.parentMenu,
                                    selectedIndex = selectedSubMenuIndex
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioPlaybackService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
            audioService?.stopBackgroundMusic()
        }
    }

    private fun playStartupSequence() {
        if (!isStartupSequenceCompleted) {
            audioService?.playStartupSequence {
                isStartupSequenceCompleted = true
                audioService?.playMainMenuMusic()
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
                    }

                    else -> if (!isChangingConfigurations) finish()
                }
            }
        })
    }

    private fun playBackgroundMusicForCurrentMenu() {
        val musicName = when (val screen = currentScreen) {
            is Screen.MainMenu -> menuItems.getOrNull(selectedMenuIndex)?.backgroundMusic
            is Screen.SubMenu -> screen.parentMenu.subMenuMusic
            else -> null
        }
        audioService?.playBackgroundMusic(musicName)
    }

    private fun playHighlightSound() {
        val soundName = menuItems.getOrNull(selectedMenuIndex)?.highlightSound
        audioService?.playHighlightSound(soundName)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK != 0 && event.action == MotionEvent.ACTION_MOVE) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDpadEvent < 300) return true

            val yValue = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (yValue < -0.5f) { // D-pad Up
                handleDpadUp()
                lastDpadEvent = currentTime
                return true
            } else if (yValue > 0.5f) { // D-pad Down
                handleDpadDown()
                lastDpadEvent = currentTime
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun handleDpadUp() {
        if (menuItems.isEmpty()) return
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
        if (menuItems.isEmpty()) return
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
        if (menuItems.isEmpty()) return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            handleDpadUp()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            handleDpadDown()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_A && currentScreen is Screen.MainMenu) {
            val selectedMenu = menuItems[selectedMenuIndex]
            if (selectedMenu.subMenu.isNotEmpty()) {
                currentScreen = Screen.SubMenu(selectedMenu)
                playBackgroundMusicForCurrentMenu()
                audioService?.playMenuSelectedSound()
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

        val skyBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF000033), colors.getOrElse(0) { Color.Black }.copy(alpha = 0.5f)),
            startY = 0f,
            endY = height / 2f
        )
        drawRect(brush = skyBrush)

        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.size,
                center = Offset(star.x * width, star.y * height)
            )
        }

        drawWave(width, height, 50f, 0.01f, phase1, colors.getOrElse(0) { Color.Black }.copy(alpha = 0.5f))
        drawWave(width, height, 70f, 0.008f, phase2, colors.getOrElse(1) { Color.Black }.copy(alpha = 0.5f))
        drawWave(width, height, 90f, 0.006f, phase3, colors.getOrElse(2) { Color.Black }.copy(alpha = 0.5f))
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
    val previewMenuItem = MenuItem(
        name = "Sleep",
        colors = listOf(Color.Blue, Color.Cyan, Color.Gray),
        backgroundMusic = null,
        highlightSound = null,
        stars = (1..50).map {
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
