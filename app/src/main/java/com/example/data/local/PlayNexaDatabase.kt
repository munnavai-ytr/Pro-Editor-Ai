package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.dao.MediaAssetDao
import com.example.data.local.dao.OverlayAndKeyframeDao
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.TimelineClipDao
import com.example.data.local.entity.KeyframeEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextOverlayEntity
import com.example.data.local.entity.TimelineClipEntity

@Database(
  entities = [
    ProjectEntity::class,
    MediaAssetEntity::class,
    TimelineClipEntity::class,
    OverlayTransformEntity::class,
    TextOverlayEntity::class,
    KeyframeEntity::class
  ],
  version = 2,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PlayNexaDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao
  abstract fun mediaAssetDao(): MediaAssetDao
  abstract fun timelineClipDao(): TimelineClipDao
  abstract fun overlayAndKeyframeDao(): OverlayAndKeyframeDao
}
