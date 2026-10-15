package com.zoomers.app.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.zoomers.app.designsystem.theme.BrandSerif
import com.zoomers.app.designsystem.theme.ZoomersTheme

/** The Zoomers brand wordmark (lowercase serif, echoing the concept). */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 26.sp) {
    Text(
        text = "zoomers",
        modifier = modifier,
        fontFamily = BrandSerif,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = ZoomersTheme.colors.text,
    )
}
