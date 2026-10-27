package org.pcbu.libreclient.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PairedDeviceEntity::class], version = 3, exportSchema = true)
abstract class PcbuDatabase : RoomDatabase() {
    abstract fun pairedDeviceDao(): PairedDeviceDao

    companion object {
        @Volatile private var instance: PcbuDatabase? = null

        /** First migration this codebase has ever needed — adds the post-unlock macro column.
         * No fallbackToDestructiveMigration() is set below, so this (or any future migration)
         * is required, not optional, for existing installs to survive a version bump. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paired_devices ADD COLUMN macroStepsJson TEXT")
            }
        }

        /** Adds the name chosen on the phone (null = default display name). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paired_devices ADD COLUMN customName TEXT")
            }
        }

        fun getInstance(context: Context): PcbuDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, PcbuDatabase::class.java, "pcbu.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
