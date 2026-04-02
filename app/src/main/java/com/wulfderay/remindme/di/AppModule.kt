package com.wulfderay.remindme.di

import android.content.Context
import androidx.room.Room
import com.wulfderay.remindme.alarm.AlarmScheduler
import com.wulfderay.remindme.alarm.AlarmSchedulerImpl
import com.wulfderay.remindme.data.AppDatabase
import com.wulfderay.remindme.data.MIGRATION_1_2
import com.wulfderay.remindme.data.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing app-wide singleton dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "remindme_database"
        ).addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler {
        return AlarmSchedulerImpl(context)
    }
}
