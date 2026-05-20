package com.omniread.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniread.app.ui.theme.Tokens

@Composable
fun RegisterScreen(
    onSignedUp: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val isLoading = state is AuthUiState.Loading
    val canSubmit = email.isNotBlank()
        && username.length >= 3
        && password.length >= 8
        && !isLoading

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        vm.register(email, username, password, birthYear.toIntOrNull())
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onSignedUp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Tokens.Bg0, Color(0xFF101827), Tokens.Bg0),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Tokens.Ink1,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Create account",
                    color = Tokens.Ink1,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Create your OmniRead account", color = Tokens.Ink0, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Save your reading progress, unlocks, and community activity on any device.",
                    color = Tokens.Ink2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Tokens.Bg1.copy(alpha = 0.92f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Tokens.Bg2.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        "A username helps readers find you and keeps comments consistent across the app.",
                        color = Tokens.Ink2,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    shape = RoundedCornerShape(18.dp),
                    colors = registerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c in "._" } },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    shape = RoundedCornerShape(18.dp),
                    colors = registerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    supportingText = {
                        Text("Use at least 8 characters, with letters and digits.")
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = registerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Birth year (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                    shape = RoundedCornerShape(18.dp),
                    colors = registerFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                (state as? AuthUiState.Error)?.let {
                    RegisterError(message = it.message)
                }

                Button(
                    onClick = { submit() },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.Accent,
                        contentColor = Color.White,
                        disabledContainerColor = Tokens.Bg3,
                        disabledContentColor = Tokens.Ink3,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Creating account", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("Create account", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Already have an account?", color = Tokens.Ink3, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = onLogin,
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Sign in", color = Tokens.Accent)
                }
            }

            Text(
                "By creating an account, you can follow stories, comment, and keep coins in sync.",
                color = Tokens.Ink3,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RegisterError(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Danger.copy(alpha = 0.14f))
            .border(1.dp, Tokens.Danger.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            message,
            color = Tokens.Danger,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun registerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Tokens.Ink0,
    unfocusedTextColor = Tokens.Ink0,
    focusedContainerColor = Tokens.Bg2,
    unfocusedContainerColor = Tokens.Bg2,
    cursorColor = Tokens.Accent,
    focusedBorderColor = Tokens.Accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedLabelColor = Tokens.Accent,
    unfocusedLabelColor = Tokens.Ink2,
    focusedLeadingIconColor = Tokens.Accent,
    unfocusedLeadingIconColor = Tokens.Ink2,
    focusedTrailingIconColor = Tokens.Ink1,
    unfocusedTrailingIconColor = Tokens.Ink2,
    focusedSupportingTextColor = Tokens.Ink3,
    unfocusedSupportingTextColor = Tokens.Ink3,
)
