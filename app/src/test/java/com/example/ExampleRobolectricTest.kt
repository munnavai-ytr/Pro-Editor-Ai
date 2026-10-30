package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlayNexaDatabase
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TimelineClipEntity
import com.example.data.local.entity.TrackType
import com.example.data.media.MediaMetadataHelper
import com.example.data.repository.ProjectRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ExampleRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: PlayNexaDatabase
  private lateinit var repository: ProjectRepositoryImpl

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, PlayNexaDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = ProjectRepositoryImpl(
      database = database,
      projectDao = database.projectDao(),
      mediaAssetDao = database.mediaAssetDao(),
      timelineClipDao = database.timelineClipDao(),
      metadataHelper = MediaMetadataHelper(context)
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `app name matches Play Nexa`() {
    val appName = context.getString(R.string.app_name)
    assertEquals("Play Nexa", appName)
  }

  @Test
  fun `trimming splitting speed and reordering persist accurately in Room`() = runTest {
    val projectId = database.projectDao().insertProject(
      ProjectEntity(
        name = "Test Reel",
        aspectRatio = AspectRatio.RATIO_9_16.rawValue,
        durationMs = 10_000L
      )
    )
    val assetId = database.mediaAssetDao().insertAsset(
      MediaAssetEntity(
        projectId = projectId,
        type = MediaType.VIDEO,
        sourceUri = "content://media/external/video/media/101",
        durationMs = 10_000L
      )
    )
    val clipId = database.timelineClipDao().insertClip(
      TimelineClipEntity(
        projectId = projectId,
        mediaAssetId = assetId,
        trackIndex = 0,
        trackType = TrackType.VIDEO,
        startMs = 0L,
        trimStartMs = 0L,
        trimEndMs = 10_000L,
        orderIndex = 0,
        speed = 1.0f
      )
    )

    // 1. Trim clip from 1000ms to 9000ms (8000ms total)
    repository.trimClip(
      projectId = projectId,
      clipId = clipId,
      newTrimStartMs = 1_000L,
      newTrimEndMs = 9_000L
    )

    var clips = repository.observeTimelineClips(projectId).first()
    assertEquals(1, clips.size)
    assertEquals(1_000L, clips[0].trimStartMs)
    assertEquals(9_000L, clips[0].trimEndMs)
    assertEquals(8_000L, clips[0].timelineDurationMs)

    var project = repository.observeProject(projectId).first()
    assertNotNull(project)
    assertEquals(8_000L, project!!.durationMs)

    // 2. Split clip at playhead = 3000ms -> Left [1000..4000], Right [4000..9000]
    val splitPair = repository.splitClipAtPosition(
      projectId = projectId,
      clipId = clipId,
      playheadMs = 3_000L
    )
    assertNotNull(splitPair)

    clips = repository.observeTimelineClips(projectId).first()
    assertEquals(2, clips.size)
    assertEquals(1_000L, clips[0].trimStartMs)
    assertEquals(4_000L, clips[0].trimEndMs)
    assertEquals(0L, clips[0].startMs)
    assertEquals(0, clips[0].orderIndex)

    assertEquals(4_000L, clips[1].trimStartMs)
    assertEquals(9_000L, clips[1].trimEndMs)
    assertEquals(3_000L, clips[1].startMs)
    assertEquals(1, clips[1].orderIndex)

    // 3. Change second clip speed to 2.0x -> 5000ms source becomes 2500ms timeline duration
    repository.updateClipSpeed(projectId, clips[1].id, 2.0f)
    clips = repository.observeTimelineClips(projectId).first()
    assertEquals(2_500L, clips[1].timelineDurationMs)
    project = repository.observeProject(projectId).first()
    assertEquals(5_500L, project!!.durationMs)

    // 4. Reorder second clip to index 0 -> starts at 0ms, first clip shifts to start at 2500ms
    val secondClipId = clips[1].id
    repository.reorderMainTrackClip(projectId, secondClipId, 0)
    clips = repository.observeTimelineClips(projectId).first()
    assertEquals(secondClipId, clips[0].id)
    assertEquals(0L, clips[0].startMs)
    assertEquals(2_500L, clips[1].startMs)
  }
}
