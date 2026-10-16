package xyz.tosdev.yetasimplelyicsapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LyricsDocumentEntity::class,
        LyricsLineEntity::class,
        LyricsWordEntity::class,
        LyricsWordSegmentEntity::class,
        LyricsTextVariantEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN title TEXT")
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN artists TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN durationMs INTEGER")
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN lastAccessedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_documents_isPinned ON lyrics_documents(isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_documents_lastAccessedAtEpochMs ON lyrics_documents(lastAccessedAtEpochMs)")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lyrics_lines ADD COLUMN isBackground INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lyrics_documents ADD COLUMN artworkUri TEXT")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lyrics_lines ADD COLUMN alignment TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE lyrics_lines ADD COLUMN role TEXT")
                db.execSQL("ALTER TABLE lyrics_lines ADD COLUMN region TEXT")
                db.execSQL("ALTER TABLE lyrics_lines ADD COLUMN textDirection TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lyrics_word_segments` (
                        `documentId` TEXT NOT NULL,
                        `lineId` TEXT NOT NULL,
                        `wordId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `startTimeMs` INTEGER,
                        `endTimeMs` INTEGER,
                        `timingQuality` TEXT NOT NULL,
                        PRIMARY KEY(`documentId`, `lineId`, `wordId`, `position`),
                        FOREIGN KEY(`documentId`, `lineId`, `wordId`)
                            REFERENCES `lyrics_words`(`documentId`, `lineId`, `wordId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_lyrics_word_segments_documentId_lineId_wordId` " +
                        "ON `lyrics_word_segments` (`documentId`, `lineId`, `wordId`)",
                )
            }
        }

        @Volatile
        private var instance: LyricsDatabase? = null

        fun getInstance(context: Context): LyricsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                    .build()
                    .also { instance = it }
            }
    }
}
