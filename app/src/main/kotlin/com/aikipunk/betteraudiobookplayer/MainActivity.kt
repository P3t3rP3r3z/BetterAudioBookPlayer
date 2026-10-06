package com.aikipunk.betteraudiobookplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.aikipunk.betteraudiobookplayer.domain.library.VoiceSearch
import com.aikipunk.betteraudiobookplayer.presentation.navigation.VoicePlayRequests
import com.aikipunk.betteraudiobookplayer.service.MediaBrowseTree
import kotlinx.coroutines.launch
import com.aikipunk.betteraudiobookplayer.service.PlaybackServiceManager
import javax.inject.Inject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.aikipunk.betteraudiobookplayer.data.preferences.UserPreferences
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.LocalAppColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aikipunk.betteraudiobookplayer.presentation.navigation.AppNavigation
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.AudiobookPlayerTheme
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.SurfaceDark
import dagger.hilt.android.AndroidEntryPoint

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playbackServiceManager: PlaybackServiceManager

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var mediaBrowseTree: MediaBrowseTree

    @Inject
    lateinit var voicePlayRequests: VoicePlayRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by userPreferences.themeMode.collectAsState()
            val dynamicColor by userPreferences.dynamicColor.collectAsState()
            AudiobookPlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val isDark = LocalAppColors.current.isDark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = SurfaceDark) {
                    MainAppContent(context = this@MainActivity, voicePlayRequests = voicePlayRequests)
                }
            }
        }
        // A recreated activity (rotation) already handled its launch intent
        if (savedInstanceState == null) handleVoiceSearch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceSearch(intent)
    }

    /** "Play <book> on Better Audiobook Player" from Google Assistant or Gemini. */
    private fun handleVoiceSearch(intent: Intent?) {
        val query = VoiceSearch.queryFrom(intent?.action, intent?.getStringExtra(SearchManager.QUERY)) ?: return
        lifecycleScope.launch {
            val bookId = mediaBrowseTree.bestMatchBookId(query)
            if (bookId != null) {
                voicePlayRequests.request(bookId)
            } else {
                val message = if (query.isBlank()) "No audiobooks yet" else "No audiobook matches \u201c$query\u201d"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        // Leaving the app for good (not a rotation or backgrounding) stops playback
        if (isFinishing && !isChangingConfigurations) {
            playbackServiceManager.stopPlaybackService()
        }
        super.onDestroy()
    }
}

@Composable
private fun MainAppContent(context: android.content.Context? = null, voicePlayRequests: VoicePlayRequests? = null) {
    AppNavigation(
        modifier = Modifier.fillMaxSize(),
        context = context,
        voicePlayRequests = voicePlayRequests,
    )
}

@Preview(showBackground = true)
@Composable
private fun MainAppContentPreview() {
    AudiobookPlayerTheme {
        // Preview placeholder
    }
}
