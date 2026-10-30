package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.PlayNexaDatabase
import com.example.data.local.dao.MediaAssetDao
import com.example.data.local.dao.OverlayAndKeyframeDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.TimelineClipDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun providePlayNexaDatabase(
    @ApplicationContext context: Context
  ): PlayNexaDatabase {
    return Room.databaseBuilder(
      context,
      PlayNexaDatabase::class.java,
      "play_nexa_studio.db"
    )
      .fallbackToDestructiveMigration(true)
      .build()
  }

  @Provides
  fun provideProjectDao(database: PlayNexaDatabase): ProjectDao =
    database.projectDao()

  @Provides
  fun provideMediaAssetDao(database: PlayNexaDatabase): MediaAssetDao =
    database.mediaAssetDao()

  @Provides
  fun provideTimelineClipDao(database: PlayNexaDatabase): TimelineClipDao =
    database.timelineClipDao()

  @Provides
  fun provideOverlayAndKeyframeDao(database: PlayNexaDatabase): OverlayAndKeyframeDao =
    database.overlayAndKeyframeDao()
}
