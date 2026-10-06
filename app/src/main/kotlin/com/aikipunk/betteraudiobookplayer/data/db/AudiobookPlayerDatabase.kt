package com.aikipunk.betteraudiobookplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.aikipunk.betteraudiobookplayer.data.dao.AudiobookDao
import com.aikipunk.betteraudiobookplayer.data.dao.BookmarkDao
import com.aikipunk.betteraudiobookplayer.data.dao.PlaybackStateDao
import com.aikipunk.betteraudiobookplayer.data.dao.TrackDao
import com.aikipunk.betteraudiobookplayer.data.entity.AudiobookEntity
import com.aikipunk.betteraudiobookplayer.data.entity.BookmarkEntity
import com.aikipunk.betteraudiobookplayer.data.entity.PlaybackStateEntity
import com.aikipunk.betteraudiobookplayer.data.entity.TrackEntity

@Database(
    entities = [
        AudiobookEntity::class,
        TrackEntity::class,
        PlaybackStateEntity::class,
        BookmarkEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(LocalDateTimeConverter::class)
abstract class AudiobookPlayerDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN notes TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN customTitle TEXT")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN narrator TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN publisher TEXT")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN publishedYear INTEGER")
            }
        }
    }

    abstract fun audiobookDao(): AudiobookDao
    abstract fun trackDao(): TrackDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun bookmarkDao(): BookmarkDao
}
