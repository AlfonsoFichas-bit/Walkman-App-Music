package com.buga.walkman.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        QueueItemEntity::class,
        PlayHistoryEntity::class,
        SelectedFolder::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun queueDao(): QueueDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun folderDao(): FolderDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`songId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`albumId` INTEGER NOT NULL, " +
                        "`artistId` INTEGER NOT NULL, " +
                        "`duration` INTEGER NOT NULL, " +
                        "`trackNumber` INTEGER NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `songId`))"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders_new` (" +
                        "`treeUri` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`relativePath` TEXT, " +
                        "`absolutePath` TEXT, " +
                        "PRIMARY KEY(`treeUri`))"
                )
                db.execSQL(
                    "INSERT INTO `folders_new` (`treeUri`, `displayName`, `relativePath`, `absolutePath`) " +
                        "SELECT `treeUri`, `displayName`, `relativePath`, `absolutePath` FROM `folders`"
                )
                db.execSQL("DROP TABLE `folders`")
                db.execSQL("ALTER TABLE `folders_new` RENAME TO `folders`")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "walkman.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}