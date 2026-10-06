package com.aikipunk.betteraudiobookplayer.data.licenses

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import com.aikipunk.betteraudiobookplayer.domain.about.OpenSourceLibrary
import com.aikipunk.betteraudiobookplayer.domain.about.OpenSourceLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the dependency list the AboutLibraries Gradle plugin generates at build time. */
object OpenSourceLicenses {
    suspend fun load(context: Context): List<OpenSourceLibrary> = withContext(Dispatchers.IO) {
        val libs = Libs.Builder().withContext(context).build()
        OpenSourceLibrary.forDisplay(
            libs.libraries.map { lib ->
                OpenSourceLibrary(
                    id = lib.uniqueId,
                    name = lib.name,
                    version = lib.artifactVersion,
                    author = lib.organization?.name
                        ?: lib.developers.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.joinToString(),
                    website = lib.website ?: lib.scm?.url,
                    licenses = lib.licenses.map { OpenSourceLicense(it.name, it.url, it.licenseContent) },
                )
            },
        )
    }
}
