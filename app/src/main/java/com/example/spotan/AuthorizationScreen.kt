package com.example.spotan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotan.components.AuthorizationButton

@Composable
fun AuthorizationScreen(
    viewModel: AuthorizationViewModel = viewModel(),
    onAuthorize: () -> Unit
) {
    val authStatus = viewModel.authStatus.value

    // Общий фон – тёмный
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (authStatus == "OK") {
            // Если авторизованы, выводим простой текст "OK"
            Text(
                text = "OK",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
        } else {
            // Если не авторизованы – оформляем экран с логотипом, заголовком и описанием
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Логотип (укажите свой ресурс, например, R.drawable.ic_logo)
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(100.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                // Заголовок
                Text(
                    text = "Добро пожаловать в Spotan!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Описание
                Text(
                    text = "Авторизуйтесь, чтобы начать отслеживать свою музыкальную статистику в реальном времени.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                // Кнопка авторизации – занимает всю ширину с отступами
                AuthorizationButton(
                    onAuthSuccess = { onAuthorize() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}
