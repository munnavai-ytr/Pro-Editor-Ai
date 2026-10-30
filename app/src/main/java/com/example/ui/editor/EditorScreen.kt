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
import com.example.ui.editor.components.VideoPreviewStage
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
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
        onBack = {
          viewModel.pausePlayback()
          onNavigateBack()
        },
        onEditProjectName = viewModel::openRenameDialog,
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
          selectedClip = uiState.selectedClip,
          showSpeedPicker = uiState.showSpeedPicker,
          assets = uiState.assets,
          isImportingMedia = uiState.isImportingMedia,
          onSplitClip = viewModel::splitSelectedClipAtPlayhead,
          onToggleSpeedPicker = viewModel::toggleSpeedPicker,
          onSelectSpeed = viewModel::setSelectedClipSpeed,
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
      // Top Half: Real-time Media3 ExoPlayer Preview Stage + Transport Bar
      VideoPreviewStage(
        aspectRatio = uiState.aspectRatio,
        mainTrackClips = uiState.mainTrackClips,
        overlayTracks = uiState.overlayTracks,
        selectedClipId = uiState.selectedClipId,
        playheadMs = uiState.playheadMs,
        totalDurationMs = uiState.totalDurationMs,
        isPlaying = uiState.isPlaying,
        seekCommand = uiState.seekCommand,
        zoomScale = uiState.zoomScale,
        onTogglePlayPause = viewModel::togglePlayPause,
        onRestartToBeginning = { viewModel.scrubTo(0L, pauseIfPlaying = true) },
        onPlaybackPositionUpdated = viewModel::onExoPlayerPositionUpdated,
        onPlaybackEnded = viewModel::onExoPlayerPlaybackEnded,
        onSelectOverlayClip = viewModel::selectClip,
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
          .weight(0.52f)
      )

      HorizontalDivider(color = NexaBorderSubtle)

      // Bottom Half: Multi-Track Zoomable Filmstrip Timeline
      MultiTrackTimeline(
        mainTrackClips = uiState.mainTrackClips,
        overlayTracks = uiState.overlayTracks,
        totalDurationMs = uiState.totalDurationMs,
        playheadMs = uiState.playheadMs,
        isPlaying = uiState.isPlaying,
        zoomScale = uiState.zoomScale,
        selectedClipId = uiState.selectedClipId,
        filmstripGenerator = viewModel.filmstripGenerator,
        onScrubTo = { targetMs -> viewModel.scrubTo(targetMs, pauseIfPlaying = true) },
        onPinchZoomDelta = viewModel::updateZoom,
        onSelectClip = viewModel::selectClip,
        onLiveTrimUpdate = viewModel::onLiveTrimUpdate,
        onCommitLiveTrim = viewModel::commitLiveTrim,
        onReorderMainClip = viewModel::reorderMainTrackClip,
        onLiveOverlayMove = viewModel::onLiveOverlayMove,
        onCommitOverlayMove = viewModel::commitLiveOverlayMove,
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
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.48f)
      )
    }
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
  onBack: () -> Unit,
  onEditProjectName: () -> Unit,
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
          .height(56.dp)
          .padding(horizontal = 8.dp),
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
              .padding(horizontal = 8.dp, vertical = 8.dp)
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
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = stringResource(R.string.action_rename),
              tint = NexaTextSecondary,
              modifier = Modifier.size(15.dp)
            )
          }
        }

        // Right: Aspect Ratio Toggle Pill + Gradient Export Button
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(end = 6.dp)
        ) {
          Surface(
            onClick = onToggleAspectRatio,
            color = NexaSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexaBorderSubtle),
            modifier = Modifier
              .height(36.dp)
              .testTag("editor_aspect_ratio_chip")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 10.dp)
            ) {
              Text(
                text = aspectRatioLabel,
                style = MaterialTheme.typography.labelMedium,
                color = NexaCyan,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Box(
            modifier = Modifier
              .height(38.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(NexaAccentGradient)
              .clickable(onClick = onExport)
              .padding(horizontal = 14.dp)
              .testTag("export_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.FileUpload,
                contentDescription = null,
                tint = NexaBlack,
                modifier = Modifier.size(16.dp)
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
