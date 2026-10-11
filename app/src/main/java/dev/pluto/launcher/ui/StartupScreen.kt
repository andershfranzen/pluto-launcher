package dev.pluto.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.R
import kotlinx.coroutines.delay

/** Matches Android's unbacked 288 dp splash icon, including its transparent safe area. */
@Composable
internal fun StartupScreen(reducedMotion: Boolean) {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        showProgress = true
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .semantics { contentDescription = "Pluto is starting" },
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(288.dp).align(Alignment.Center),
        )
        if (showProgress) {
            val progressModifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 48.dp).size(width = 56.dp, height = 2.dp)
                .semantics { contentDescription = "Loading apps" }
            if (reducedMotion) {
                Box(progressModifier.background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.size(width = 20.dp, height = 2.dp)
                        .background(Color.White.copy(alpha = 0.5f)))
                }
            } else {
                LinearProgressIndicator(
                    modifier = progressModifier,
                    color = Color.White.copy(alpha = 0.5f),
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}
