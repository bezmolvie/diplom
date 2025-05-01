package com.example.spotan.components

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

@Composable
fun AuthorizationButton(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onAuthSuccess() },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
    ) {
        Text(text = "Авторизоваться через Spotify")
    }
}