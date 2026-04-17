package com.smssequencer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Sequence::class, SmsStep::class, Contact::class, SendLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sequenceDao(): SequenceDao
    abstract fun smsStepDao(): SmsStepDao
    abstract fun contactDao(): ContactDao
    abstract fun sendLogDao(): SendLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "sms_sequencer.db")
                    .build().also { INSTANCE = it }
            }
    }
}
