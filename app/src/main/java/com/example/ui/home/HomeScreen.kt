package com.example.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entity.AspectRatio
import com.example.data.local.entity.ProjectEntity
import com.example.domain.model.TimeFormatUtils
import com.example.ui.theme.NexaAccentGradient
import com.example.ui.theme.NexaBlack
import com.example.ui.theme.NexaBorderSubtle
import com.example.ui.theme.NexaCyan
import com.example.ui.theme.NexaPurple
import com.example.ui.theme.NexaRed
import com.example.ui.theme.NexaRedDim
import com.example.ui.theme.NexaSurface
import com.example.ui.theme.NexaSurfaceElevated
import com.example.ui.theme.NexaTextDisabled
import com.example.ui.theme.NexaTextPrimary
import com.example.ui.theme.NexaTextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onOpenEditor: (Long) -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val haptic = LocalHapticFeedback.current
  val snackbarHostState = remember { SnackbarHostState() }

  val multipleMediaLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
  ) { uris ->
    if (uris.isNotEmpty()) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      viewModel.onMediaPickedForNewProject(uris)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is HomeEvent.NavigateToEditor -> onOpenEditor(event.projectId)
      }
    }
  }

  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
    }
  }

  Scaffold(
    containerColor = NexaBlack,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      HomeStudioTopBar(projectCount = uiState.projects.size)
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 164.dp),
        modifier = Modifier
          .widthIn(max = 960.dp)
          .fillMaxSize()
          .testTag("projects_grid"),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 8.dp,
          bottom = 32.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Large "+ New Project" hero card spanning full width at top
        item(span = { GridItemSpan(maxLineSpan) }) {
          NewProjectHeroCard(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              viewModel.openNewProjectSheet()
            }
          )
        }

        // Section Header
        item(span = { GridItemSpan(maxLineSpan) }) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = stringResource(R.string.recent_projects),
              style = MaterialTheme.typography.titleLarge,
              color = NexaTextPrimary
            )
            if (uiState.projects.isNotEmpty()) {
              Text(
                text = "Long-press for options",
                style = MaterialTheme.typography.labelMedium,
                color = NexaTextDisabled
              )
            }
          }
        }

        if (!uiState.isLoading && uiState.projects.isEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyStudioCard(
              onStartProject = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.openNewProjectSheet()
              }
            )
          }
        } else {
          items(
            items = uiState.projects,
            key = { it.id }
          ) { project ->
            AnimatedVisibility(
              visible = true,
              enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(initialScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
              ProjectGridCard(
                project = project,
                onClick = { onOpenEditor(project.id) },
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  viewModel.openProjectOptions(project)
                }
              )
            }
          }
        }
      }
    }
  }

  // New Project Setup Bottom Sheet (Aspect Ratio + Gallery Picker)
  if (uiState.showNewProjectSheet) {
    ModalBottomSheet(
      onDismissRequest = { viewModel.dismissNewProjectSheet() },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = NexaSurface,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
      NewProjectSheetContent(
        projectName = uiState.draftProjectName,
        selectedRatio = uiState.draftAspectRatio,
        isCreating = uiState.isCreatingProject,
        onProjectNameChange = viewModel::updateDraftProjectName,
        onSelectRatio = { ratio ->
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.updateDraftAspectRatio(ratio)
        },
        onPickMedia = {
          multipleMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
          )
        }
      )
    }
  }

  // Long-Press Project Options Sheet
  uiState.activeOptionsProject?.let { targetProject ->
    ModalBottomSheet(
      onDismissRequest = { viewModel.dismissProjectOptions() },
      containerColor = NexaSurface,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
      ProjectOptionsSheetContent(
        project = targetProject,
        onRename = { viewModel.requestRenameProject(targetProject) },
        onDuplicate = {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          viewModel.duplicateProject(targetProject.id)
        },
        onDelete = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          viewModel.deleteProject(targetProject.id)
        }
      )
    }
  }

  // Rename Project Dialog
  uiState.renameTargetProject?.let { projectToRename ->
    var nameInput by rememberSaveable(projectToRename.id) {
      mutableStateOf(projectToRename.name)
    }
    AlertDialog(
      onDismissRequest = { viewModel.dismissRenameDialog() },
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
          value = nameInput,
          onValueChange = { nameInput = it },
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
            .testTag("rename_project_input")
        )
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRenameProject(projectToRename.id, nameInput) },
          enabled = nameInput.isNotBlank(),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = NexaCyan,
            contentColor = NexaBlack
          ),
          modifier = Modifier.testTag("confirm_rename_button")
        ) {
          Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRenameDialog() }) {
          Text(stringResource(R.string.action_cancel), color = NexaTextSecondary)
        }
      }
    )
  }
}

@Composable
private fun HomeStudioTopBar(projectCount: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(NexaAccentGradient),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          tint = NexaBlack,
          modifier = Modifier.size(24.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          color = NexaTextPrimary
        )
        Text(
          text = stringResource(R.string.home_subtitle),
          style = MaterialTheme.typography.labelMedium,
          color = NexaTextSecondary
        )
      }
    }

    Surface(
      color = NexaSurfaceElevated,
      shape = CircleShape,
      border = BorderStroke(1.dp, NexaBorderSubtle)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesomeMotion,
          contentDescription = null,
          tint = NexaCyan,
          modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "$projectCount ${if (projectCount == 1) "Project" else "Projects"}",
          style = MaterialTheme.typography.labelMedium,
          color = NexaTextPrimary
        )
      }
    }
  }
}

@Composable
private fun NewProjectHeroCard(onClick: () -> Unit) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = NexaSurface),
    border = BorderStroke(1.5.dp, NexaAccentGradient),
    modifier = Modifier
      .fillMaxWidth()
      .height(148.dp)
      .testTag("new_project_card")
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(id = R.drawable.img_studio_hero),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.42f,
        modifier = Modifier.fillMaxSize()
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.horizontalGradient(
              colors = listOf(
                Color(0xEE0A0A0A),
                Color(0xB8121024),
                Color(0x660A0A0A)
              )
            )
          )
      )

      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Surface(
            color = Color(0x3300F0FF),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "MULTI-TRACK STUDIO",
              style = MaterialTheme.typography.labelSmall,
              color = NexaCyan,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "+ ${stringResource(R.string.new_project)}",
            style = MaterialTheme.typography.headlineMedium,
            color = NexaTextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.new_project_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NexaTextSecondary
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NexaAccentGradient),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.new_project),
            tint = NexaBlack,
            modifier = Modifier.size(30.dp)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectGridCard(
  project: ProjectEntity,
  onClick: () -> Unit,
  onLongClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = NexaSurface),
    border = BorderStroke(1.dp, NexaBorderSubtle),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
      )
      .testTag("project_card_${project.id}")
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 11f)
          .background(NexaSurfaceElevated)
      ) {
        val thumbPath = project.thumbnailPath
        if (!thumbPath.isNullOrBlank() && File(thumbPath).exists()) {
          AsyncImage(
            model = File(thumbPath),
            contentDescription = project.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF1A1B26), Color(0xFF121218))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.VideoLibrary,
              contentDescription = null,
              tint = NexaTextDisabled,
              modifier = Modifier.size(32.dp)
            )
          }
        }

        // Aspect Ratio Pill (Top Left)
        Surface(
          color = Color(0xB30A0A0A),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
        ) {
          Text(
            text = project.aspectRatio,
            style = MaterialTheme.typography.labelSmall,
            color = NexaCyan,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        // Duration Badge (Bottom Right)
        Surface(
          color = Color(0xCC0A0A0A),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
        ) {
          Text(
            text = TimeFormatUtils.formatDurationBadge(project.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = NexaTextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.name,
            style = MaterialTheme.typography.titleMedium,
            color = NexaTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = TimeFormatUtils.formatRelativeEdited(project.updatedAt),
            style = MaterialTheme.typography.labelMedium,
            color = NexaTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        IconButton(
          onClick = onLongClick,
          modifier = Modifier
            .size(48.dp)
            .testTag("project_more_${project.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = stringResource(R.string.project_options),
            tint = NexaTextSecondary,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyStudioCard(onStartProject: () -> Unit) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = NexaSurface),
    border = BorderStroke(1.dp, NexaBorderSubtle),
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp)
      .testTag("empty_projects_state")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(NexaSurfaceElevated)
          .border(1.dp, NexaBorderSubtle, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Rounded.MovieFilter,
          contentDescription = null,
          tint = NexaCyan,
          modifier = Modifier.size(30.dp)
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.empty_projects_title),
        style = MaterialTheme.typography.titleLarge,
        color = NexaTextPrimary
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.empty_projects_body),
        style = MaterialTheme.typography.bodyMedium,
        color = NexaTextSecondary,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(20.dp))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(NexaAccentGradient)
          .clickable(onClick = onStartProject)
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .testTag("empty_state_new_project_button")
      ) {
        Text(
          text = "+ ${stringResource(R.string.new_project)}",
          style = MaterialTheme.typography.labelLarge,
          color = NexaBlack,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun NewProjectSheetContent(
  projectName: String,
  selectedRatio: AspectRatio,
  isCreating: Boolean,
  onProjectNameChange: (String) -> Unit,
  onSelectRatio: (AspectRatio) -> Unit,
  onPickMedia: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 8.dp)
  ) {
    Text(
      text = stringResource(R.string.new_project),
      style = MaterialTheme.typography.headlineMedium,
      color = NexaTextPrimary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "Configure canvas format, then pick clips from your gallery.",
      style = MaterialTheme.typography.bodyMedium,
      color = NexaTextSecondary
    )

    Spacer(modifier = Modifier.height(18.dp))

    OutlinedTextField(
      value = projectName,
      onValueChange = onProjectNameChange,
      singleLine = true,
      enabled = !isCreating,
      label = { Text(stringResource(R.string.project_name_label)) },
      shape = RoundedCornerShape(8.dp),
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
        .testTag("new_project_name_input")
    )

    Spacer(modifier = Modifier.height(20.dp))

    Text(
      text = stringResource(R.string.select_aspect_ratio),
      style = MaterialTheme.typography.titleMedium,
      color = NexaTextPrimary
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      AspectRatio.entries.forEach { ratio ->
        val isSelected = ratio == selectedRatio
        AspectRatioOptionCard(
          ratio = ratio,
          isSelected = isSelected,
          enabled = !isCreating,
          onClick = { onSelectRatio(ratio) },
          modifier = Modifier
            .weight(1f)
            .testTag("aspect_ratio_${ratio.rawValue.replace(":", "_")}")
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (isCreating) Brush.horizontalGradient(listOf(NexaSurfaceElevated, NexaSurfaceElevated)) else NexaAccentGradient)
        .clickable(enabled = !isCreating, onClick = onPickMedia)
        .testTag("pick_media_button"),
      contentAlignment = Alignment.Center
    ) {
      if (isCreating) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(
            color = NexaCyan,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Importing Media…",
            style = MaterialTheme.typography.labelLarge,
            color = NexaTextPrimary
          )
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = NexaBlack,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.pick_gallery_media),
            style = MaterialTheme.typography.titleMedium,
            color = NexaBlack,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))
  }
}

@Composable
private fun AspectRatioOptionCard(
  ratio: AspectRatio,
  isSelected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val borderBrush = if (isSelected) {
    NexaAccentGradient
  } else {
    Brush.horizontalGradient(listOf(NexaBorderSubtle, NexaBorderSubtle))
  }

  Surface(
    onClick = onClick,
    enabled = enabled,
    color = if (isSelected) Color(0x1F00F0FF) else NexaSurfaceElevated,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderBrush),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Visual aspect-ratio frame preview icon
      val frameW = when (ratio) {
        AspectRatio.RATIO_9_16 -> 16.dp
        AspectRatio.RATIO_1_1 -> 22.dp
        AspectRatio.RATIO_16_9 -> 28.dp
      }
      val frameH = when (ratio) {
        AspectRatio.RATIO_9_16 -> 28.dp
        AspectRatio.RATIO_1_1 -> 22.dp
        AspectRatio.RATIO_16_9 -> 16.dp
      }
      Box(
        modifier = Modifier.height(30.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(width = frameW, height = frameH)
            .border(
              width = 1.8.dp,
              color = if (isSelected) NexaCyan else NexaTextSecondary,
              shape = RoundedCornerShape(4.dp)
            )
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = ratio.label,
        style = MaterialTheme.typography.titleMedium,
        color = if (isSelected) NexaCyan else NexaTextPrimary,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = ratio.subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = NexaTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun ProjectOptionsSheetContent(
  project: ProjectEntity,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 8.dp)
  ) {
    Text(
      text = project.name,
      style = MaterialTheme.typography.titleLarge,
      color = NexaTextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    Text(
      text = "${project.aspectRatio} • ${TimeFormatUtils.formatDurationBadge(project.durationMs)}",
      style = MaterialTheme.typography.bodySmall,
      color = NexaTextSecondary
    )

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = NexaBorderSubtle)
    Spacer(modifier = Modifier.height(8.dp))

    OptionSheetRow(
      icon = Icons.Outlined.Edit,
      label = stringResource(R.string.action_rename),
      tint = NexaTextPrimary,
      testTag = "option_rename_project",
      onClick = onRename
    )
    OptionSheetRow(
      icon = Icons.Outlined.ContentCopy,
      label = stringResource(R.string.action_duplicate),
      tint = NexaTextPrimary,
      testTag = "option_duplicate_project",
      onClick = onDuplicate
    )
    OptionSheetRow(
      icon = Icons.Outlined.DeleteOutline,
      label = stringResource(R.string.action_delete),
      tint = NexaRed,
      backgroundTint = NexaRedDim,
      testTag = "option_delete_project",
      onClick = onDelete
    )

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun OptionSheetRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color,
  testTag: String,
  backgroundTint: Color = Color.Transparent,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    color = backgroundTint,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = tint,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}
