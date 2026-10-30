package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType

class Converters {
  @TypeConverter
  fun fromMediaType(value: MediaType): String = value.name

  @TypeConverter
  fun toMediaType(value: String): MediaType =
    runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.VIDEO)

  @TypeConverter
  fun fromTrackType(value: TrackType): String = value.name

  @TypeConverter
  fun toTrackType(value: String): TrackType =
    runCatching { TrackType.valueOf(value) }.getOrDefault(TrackType.VIDEO)

  @TypeConverter
  fun fromTransitionType(value: TransitionType): String = value.name

  @TypeConverter
  fun toTransitionType(value: String): TransitionType =
    runCatching { TransitionType.valueOf(value) }.getOrDefault(TransitionType.NONE)
}
