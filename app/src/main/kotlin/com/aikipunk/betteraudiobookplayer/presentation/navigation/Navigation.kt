package com.aikipunk.betteraudiobookplayer.presentation.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.aikipunk.betteraudiobookplayer.presentation.ui.components.RestoreBookshelfDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.SurfaceDark
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.StartupState
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.StartupViewModel
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aikipunk.betteraudiobookplayer.presentation.ui.screen.LibraryScreen
import com.aikipunk.betteraudiobookplayer.presentation.ui.screen.PlaybackScreen
import com.aikipunk.betteraudiobookplayer.presentation.viewmodel.PlaybackViewModel

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Playback : Screen("playback/{audiobookId}?autoplay={autoplay}") {
        fun createRoute(audiobookId: String, autoplay: Boolean = false) =
            "playback/$audiobookId?autoplay=$autoplay"
    }
    object EmptyPlayback : Screen("playback/empty")
}

/**
 * The player is always the root screen. The Library is pushed on top of it,
 * so system back (or the Library's close button) always returns to the player.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    context: Context? = null,
    voicePlayRequests: VoicePlayRequests? = null,
) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    val restoredBookId = (startup as? StartupState.Ready)?.audiobookId
    if (startup is StartupState.Loading) {
        // Brief blank frame while the last session is read, avoiding an empty-player flash
        Box(modifier = modifier.fillMaxSize().background(SurfaceDark))
        return
    }

    val navController = rememberNavController()
    val restorableBooks = (startup as? StartupState.Ready)?.restorableBooks ?: 0
    var restoreAnswered by rememberSaveable { mutableStateOf(false) }

    val openLibrary: () -> Unit = {
        navController.navigate(Screen.Library.route) {
            launchSingleTop = true
        }
    }

    if (restorableBooks > 0 && !restoreAnswered) {
        RestoreBookshelfDialog(
            bookCount = restorableBooks,
            onRestore = {
                restoreAnswered = true
                openLibrary()
            },
            onNotNow = { restoreAnswered = true },
            onStartFresh = {
                restoreAnswered = true
                startupViewModel.discardRestore()
            },
        )
    }

    // "Play <book> on Better Audiobook Player": open that book in the player and start it
    val voiceBookId = voicePlayRequests?.pendingBookId?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(voiceBookId) {
        val bookId = voiceBookId ?: return@LaunchedEffect
        // No launchSingleTop: a fresh player entry makes autoplay run even if this book is already open
        navController.navigate(Screen.Playback.createRoute(bookId, autoplay = true)) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
        voicePlayRequests?.handled(bookId)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.EmptyPlayback.route,
        modifier = modifier,
    ) {
        // Root player: reopens the most recently played book, or is empty if there is none
        composable(Screen.EmptyPlayback.route) {
            if (restoredBookId == null) {
                PlaybackScreen(
                    audiobookId = null,
                    onNavigateToLibrary = openLibrary,
                )
            } else {
                val viewModel: PlaybackViewModel = hiltViewModel()
                PlaybackScreen(
                    viewModel = viewModel,
                    audiobookId = restoredBookId,
                    onNavigateToLibrary = openLibrary,
                )
            }
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onAudiobookSelected = { audiobookId, autoplay ->
                    // Replace the whole stack so the selected book's player becomes the new root
                    navController.navigate(Screen.Playback.createRoute(audiobookId, autoplay)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            Screen.Playback.route,
            arguments = listOf(
                navArgument("audiobookId") { type = NavType.StringType },
                navArgument("autoplay") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val audiobookId = backStackEntry.arguments?.getString("audiobookId")
            val autoplay = backStackEntry.arguments?.getBoolean("autoplay") ?: false
            if (audiobookId.isNullOrEmpty()) {
                PlaybackScreen(
                    audiobookId = null,
                    onNavigateToLibrary = openLibrary,
                )
            } else {
                val viewModel: PlaybackViewModel = hiltViewModel()
                PlaybackScreen(
                    viewModel = viewModel,
                    audiobookId = audiobookId,
                    autoplay = autoplay,
                    onNavigateToLibrary = openLibrary,
                )
            }
        }
    }
}
