package com.zoomers.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val HOME = "home"
    const val SEARCH = "search"
    const val CREATE = "create"
    const val REELS = "reels"
    const val PROFILE = "profile"
    const val POST = "post"
}

/** The five primary destinations shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isCreate: Boolean = false,
) {
    Home(Routes.HOME, "Home", Icons.Outlined.Home),
    Search(Routes.SEARCH, "Search", Icons.Outlined.Search),
    Create(Routes.CREATE, "Create", Icons.Outlined.AddBox, isCreate = true),
    Reels(Routes.REELS, "Reels", Icons.Outlined.PlayCircle),
    Profile(Routes.PROFILE, "Profile", Icons.Outlined.PersonOutline);

    companion object {
        val all = entries
    }
}
