package com.aikipunk.betteraudiobookplayer.presentation.ui.screen

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikipunk.betteraudiobookplayer.data.licenses.OpenSourceLicenses
import com.aikipunk.betteraudiobookplayer.domain.about.AppInfo
import com.aikipunk.betteraudiobookplayer.domain.about.OpenSourceLibrary
import com.aikipunk.betteraudiobookplayer.domain.about.OpenSourceLicense
import com.aikipunk.betteraudiobookplayer.presentation.ui.theme.*

/** Full-screen credits for every open-source library the app ships with. */
@Composable
fun OpenSourceLicensesContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val libraries by produceState<List<OpenSourceLibrary>?>(initialValue = null) {
        value = OpenSourceLicenses.load(context.applicationContext)
    }
    BackHandler(onBack = onBack)
    OpenSourceLicensesList(libraries = libraries, onBack = onBack, modifier = modifier)
}

@Composable
fun OpenSourceLicensesList(
    libraries: List<OpenSourceLibrary>?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings", tint = TextPrimary)
            }
            Text(
                text = "Open-source licenses",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (libraries == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Text(
                    "Better Audiobook Player is made by ${AppInfo.DEVELOPER} with the help of these " +
                        "open-source libraries. Thank you to their authors. Tap one to read its license.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            items(libraries, key = { it.id }) { library ->
                LibraryRow(library = library, onClick = { openId = library.id })
                HorizontalDivider(color = SurfaceContainer)
            }
        }
    }

    libraries?.firstOrNull { it.id == openId }?.let { library ->
        LicenseTextDialog(library = library, onDismiss = { openId = null })
    }
}

@Composable
private fun LibraryRow(library: OpenSourceLibrary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Read license", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(library.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val details = listOfNotNull(
            library.author,
            library.version?.let { "v$it" },
            library.licenses.joinToString { it.name }.ifEmpty { null },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LicenseTextDialog(library: OpenSourceLibrary, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = { Text(library.name, color = TextPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                library.licenses.forEach { license ->
                    Text(license.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        license.text?.trim() ?: license.url ?: "License text unavailable.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                if (library.licenses.isEmpty()) {
                    Text("No license information was published for this library.", color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TerracottaPrimary) }
        },
        dismissButton = library.website?.let { url ->
            {
                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri(url)
                        } catch (_: ActivityNotFoundException) {
                            // No browser on the device; the license text is still shown
                        }
                    },
                ) { Text("Website", color = TerracottaPrimary) }
            }
        },
    )
}

/** The app's own terms, shown from Settings › About. */
@Composable
fun AppLicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLow,
        title = { Text("License", color = TextPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(AppInfo.COPYRIGHT, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                AppInfo.LICENSE_TERMS.forEach { Text(it, color = TextSecondary, fontSize = 14.sp) }
                if (AppInfo.CREDITS.isNotEmpty()) {
                    HorizontalDivider(color = SurfaceContainer, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Credits", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    AppInfo.CREDITS.forEach { Text(it, color = TextSecondary, fontSize = 14.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TerracottaPrimary) }
        },
    )
}

private val previewLibraries = listOf(
    OpenSourceLibrary(
        id = "androidx.media3:media3-exoplayer",
        name = "ExoPlayer module",
        version = "1.4.1",
        author = "The Android Open Source Project",
        website = "https://developer.android.com/media/media3",
        licenses = listOf(OpenSourceLicense("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0", null)),
    ),
    OpenSourceLibrary(
        id = "io.coil-kt:coil",
        name = "coil",
        version = "2.6.0",
        author = null,
        website = null,
        licenses = listOf(OpenSourceLicense("Apache License 2.0", null, null)),
    ),
)

@Preview(showBackground = true)
@Composable
private fun OpenSourceLicensesListPreview() {
    AudiobookPlayerTheme { OpenSourceLicensesList(libraries = previewLibraries, onBack = {}) }
}

@Preview(showBackground = true)
@Composable
private fun AppLicenseDialogPreview() {
    AudiobookPlayerTheme { AppLicenseDialog(onDismiss = {}) }
}
