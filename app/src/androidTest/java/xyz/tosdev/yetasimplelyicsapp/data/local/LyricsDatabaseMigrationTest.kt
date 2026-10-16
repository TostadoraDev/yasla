package xyz.tosdev.yetasimplelyicsapp.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LyricsDatabaseMigrationTest {
    @Test
    fun migration2To6KeepsLinesAndAddsAllDefaults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "amll-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val schema = instrumentation.context.assets.open(
            "xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase/2.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

        try {
            // Construct the real exported v2 schema using Android's JSON/SQLite APIs.
            // Opening it below makes Room itself run and validate the production migration.
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { oldDatabase ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    oldDatabase.execSQL(
                        entity.getString("createSql").replace("\${TABLE_NAME}", table)
                    )
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) {
                        for (entry in 0 until indices.length()) {
                            oldDatabase.execSQL(
                                indices.getJSONObject(entry).getString("createSql")
                                    .replace("\${TABLE_NAME}", table),
                            )
                        }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) oldDatabase.execSQL(setup.getString(index))
                oldDatabase.version = 2
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_documents (
                        documentId, trackKey, providerId, remoteId, attribution, contentRights,
                        syncType, languageTag, capabilities, schemaVersion, cachedAtEpochMs,
                        expiresAtEpochMs, title, artists, album, durationMs, isPinned,
                        lastAccessedAtEpochMs
                    ) VALUES (
                        'document', 'track', 'local', NULL, NULL, NULL,
                        'LINE', 'en', 'LINE_SYNC', 1, 10, 20, 'Title', 'Artist',
                        NULL, 1000, 0, 10
                    )
                    """.trimIndent(),
                )
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_lines (
                        documentId, lineId, position, originalText, startTimeMs, endTimeMs,
                        agentId, groupId, timingQuality
                    ) VALUES (
                        'document', 'line', 0, 'Hello', 0, 1000, 'v1', 'group', 'EXPLICIT'
                    )
                    """.trimIndent(),
                )
            }

            val migrated = Room.databaseBuilder(context, LyricsDatabase::class.java, databaseName)
                .addMigrations(
                    LyricsDatabase.MIGRATION_2_3,
                    LyricsDatabase.MIGRATION_3_4,
                    LyricsDatabase.MIGRATION_4_5,
                    LyricsDatabase.MIGRATION_5_6,
                )
                .build()
            try {
                val database = migrated.openHelper.writableDatabase
                assertEquals(6, database.version)
                database.query(
                    """
                    SELECT originalText, isBackground, alignment, role, region, textDirection
                    FROM lyrics_lines WHERE lineId = 'line'
                    """.trimIndent(),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Hello", cursor.getString(0))
                    assertEquals(0, cursor.getInt(1))
                    assertEquals("AUTO", cursor.getString(2))
                    assertTrue(cursor.isNull(3))
                    assertTrue(cursor.isNull(4))
                    assertEquals("AUTO", cursor.getString(5))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migration3To6KeepsDocumentsAndAddsPresentationColumns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "artwork-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val schema = instrumentation.context.assets.open(
            "xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase/3.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

        try {
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { oldDatabase ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    oldDatabase.execSQL(
                        entity.getString("createSql").replace("\${TABLE_NAME}", table)
                    )
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) {
                        for (entry in 0 until indices.length()) {
                            oldDatabase.execSQL(
                                indices.getJSONObject(entry).getString("createSql")
                                    .replace("\${TABLE_NAME}", table),
                            )
                        }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) oldDatabase.execSQL(setup.getString(index))
                oldDatabase.version = 3
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_documents (
                        documentId, trackKey, providerId, remoteId, attribution, contentRights,
                        syncType, languageTag, capabilities, schemaVersion, cachedAtEpochMs,
                        expiresAtEpochMs, title, artists, album, durationMs, isPinned,
                        lastAccessedAtEpochMs
                    ) VALUES (
                        'document', 'track', 'local', NULL, NULL, NULL,
                        'LINE', 'en', 'LINE_SYNC', 1, 10, 20, 'Title', 'Artist',
                        'Album', 1000, 1, 10
                    )
                    """.trimIndent(),
                )
            }

            val migrated = Room.databaseBuilder(context, LyricsDatabase::class.java, databaseName)
                .addMigrations(
                    LyricsDatabase.MIGRATION_3_4,
                    LyricsDatabase.MIGRATION_4_5,
                    LyricsDatabase.MIGRATION_5_6,
                )
                .build()
            try {
                val database = migrated.openHelper.writableDatabase
                assertEquals(6, database.version)
                database.query(
                    "SELECT title, artworkUri FROM lyrics_documents WHERE trackKey = 'track'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Title", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migration4To6KeepsLinesAndAddsPresentationDefaults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "presentation-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val schema = instrumentation.context.assets.open(
            "xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase/4.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

        try {
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { oldDatabase ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    oldDatabase.execSQL(
                        entity.getString("createSql").replace("\${TABLE_NAME}", table)
                    )
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) {
                        for (entry in 0 until indices.length()) {
                            oldDatabase.execSQL(
                                indices.getJSONObject(entry).getString("createSql")
                                    .replace("\${TABLE_NAME}", table),
                            )
                        }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) oldDatabase.execSQL(setup.getString(index))
                oldDatabase.version = 4
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_documents (
                        documentId, trackKey, providerId, remoteId, attribution, contentRights,
                        syncType, languageTag, capabilities, schemaVersion, cachedAtEpochMs,
                        expiresAtEpochMs, title, artists, album, durationMs, artworkUri, isPinned,
                        lastAccessedAtEpochMs
                    ) VALUES (
                        'document', 'track', 'local', NULL, NULL, NULL,
                        'LINE', 'en', 'LINE_SYNC', 1, 10, 20, 'Title', 'Artist',
                        NULL, 1000, NULL, 0, 10
                    )
                    """.trimIndent(),
                )
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_lines (
                        documentId, lineId, position, originalText, startTimeMs, endTimeMs,
                        agentId, groupId, timingQuality, isBackground
                    ) VALUES (
                        'document', 'line', 0, 'Hello', 0, 1000,
                        'v1', 'group', 'EXPLICIT', 0
                    )
                    """.trimIndent(),
                )
            }

            val migrated = Room.databaseBuilder(context, LyricsDatabase::class.java, databaseName)
                .addMigrations(
                    LyricsDatabase.MIGRATION_4_5,
                    LyricsDatabase.MIGRATION_5_6,
                )
                .build()
            try {
                val database = migrated.openHelper.writableDatabase
                assertEquals(6, database.version)
                database.query(
                    """
                    SELECT originalText, alignment, role, region, textDirection
                    FROM lyrics_lines WHERE lineId = 'line'
                    """.trimIndent(),
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Hello", cursor.getString(0))
                    assertEquals("AUTO", cursor.getString(1))
                    assertTrue(cursor.isNull(2))
                    assertTrue(cursor.isNull(3))
                    assertEquals("AUTO", cursor.getString(4))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migration5To6CreatesOrderedWordSegmentsWithCascade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val databaseName = "word-segment-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        val schema = instrumentation.context.assets.open(
            "xyz.tosdev.yetasimplelyicsapp.data.local.LyricsDatabase/5.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }

        try {
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { oldDatabase ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    oldDatabase.execSQL(
                        entity.getString("createSql").replace("\${TABLE_NAME}", table),
                    )
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) {
                        for (entry in 0 until indices.length()) {
                            oldDatabase.execSQL(
                                indices.getJSONObject(entry).getString("createSql")
                                    .replace("\${TABLE_NAME}", table),
                            )
                        }
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) oldDatabase.execSQL(setup.getString(index))
                oldDatabase.version = 5
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_documents (
                        documentId, trackKey, providerId, remoteId, attribution, contentRights,
                        syncType, languageTag, capabilities, schemaVersion, cachedAtEpochMs,
                        expiresAtEpochMs, title, artists, album, durationMs, artworkUri, isPinned,
                        lastAccessedAtEpochMs
                    ) VALUES (
                        'document', 'track', 'local', NULL, NULL, NULL,
                        'WORD', 'es', 'WORD_SYNC', 1, 10, 20, 'Title', 'Artist',
                        NULL, 1000, NULL, 0, 10
                    )
                    """.trimIndent(),
                )
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_lines (
                        documentId, lineId, position, originalText, startTimeMs, endTimeMs,
                        agentId, groupId, alignment, role, region, textDirection,
                        timingQuality, isBackground
                    ) VALUES (
                        'document', 'line', 0, 'Hola', 0, 1000,
                        NULL, NULL, 'AUTO', NULL, NULL, 'AUTO', 'EXPLICIT', 0
                    )
                    """.trimIndent(),
                )
                oldDatabase.execSQL(
                    """
                    INSERT INTO lyrics_words (
                        documentId, lineId, wordId, position, text,
                        startTimeMs, endTimeMs, timingQuality
                    ) VALUES (
                        'document', 'line', 'word', 0, 'Hola', 0, 1000, 'EXPLICIT'
                    )
                    """.trimIndent(),
                )
            }

            val migrated = Room.databaseBuilder(context, LyricsDatabase::class.java, databaseName)
                .addMigrations(LyricsDatabase.MIGRATION_5_6)
                .build()
            try {
                val database = migrated.openHelper.writableDatabase
                assertEquals(6, database.version)
                database.query(
                    "SELECT COUNT(*) FROM lyrics_word_segments WHERE documentId = 'document'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }

                database.execSQL(
                    """
                    INSERT INTO lyrics_word_segments (
                        documentId, lineId, wordId, position, text,
                        startTimeMs, endTimeMs, timingQuality
                    ) VALUES (
                        'document', 'line', 'word', 1, 'la', 400, 1000, 'EXPLICIT'
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO lyrics_word_segments (
                        documentId, lineId, wordId, position, text,
                        startTimeMs, endTimeMs, timingQuality
                    ) VALUES (
                        'document', 'line', 'word', 0, 'Ho', 0, 400, 'EXPLICIT'
                    )
                    """.trimIndent(),
                )

                database.query(
                    """
                    SELECT text FROM lyrics_word_segments
                    WHERE documentId = 'document'
                    ORDER BY lineId, wordId, position
                    """.trimIndent(),
                ).use { cursor ->
                    val texts = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                    assertEquals(listOf("Ho", "la"), texts)
                }
                database.execSQL("DELETE FROM lyrics_words WHERE wordId = 'word'")
                database.query(
                    "SELECT COUNT(*) FROM lyrics_word_segments WHERE documentId = 'document'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
