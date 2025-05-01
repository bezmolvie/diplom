package com.example.spotan.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack

import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.spotan.data.Recommendation
import com.example.spotan.data.SpotifyTrack
import java.time.Duration


enum class RecommendationPeriod {
    Morning, Work, Night
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // При первом отображении запускаем загрузку рекомендаций (если ещё не загружены)
    LaunchedEffect(Unit) {
        viewModel.fetchRecommendationsForWeeklyTop()
    }

    // Читаем кэш рекомендаций – здесь ожидается Map<String, List<Recommendation>>
    // где key = seed (в формате "artist - song name")
    val recMap by viewModel.cachedRecommendations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Рекомендации за неделю", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                colors = topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        if (recMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            // Для каждой seed-строки отображаем секцию с карточками (например, в LazyColumn и LazyRow)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                recMap.forEach { (seed, recList) ->
                    item {
                        // Заголовок секции — seed-трек
                        Text(
                            text = seed,
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recList) { rec ->
                                // Для каждого рекомендованного трека rec.track_key
                                // вызываем RecommendationDetailCard, чтобы получить полную инфу
                                RecommendationDetailCard(
                                    seed = rec.track_key.lowercase(), // обязательный формат
                                    viewModel = viewModel,
                                    onClick = {
                                        // Действие при клике, например, открытие трека в Spotify
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(rec: Recommendation) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { /* например, открыть трек в Spotify */ },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Отобразим track_key; можно добавить фото, если сервер возвращает URL обложки и т.д.
            Text(
                text = rec.track_key,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Score: ${rec.score}",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
        }
    }
}
@Composable
fun RecommendationDetailCard(
    seed: String, // seed в формате "artist - song name" (нижним регистром)
    viewModel: MainViewModel,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var trackDetail by remember { mutableStateOf<SpotifyTrack?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // При появлении карточки загружаем детали трека
    LaunchedEffect(seed) {
        trackDetail = viewModel.fetchTrackDetail(seed)
        isLoading = false
    }

    // Функция копирования в буфер (если у трека есть id)
    fun copySpotifyLink() {
        trackDetail?.id?.let { id ->
            val uri = "spotify:track:$id"
            clipboardManager.setText(AnnotatedString(uri))
            // Показываем Toast, используя LocalContext
            Toast.makeText(context, "Ссылка скопирована: $uri", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { copySpotifyLink() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (trackDetail != null) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Обложка альбома
                val coverUrl = trackDetail?.album?.images?.firstOrNull()?.url ?: ""
                Box(
                    modifier = Modifier
                        .height(140.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray)
                ) {
                    if (coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Обложка трека",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Название трека
                Text(
                    text = trackDetail?.name ?: "Неизвестно",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    maxLines = 1
                )
                // Исполнители (соединяем имена через запятую)
                Text(
                    text = trackDetail?.artists?.joinToString(", ") { it.name ?: "" } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                    maxLines = 1
                )
                // Если доступны, добавим название альбома и длительность
                trackDetail?.album?.name?.takeIf { it.isNotEmpty() }?.let { albumName ->
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray),
                        maxLines = 1
                    )
                }
                trackDetail?.durationMs?.let { durationMs ->
                    val duration = Duration.ofMillis(durationMs.toLong())
                    val minutes = duration.toMinutes()
                    val seconds = duration.minusMinutes(minutes).seconds
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Нет данных", color = Color.White)
            }
        }
    }
}



