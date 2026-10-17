package com.zoomers.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zoomers.app.designsystem.component.NavItem
import com.zoomers.app.designsystem.component.ZBottomBar
import com.zoomers.app.designsystem.component.ZNavRail
import com.zoomers.app.designsystem.foundation.rememberZoomersLayout
import com.zoomers.app.feature.create.CreatePostScreen
import com.zoomers.app.feature.detail.PostDetailScreen
import com.zoomers.app.feature.home.HomeScreen
import com.zoomers.app.feature.profile.ProfileScreen

/**
 * Hosts the five primary destinations. Adapts navigation to the window size: a
 * bottom bar on phones, a side rail on tablets/foldables/landscape — while the
 * same content composables are reused, so every screen is responsive for free.
 */
@Composable
fun MainScaffold(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val layout = rememberZoomersLayout(windowSizeClass)

    val navItems = TopLevelDestination.all.map { NavItem(it.route, it.label, it.icon, it.isCreate) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    val onSelect: (NavItem) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val graph: @Composable (Modifier) -> Unit = { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = contentModifier,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    layout = layout,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenComments = { /* comments screen — next milestone */ },
                    onOpenMessages = { /* messages screen — next milestone */ },
                    onOpenNotifications = { /* notifications screen — next milestone */ },
                )
            }
            composable(Routes.SEARCH) { PlaceholderScreen("Search & Explore") }
            composable(Routes.CREATE) {
                val goHome: () -> Unit = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                CreatePostScreen(onPosted = goHome, onCancel = goHome)
            }
            composable(Routes.REELS) { PlaceholderScreen("Reels") }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    gridColumns = layout.gridColumns,
                    onOpenPost = { postId -> navController.navigate("${Routes.POST}/$postId") },
                )
            }
            composable("${Routes.POST}/{postId}") { entry ->
                val postId = entry.arguments?.getString("postId").orEmpty()
                PostDetailScreen(postId = postId, onBack = { navController.popBackStack() })
            }
        }
    }

    if (layout.useNavigationRail) {
        Row(Modifier.fillMaxSize()) {
            ZNavRail(items = navItems, currentRoute = currentRoute, onSelect = onSelect, modifier = Modifier.width(80.dp))
            graph(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { graph(Modifier.fillMaxSize()) }
            ZBottomBar(items = navItems, currentRoute = currentRoute, onSelect = onSelect)
        }
    }
}
