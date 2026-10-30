package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlayNexaDatabase
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaType
import com.example.data.local.entity.OverlayTransformEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.TextAlignOption
import com.example.data.local.entity.TimelineClipEntity
import com.example.data.local.entity.TrackType
import com.example.data.local.entity.TransitionType
import com.example.data.media.Media3EffectsFactory
import com.example.data.media.MediaMetadataHelper
import com.example.data.media.TextBitmapRenderer
import com.example.data.repository.ProjectRepositoryImpl
import com.example.domain.model.KeyframeInterpolator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
      overlayAndKeyframeDao = database.overlayAndKeyframeDao(),
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

  @Test
  fun `overlay transforms text overlays keyframe interpolation audio transitions effects and undo snapshot work end to end`() = runTest {
    val projectId = database.projectDao().insertProject(
      ProjectEntity(name = "Advanced Studio Reel", aspectRatio = AspectRatio.RATIO_9_16.rawValue)
    )
    val videoAssetId = database.mediaAssetDao().insertAsset(
      MediaAssetEntity(
        projectId = projectId,
        type = MediaType.VIDEO,
        sourceUri = "content://media/external/video/media/201",
        durationMs = 6_000L
      )
    )
    val audioAssetId = database.mediaAssetDao().insertAsset(
      MediaAssetEntity(
        projectId = projectId,
        type = MediaType.AUDIO,
        sourceUri = "content://media/external/audio/media/301",
        durationMs = 8_000L
      )
    )

    val mainClip1 = database.timelineClipDao().insertClip(
      TimelineClipEntity(
        projectId = projectId,
        mediaAssetId = videoAssetId,
        trackIndex = 0,
        trackType = TrackType.VIDEO,
        startMs = 0L,
        trimStartMs = 0L,
        trimEndMs = 4_000L,
        orderIndex = 0
      )
    )
    val mainClip2 = database.timelineClipDao().insertClip(
      TimelineClipEntity(
        projectId = projectId,
        mediaAssetId = videoAssetId,
        trackIndex = 0,
        trackType = TrackType.VIDEO,
        startMs = 4_000L,
        trimStartMs = 0L,
        trimEndMs = 4_000L,
        orderIndex = 1
      )
    )

    // 1. Add Overlay PiP clip + enable Keyframes at 0ms and 2000ms
    val overlayClipId = repository.addExistingAssetToTrack(
      projectId = projectId,
      assetId = videoAssetId,
      asOverlay = true,
      overlayStartMs = 500L,
      overlayTrackIndex = 1
    )
    assertNotNull(overlayClipId)

    repository.updateLayerTransformOrKeyframe(
      projectId = projectId,
      clipId = overlayClipId!!,
      localTimeMs = 0L,
      offsetX = -0.6f,
      offsetY = -0.4f,
      scale = 0.4f,
      rotationDeg = 0f,
      opacity = 0.2f
    )
    repository.toggleKeyframesForClip(
      projectId = projectId,
      clipId = overlayClipId,
      enabled = true,
      initialLocalTimeMs = 0L
    )
    repository.updateLayerTransformOrKeyframe(
      projectId = projectId,
      clipId = overlayClipId,
      localTimeMs = 2_000L,
      offsetX = 0.6f,
      offsetY = 0.4f,
      scale = 1.0f,
      rotationDeg = 90f,
      opacity = 1.0f
    )

    // 2. Add Styled Text Overlay clip + render bitmap with bundled Google Font
    val textClipId = repository.addTextOverlayClip(
      projectId = projectId,
      startMs = 200L,
      durationMs = 3_000L,
      content = "PLAY NEXA STUDIO",
      fontFamily = "Bebas Neue",
      fontSize = 34f,
      color = 0xFF00F0FF.toInt(),
      backgroundColor = 0xCC0A0A0A.toInt(),
      alignment = TextAlignOption.CENTER,
      isBold = true,
      isItalic = false
    )
    assertNotNull(textClipId)

    // 3. Add Audio Track clip, set 180% volume, and mute original video audio
    val audioClipId = repository.addExistingAssetToTrack(
      projectId = projectId,
      assetId = audioAssetId,
      asOverlay = false,
      overlayStartMs = 0L
    )
    assertNotNull(audioClipId)
    repository.updateClipVolume(projectId, audioClipId!!, 1.8f)
    repository.setOriginalAudioMuted(projectId, true)

    // 4. Apply Transition, Crop/Rotate/Flip, and Color Adjustments
    repository.updateClipTransition(projectId, mainClip2, TransitionType.SLIDE_LEFT, 400L)
    repository.rotateClip90(projectId, mainClip1)
    repository.toggleClipFlipHorizontal(projectId, mainClip1)
    repository.updateClipCrop(projectId, mainClip1, 0.1f, 0.1f, 0.9f, 0.9f)
    repository.updateClipColorAdjustments(projectId, mainClip1, 0.25f, 0.3f, -0.35f)

    // Verify all entities via observeTimelineClips
    val allClips = repository.observeTimelineClips(projectId).first()
    val observedOverlay = allClips.first { it.id == overlayClipId }
    assertEquals(2, observedOverlay.keyframes.size)

    // Verify linear keyframe interpolation at midpoint (1000ms)
    val midEval = KeyframeInterpolator.evaluateOverlay(
      observedOverlay.overlayTransform,
      observedOverlay.keyframes,
      1_000L
    )
    assertEquals(0.0f, midEval.offsetX, 0.01f)
    assertEquals(0.0f, midEval.offsetY, 0.01f)
    assertEquals(0.7f, midEval.scale, 0.01f)
    assertEquals(45.0f, midEval.rotationDeg, 0.1f)
    assertEquals(0.6f, midEval.opacity, 0.01f)

    // Verify TextBitmapRenderer renders valid ARGB_8888 bitmap
    val observedText = allClips.first { it.id == textClipId }
    assertNotNull(observedText.textOverlay)
    val renderedBmp = TextBitmapRenderer(context).renderTextBitmap(observedText.textOverlay!!, 720)
    assertTrue(renderedBmp.width > 32 && renderedBmp.height > 24)

    // Verify Media3 per-clip effects & transition evaluation
    val observedMain1 = allClips.first { it.id == mainClip1 }
    val perClipEffects = Media3EffectsFactory.buildPerClipVideoEffects(observedMain1)
    assertTrue("Expected Crop, ScaleAndRotate, RgbAdjustment, Contrast, HslAdjustment", perClipEffects.size >= 5)

    val mainClipsSorted = allClips
      .filter { it.trackType == TrackType.VIDEO && it.trackIndex == 0 }
      .sortedBy { it.orderIndex }
    val transAtCut = Media3EffectsFactory.evaluateTransitionAtMs(mainClipsSorted, 3_900L)
    assertTrue(transAtCut.translationFractionX < 0f)

    val observedProject = repository.observeProject(projectId).first()
    assertTrue(observedProject!!.isOriginalAudioMuted)

    // 5. Capture Undo Snapshot -> delete text & overlay clips -> restore snapshot and verify exact recovery
    val snapshotBeforeDelete = repository.captureProjectSnapshot(projectId)
    assertNotNull(snapshotBeforeDelete)

    repository.deleteClip(projectId, textClipId!!)
    repository.deleteClip(projectId, overlayClipId)
    val afterDelete = repository.observeTimelineClips(projectId).first()
    assertFalse(afterDelete.any { it.id == textClipId || it.id == overlayClipId })

    repository.restoreProjectSnapshot(snapshotBeforeDelete!!)
    val restoredClips = repository.observeTimelineClips(projectId).first()
    assertTrue(restoredClips.any { it.id == textClipId && it.textOverlay?.content == "PLAY NEXA STUDIO" })
    assertEquals(2, restoredClips.first { it.id == overlayClipId }.keyframes.size)
  }
}
