package com.aikipunk.betteraudiobookplayer.domain.about

/** Who makes the app and the terms it's released under. */
object AppInfo {
    const val DEVELOPER = "Aikipunk Creations"
    const val COPYRIGHT_YEAR = 2026
    const val COPYRIGHT = "© $COPYRIGHT_YEAR $DEVELOPER. All rights reserved."

    val LICENSE_TERMS = listOf(
        "Better Audiobook Player is proprietary software owned by $DEVELOPER.",
        "You may install and use the app on devices you own or control for your own listening. " +
            "You may not copy, modify, distribute, sell or rent the app, or reverse engineer it " +
            "except where the law expressly allows.",
        "Your audiobooks stay yours: the app only reads the files and folders you choose, and keeps " +
            "your progress, bookmarks and notes on your device.",
        "The app is provided \"as is\", without warranties of any kind. To the extent the law allows, " +
            "$DEVELOPER isn't liable for any damages arising from its use.",
        "The app includes open-source software, used under the licenses listed in " +
            "Open-source licenses. Those licenses govern those components.",
    )

    val CREDITS = listOf(
        "Feature inspiration: u/GoodNewsEveryone (Reddit) for gradual volume fade.",
    )
}
