package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TimelineClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineClipDao {
  @Query(
    "SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, orderIndex ASC, id ASC"
  )
  fun observeClipsForProject(projectId: Long): Flow<List<TimelineClipEntity>>

  @Query(
    "SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, orderIndex ASC, id ASC"
  )
  suspend fun getClipsForProject(projectId: Long): List<TimelineClipEntity>

  @Query("SELECT * FROM timeline_clips WHERE id = :clipId LIMIT 1")
  suspend fun getClipById(clipId: Long): TimelineClipEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClip(clip: TimelineClipEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClips(clips: List<TimelineClipEntity>): List<Long>

  @Update
  suspend fun updateClip(clip: TimelineClipEntity)

  @Update
  suspend fun updateClips(clips: List<TimelineClipEntity>)

  @Query("DELETE FROM timeline_clips WHERE id = :clipId")
  suspend fun deleteClipById(clipId: Long)

  @Query("DELETE FROM timeline_clips WHERE projectId = :projectId")
  suspend fun deleteClipsForProject(projectId: Long)
}
