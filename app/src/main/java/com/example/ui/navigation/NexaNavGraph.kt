package com.example.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class EditorRoute(val projectId: Long)

@Composable
fun NexaNavGraph(
  modifier: Modifier = Modifier,
  navController: NavHostController = rememberNavController()
) {
  NavHost(
    navController = navController,
    startDestination = HomeRoute,
    modifier = modifier,
    enterTransition = {
      slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
      ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
    },
    exitTransition = {
      fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
    },
    popEnterTransition = {
      fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
    },
    popExitTransition = {
      slideOutHorizontally(
        targetOffsetX = { it / 3 },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
      ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
    }
  ) {
    composable<HomeRoute> {
      val homeViewModel: HomeViewModel = hiltViewModel()
      HomeScreen(
        viewModel = homeViewModel,
        onOpenEditor = { projectId ->
          navController.navigate(EditorRoute(projectId = projectId))
        }
      )
    }

    composable<EditorRoute> {
      val editorViewModel: EditorViewModel = hiltViewModel()
      EditorScreen(
        viewModel = editorViewModel,
        onNavigateBack = {
          navController.popBackStack()
        }
      )
    }
  }
}
