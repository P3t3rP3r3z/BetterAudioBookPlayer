package com.aikipunk.betteraudiobookplayer.data.transfer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.aikipunk.betteraudiobookplayer.domain.transfer.ShelfMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists audio files in the phone's media index (needs the audio read permission). */
@Singleton
class AudioFileFinder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun allAudioFiles(): List<ShelfMatcher.AudioFile> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) queryScoped() else queryLegacy()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryScoped(): List<ShelfMatcher.AudioFile> {
        val projection = arrayOf(
            MediaStore.Audio.Media.VOLUME_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ALBUM,
        )
        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val volume = c.getString(0) ?: continue
                    val path = c.getString(1) ?: continue
                    val name = c.getString(2) ?: continue
                    add(ShelfMatcher.AudioFile(volume, path.trimEnd('/'), name, c.getString(3)))
                }
            }
        }.orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun queryLegacy(): List<ShelfMatcher.AudioFile> {
        val projection = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM)
        return context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val data = c.getString(0) ?: continue
                        val (volume, relative) = splitLegacyPath(data) ?: continue
                        add(
                            ShelfMatcher.AudioFile(
                                volume = volume,
                                folderPath = relative.substringBeforeLast('/', ""),
                                fileName = relative.substringAfterLast('/'),
                                album = c.getString(1),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    /** "/storage/emulated/0/Books/a.mp3" → ("external_primary", "Books/a.mp3"). */
    private fun splitLegacyPath(path: String): Pair<String, String>? {
        val primary = "/storage/emulated/0/"
        if (path.startsWith(primary)) return PRIMARY_VOLUME to path.removePrefix(primary)
        val rest = path.removePrefix("/storage/").takeIf { it != path } ?: return null
        val volume = rest.substringBefore('/')
        return volume.lowercase() to rest.substringAfter('/')
    }

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /** Where the folder picker should open to show [candidate]. */
        fun folderPickerUri(candidate: ShelfMatcher.FolderCandidate): Uri {
            val root = if (candidate.volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary" else candidate.volume.uppercase()
            return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "$root:${candidate.folderPath}")
        }
    }
}

// MediaStore.VOLUME_EXTERNAL_PRIMARY's value; the constant itself is API 29+
private const val PRIMARY_VOLUME = "external_primary"
