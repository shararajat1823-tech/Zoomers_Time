package com.zoomers.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.component.Wordmark
import com.zoomers.app.designsystem.component.ZPrimaryButton
import com.zoomers.app.designsystem.component.ZTextField
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val colors = ZoomersTheme.colors

    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp) // keeps the form comfortable on tablets
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Wordmark(fontSize = 40.sp)
            Text(
                "Share your world.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )

            ZTextField(
                value = identifier,
                onValueChange = { identifier = it; viewModel.clearError() },
                label = "Username or email",
            )
            ZTextField(
                value = password,
                onValueChange = { password = it; viewModel.clearError() },
                label = "Password",
                isPassword = true,
                keyboardType = KeyboardType.Password,
                errorText = state.error,
            )

            ZPrimaryButton(
                text = "Log in",
                onClick = { viewModel.login(identifier, password) },
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            val linkText = buildAnnotatedString {
                append("New here?  ")
                withStyle(SpanStyle(color = colors.accent)) { append("Create an account") }
            }
            ClickableText(
                text = linkText,
                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                onClick = { onNavigateToRegister() },
            )
        }
    }
}
