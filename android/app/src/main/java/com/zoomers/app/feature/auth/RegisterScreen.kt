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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zoomers.app.designsystem.component.Wordmark
import com.zoomers.app.designsystem.component.ZPrimaryButton
import com.zoomers.app.designsystem.component.ZTextField
import com.zoomers.app.designsystem.theme.Spacing
import com.zoomers.app.designsystem.theme.ZoomersTheme

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val colors = ZoomersTheme.colors

    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Wordmark()
            Text(
                "Create your account",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )

            ZTextField(username, { username = it; viewModel.clearError() }, label = "Username")
            ZTextField(displayName, { displayName = it; viewModel.clearError() }, label = "Display name")
            ZTextField(email, { email = it; viewModel.clearError() }, label = "Email (optional)", keyboardType = KeyboardType.Email)
            ZTextField(
                password, { password = it; viewModel.clearError() },
                label = "Password", isPassword = true, errorText = state.error,
            )

            ZPrimaryButton(
                text = "Sign up",
                onClick = { viewModel.register(username, email, password, displayName) },
                loading = state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            ClickableText(
                text = androidx.compose.ui.text.AnnotatedString("Already have an account? Log in"),
                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
                onClick = { onBack() },
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
