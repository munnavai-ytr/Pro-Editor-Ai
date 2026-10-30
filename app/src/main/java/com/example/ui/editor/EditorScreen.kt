package com.example.ui.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ExportState
import com.example.ui.editor.components.EditorBottomNavigationBar
import com.example.ui.editor.components.EditorContextualPanel
import com.example.ui.editor.components.ExportSheetContent
import com.example.ui.editor.components.MultiTrackTimeline
import com.example.ui.editor.components.TextOverlayEditorDialog
import com.example.ui.editor.components.VideoPreviewStage
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextDisabled
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  viewModel: EditorViewModel,
  onNavigateBack: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val snackbarHostState = remember { SnackbarHostState() }

  var pendingOverlayTrackIdx by remember { mutableIntStateOf(1) }

  val addMainMediaLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
  ) { uris ->
    if (uris.isNotEmpty()) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      viewModel.addPickedMedia(uris, asOverlay = false)
    }
  }

  val addOverlayMediaLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      viewModel.addPickedMedia(
        uris = listOf(uri),
        asOverlay = true,
        overlayTrackIndex = pendingOverlayTrackIdx
      )
    }
  }

  val addAudioFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      viewModel.addPickedAudio(uri)
    }
  }

  LaunchedEffect(uiState.snackbarMessage) {
    uiState.snackbarMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessage()
    }
  }

  Scaffold(
    containerColor = NexaBlack,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      EditorTopBar(
        projectName = uiState.project?.name ?: "Loading…",
        aspectRatioLabel = uiState.aspectRatio.rawValue,
        canUndo = uiState.canUndo,
        canRedo = uiState.canRedo,
        onBack = {
          viewModel.pausePlayback()
          onNavigateBack()
        },
        onEditProjectName = viewModel::openRenameDialog,
        onUndo = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.undo()
        },
        onRedo = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.redo()
        },
        onToggleAspectRatio = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.cycleAspectRatio()
        },
        onExport = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.openExportSheet()
        }
      )
    },
    bottomBar = {
      Column(modifier = Modifier.fillMaxWidth()) {
        EditorContextualPanel(
          activeTab = uiState.activeTab,
          activeSubmode = uiState.activeSubmode,
          isInteractiveCropMode = uiState.isInteractiveCropMode,
          selectedClip = uiState.selectedClip,
          playheadMs = uiState.playheadMs,
          isOriginalAudioMuted = uiState.isOriginalAudioMuted,
          assets = uiState.assets,
          audioTrackClips = uiState.audioTrackClips,
          textTrackClips = uiState.textTrackClips,
          isImportingMedia = uiState.isImportingMedia,
          onSelectClip = { id -> viewModel.selectClip(id) },
          onToggleSubmode = viewModel::toggleSubmode,
          onSplitClip = viewModel::splitSelectedClipAtPlayhead,
          onSelectSpeed = viewModel::setSelectedClipSpeed,
          onLiveVolumeChange = viewModel::onLiveVolumeChange,
          onCommitVolume = viewModel::commitLiveVolume,
          onSelectPresetVolume = { preset ->
            viewModel.onLiveVolumeChange(preset)
            viewModel.commitLiveVolume()
          },
          onToggleKeyframes = viewModel::toggleSelectedClipKeyframes,
          onAddOrRemoveKeyframeAtPlayhead = viewModel::addOrRemoveKeyframeAtPlayhead,
          onLiveLayerOpacityChange = viewModel::onLiveLayerOpacityChange,
          onCommitLayerOpacity = viewModel::commitLiveCanvasTransform,
          onResetLayerTransform = viewModel::resetSelectedLayerTransform,
          onRotate90 = viewModel::rotateSelectedClip90,
          onFlipHorizontal = viewModel::flipSelectedClipHorizontal,
          onFlipVertical = viewModel::flipSelectedClipVertical,
          onToggleInteractiveCrop = viewModel::toggleInteractiveCropMode,
          onResetCropAndTransform = viewModel::resetSelectedClipCropAndTransform,
          onLiveColorChange = viewModel::onLiveColorAdjustChange,
          onCommitColorChange = viewModel::commitLiveColorAdjust,
          onResetColorAdjustments = viewModel::resetSelectedClipColorAdjustments,
          onSelectTransition = viewModel::setSelectedClipTransition,
          onOpenAddTextDialog = viewModel::openAddTextDialog,
          onOpenEditTextDialog = viewModel::openEditSelectedTextDialog,
          onUpdateSelectedTextStyle = viewModel::updateSelectedTextStyle,
          onDuplicateClip = viewModel::duplicateSelectedClip,
          onDeleteClip = viewModel::deleteSelectedClip,
          onPickMainMedia = {
            addMainMediaLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          onPickOverlayMedia = {
            pendingOverlayTrackIdx = 1
            addOverlayMediaLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
          },
          onPickAudioFile = {
            addAudioFileLauncher.launch(arrayOf("audio/*"))
          },
          onToggleOriginalAudioMute = viewModel::toggleOriginalVideoAudioMute,
          onReuseAsset = viewModel::addExistingAsset
        )

        EditorBottomNavigationBar(
          activeTab = uiState.activeTab,
          onSelectTab = viewModel::selectBottomTab
        )
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Top Half: Real-time Media3 ExoPlayer Preview Stage + Interactive Layers + Transport Bar
      VideoPreviewStage(
        aspectRatio = uiState.aspectRatio,
        isOriginalAudioMuted = uiState.isOriginalAudioMuted,
        mainTrackClips = uiState.mainTrackClips,
        overlayTracks = uiState.overlayTracks,
        textTrackClips = uiState.textTrackClips,
        audioTrackClips = uiState.audioTrackClips,
        selectedClip = uiState.selectedClip,
        isInteractiveCropMode = uiState.isInteractiveCropMode,
        playheadMs = uiState.playheadMs,
        totalDurationMs = uiState.totalDurationMs,
        isPlaying = uiState.isPlaying,
        seekCommand = uiState.seekCommand,
        zoomScale = uiState.zoomScale,
        textBitmapRenderer = viewModel.textBitmapRenderer,
        onTogglePlayPause = viewModel::togglePlayPause,
        onRestartToBeginning = { viewModel.scrubTo(0L, pauseIfPlaying = true) },
        onPlaybackPositionUpdated = viewModel::onExoPlayerPositionUpdated,
        onPlaybackEnded = viewModel::onExoPlayerPlaybackEnded,
        onSelectClip = { id -> viewModel.selectClip(id) },
        onDoubleTapTextClip = viewModel::openEditSelectedTextDialog,
        onLiveCanvasTransform = { clipId, ox, oy, sc, rot ->
          viewModel.onLiveCanvasTransform(clipId, ox, oy, sc, rot)
        },
        onCommitCanvasTransform = viewModel::commitLiveCanvasTransform,
        onLiveCropChange = viewModel::onLiveCropChange,
        onCommitCrop = viewModel::commitInteractiveCrop,
        onResetCrop = viewModel::resetSelectedClipCropAndTransform,
        onZoomIn = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.updateZoom(1.25f)
        },
        onZoomOut = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.updateZoom(0.8f)
        },
        onResetZoom = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.setZoomExact(1.0f)
        },
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.50f)
      )

      HorizontalDivider(color = NexaBorderSubtle)

      // Bottom Half: Multi-Track Zoomable Timeline (Text, Overlay, Video w/ Transitions, Audio)
      MultiTrackTimeline(
        mainTrackClips = uiState.mainTrackClips,
        overlayTracks = uiState.overlayTracks,
        textTrackClips = uiState.textTrackClips,
        audioTrackClips = uiState.audioTrackClips,
        isOriginalAudioMuted = uiState.isOriginalAudioMuted,
        totalDurationMs = uiState.totalDurationMs,
        playheadMs = uiState.playheadMs,
        isPlaying = uiState.isPlaying,
        zoomScale = uiState.zoomScale,
        selectedClipId = uiState.selectedClipId,
        filmstripGenerator = viewModel.filmstripGenerator,
        onScrubTo = { targetMs -> viewModel.scrubTo(targetMs, pauseIfPlaying = true) },
        onPinchZoomDelta = viewModel::updateZoom,
        onSelectClip = { id -> viewModel.selectClip(id) },
        onClickTransitionJunction = viewModel::openTransitionJunction,
        onToggleOriginalAudioMute = viewModel::toggleOriginalVideoAudioMute,
        onLiveTrimUpdate = viewModel::onLiveTrimUpdate,
        onCommitLiveTrim = viewModel::commitLiveTrim,
        onReorderMainClip = viewModel::reorderMainTrackClip,
        onLiveNonMainMove = viewModel::onLiveNonMainMove,
        onCommitNonMainMove = viewModel::commitLiveNonMainMove,
        onRequestAddText = viewModel::openAddTextDialog,
        onRequestAddOverlay = { trackIdx ->
          pendingOverlayTrackIdx = trackIdx
          addOverlayMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        },
        onRequestAddMainMedia = {
          addMainMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        },
        onRequestAddAudio = {
          addAudioFileLauncher.launch(arrayOf("audio/*"))
        },
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.50f)
      )
    }
  }

  // Text Overlay Add / Edit Dialog
  if (uiState.showTextEditorDialog) {
    val existingTextEntity = uiState.editingTextClipId?.let { editId ->
      uiState.textTrackClips.firstOrNull { it.id == editId }?.textOverlay
    }
    TextOverlayEditorDialog(
      initialOverlay = existingTextEntity,
      onDismiss = viewModel::dismissTextDialog,
      onConfirm = viewModel::confirmTextDialog
    )
  }

  // Editable Project Name Dialog
  if (uiState.showRenameDialog) {
    var titleDraft by rememberSaveable(uiState.project?.name) {
      mutableStateOf(uiState.project?.name ?: "")
    }
    AlertDialog(
      onDismissRequest = viewModel::dismissRenameDialog,
      containerColor = NexaSurfaceElevated,
      shape = RoundedCornerShape(16.dp),
      title = {
        Text(
          text = stringResource(R.string.action_rename),
          style = MaterialTheme.typography.titleLarge,
          color = NexaTextPrimary
        )
      },
      text = {
        OutlinedTextField(
          value = titleDraft,
          onValueChange = { titleDraft = it },
          singleLine = true,
          label = { Text(stringResource(R.string.project_name_label)) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NexaCyan,
            unfocusedBorderColor = NexaBorderSubtle,
            focusedLabelColor = NexaCyan,
            unfocusedLabelColor = NexaTextSecondary,
            focusedTextColor = NexaTextPrimary,
            unfocusedTextColor = NexaTextPrimary,
            cursorColor = NexaCyan
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_rename_input")
        )
      },
      confirmButton = {
        Button(
          onClick = { viewModel.updateProjectName(titleDraft) },
          enabled = titleDraft.isNotBlank(),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = NexaCyan,
            contentColor = NexaBlack
          )
        ) {
          Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissRenameDialog) {
          Text(stringResource(R.string.action_cancel), color = NexaTextSecondary)
        }
      }
    )
  }

  // Export Resolution / Progress / Completion Bottom Sheet
  if (uiState.exportState !is ExportState.Idle) {
    val isCurrentlyExporting = uiState.exportState is ExportState.Exporting
    ModalBottomSheet(
      onDismissRequest = {
        if (!isCurrentlyExporting) {
          viewModel.cancelOrCloseExport()
        }
      },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = NexaSurface,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
      ExportSheetContent(
        projectName = uiState.project?.name ?: "PlayNexa",
        aspectRatio = uiState.aspectRatio,
        totalDurationMs = uiState.totalDurationMs,
        clipCount = uiState.clips.size,
        exportState = uiState.exportState,
        onSelectResolution = viewModel::selectExportResolution,
        onStartExport = viewModel::startExport,
        onCancelOrClose = viewModel::cancelOrCloseExport,
        onViewExportedVideo = { uriStr ->
          runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(Uri.parse(uriStr), "video/mp4")
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(viewIntent)
          }.onFailure {
            viewModel.showMessage("No video player app found to open MP4.")
          }
        },
        onShareExportedVideo = { uriStr ->
          runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "video/mp4"
              putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Exported Video"))
          }.onFailure {
            viewModel.showMessage("Unable to open share sheet.")
          }
        }
      )
    }
  }
}

@Composable
private fun EditorTopBar(
  projectName: String,
  aspectRatioLabel: String,
  canUndo: Boolean,
  canRedo: Boolean,
  onBack: () -> Unit,
  onEditProjectName: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onToggleAspectRatio: () -> Unit,
  onExport: () -> Unit
) {
  Surface(
    color = NexaBlack,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .height(54.dp)
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Back Button + Editable Project Name
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("editor_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.editor_back),
              tint = NexaTextPrimary
            )
          }

          Row(
            modifier = Modifier
              .weight(1f, fill = false)
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onEditProjectName)
              .padding(horizontal = 6.dp, vertical = 6.dp)
              .testTag("editor_project_name"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = projectName,
              style = MaterialTheme.typography.titleMedium,
              color = NexaTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = stringResource(R.string.action_rename),
              tint = NexaTextSecondary,
              modifier = Modifier.size(14.dp)
            )
          }
        }

        // Right: Undo + Redo + Aspect Ratio Chip + Gradient Export Button
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier.padding(end = 6.dp)
        ) {
          IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier
              .size(40.dp)
              .testTag("undo_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Undo,
              contentDescription = stringResource(R.string.action_undo),
              tint = if (canUndo) NexaTextPrimary else NexaTextDisabled,
              modifier = Modifier.size(20.dp)
            )
          }

          IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier
              .size(40.dp)
              .testTag("redo_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Redo,
              contentDescription = stringResource(R.string.action_redo),
              tint = if (canRedo) NexaTextPrimary else NexaTextDisabled,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(2.dp))

          Surface(
            onClick = onToggleAspectRatio,
            color = NexaSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexaBorderSubtle),
            modifier = Modifier
              .height(34.dp)
              .testTag("editor_aspect_ratio_chip")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 8.dp)
            ) {
              Text(
                text = aspectRatioLabel,
                style = MaterialTheme.typography.labelMedium,
                color = NexaCyan,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.width(4.dp))

          Box(
            modifier = Modifier
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(NexaAccentGradient)
              .clickable(onClick = onExport)
              .padding(horizontal = 12.dp)
              .testTag("export_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.FileUpload,
                contentDescription = null,
                tint = NexaBlack,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.editor_export),
                style = MaterialTheme.typography.labelLarge,
                color = NexaBlack,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
      HorizontalDivider(color = NexaBorderSubtle)
    }
  }
}
