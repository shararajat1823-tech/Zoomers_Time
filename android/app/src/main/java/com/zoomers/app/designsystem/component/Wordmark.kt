package com.zoomers.app.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.zoomers.app.designsystem.theme.StoryGradient
import com.zoomers.app.designsystem.theme.UiSans

/** The Zoomers brand wordmark: a bold lowercase sans with the brand gradient. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 26.sp) {
    Text(
        text = "zoomers",
        modifier = modifier,
        style = TextStyle(
            fontFamily = UiSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = (-0.5).sp,
            brush = Brush.linearGradient(StoryGradient),
        ),
    )
}
