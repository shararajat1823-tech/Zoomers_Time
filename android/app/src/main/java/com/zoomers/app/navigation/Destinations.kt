package com.zoomers.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox as AddBoxFilled
import androidx.compose.material.icons.filled.Home as HomeFilled
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle as PlayCircleFilled
import androidx.compose.material.icons.filled.Search as SearchFilled
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
    const val EDIT_PROFILE = "edit-profile"
    const val USER = "user"
    const val COMMENTS = "comments"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val SAVED = "saved"
    const val HASHTAG = "hashtag"
    const val STORY_VIEWER = "story-viewer"
    const val INBOX = "inbox"
    const val CHAT = "chat"
}

/** The five primary destinations shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val isCreate: Boolean = false,
    val isProfile: Boolean = false,
) {
    Home(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.HomeFilled),
    Search(Routes.SEARCH, "Search", Icons.Outlined.Search, Icons.Filled.SearchFilled),
    Create(Routes.CREATE, "Create", Icons.Outlined.AddBox, Icons.Filled.AddBoxFilled, isCreate = true),
    Reels(Routes.REELS, "Reels", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircleFilled),
    Profile(Routes.PROFILE, "Profile", Icons.Outlined.PersonOutline, Icons.Filled.Person, isProfile = true);

    companion object {
        val all = entries
    }
}
