package com.omniread.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniread.app.ui.theme.Tokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val state by vm.passwordResetState.collectAsState()
    var email by remember { mutableStateOf("") }
    var token by remember(state.devToken) { mutableStateOf(state.devToken ?: "") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset password") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg0),
            )
        },
        containerColor = Tokens.Bg0,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.requestPasswordReset(email) },
                enabled = email.isNotBlank() && !state.loading,
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.loading && !state.requested) "Sending..." else "Send reset token")
            }

            if (state.requested) {
                Spacer(Modifier.height(8.dp))
                Text("Enter the reset token and your new password.", color = Tokens.Ink2, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Reset token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.submitPasswordReset(token, password) },
                    enabled = token.isNotBlank() && password.length >= 8 && !state.loading && !state.reset,
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Accent),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (state.loading) "Resetting..." else "Reset password")
                }
            }

            state.error?.let {
                Text(it, color = Tokens.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.reset) {
                Text("Password changed. Sign in with your new password.", color = Tokens.Accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
