package com.example.spotan.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.spotan.ui.theme.SpotanTheme

/**
 * Главный экран с вкладками (Tracks / Artists / Albums / Genres) + (Month / HalfYear / AllTime)
 * и сеткой карточек [TopTrackInfo].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSectionScreen(
    tracks: List<TopTrackInfo>,          // Данные для отображения (например, треки)
    selectedCategory: TopCategory,       // Выбранная категория
    selectedPeriod: TopPeriod,           // Выбранный период
    onCategorySelected: (TopCategory) -> Unit,
    onPeriodSelected: (TopPeriod) -> Unit,
    onItemClick: (TopTrackInfo) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мой Топ", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            // 1) Верхние вкладки: Tracks / Artists / Albums / Genres
            val categories = listOf(TopCategory.Tracks, TopCategory.Artists, TopCategory.Albums, TopCategory.Genres)
            val selectedCatIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)

            ScrollableTabRow(
                selectedTabIndex = selectedCatIndex,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                divider = {},
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCatIndex]),
                        color = Color(0xFFBB86FC)
                    )
                }
            ) {
                categories.forEachIndexed { i, cat ->
                    Tab(
                        selected = (i == selectedCatIndex),
                        onClick = { onCategorySelected(cat) },
                        text = {
                            Text(
                                text = when (cat) {
                                    TopCategory.Tracks -> "Треки"
                                    TopCategory.Artists -> "Исполнители"
                                    TopCategory.Albums -> "Альбомы"
                                    TopCategory.Genres -> "Жанры"
                                },
                                color = if (i == selectedCatIndex) Color.White else Color.Gray
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2) Нижние вкладки: месяц / полгода / всё время
            val periods = listOf(TopPeriod.Month, TopPeriod.HalfYear, TopPeriod.AllTime)
            val selectedPeriodIndex = periods.indexOf(selectedPeriod).coerceAtLeast(0)

            TabRow(
                selectedTabIndex = selectedPeriodIndex,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPeriodIndex]),
                        color = Color(0xFFBB86FC)
                    )
                }
            ) {
                periods.forEachIndexed { i, period ->
                    Tab(
                        selected = (i == selectedPeriodIndex),
                        onClick = { onPeriodSelected(period) },
                        text = {
                            Text(
                                text = when (period) {
                                    TopPeriod.Month -> "За месяц"
                                    TopPeriod.HalfYear -> "За полгода"
                                    TopPeriod.AllTime -> "Всё время"
                                },
                                color = if (i == selectedPeriodIndex) Color.White else Color.Gray
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3) Сетка с карточками
            // Для примера, GridCells.Fixed(2), можно менять на Adaptive(...) при желании
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tracks) { item ->
                    TopCardItem(
                        trackInfo = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

/**
 * Карточка для одного "трека" (или другого элемента), показываем обложку, название, кол-во минут и т.д.
 */
@Composable
fun TopCardItem(
    trackInfo: TopTrackInfo,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // чтобы была ближе к квадрату
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Обложка
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // обложка занимает основную часть
            ) {
                if (trackInfo.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = trackInfo.coverUrl,
                        contentDescription = "cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                    )
                } else {
                    // Заглушка, если нет URL
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Название
            Text(
                text = trackInfo.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            // Пример: "NN мин • XX просл."
            val statsString = "${trackInfo.minutes} мин • ${trackInfo.listens} просл."
            Text(
                text = statsString,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                maxLines = 1
            )
        }
    }
}
enum class TopCategory {
    Tracks, Artists, Albums, Genres
}

/**
 * Период (нижний ряд вкладок): За месяц / За полгода / За всё время
 */
enum class TopPeriod {
    Month, HalfYear, AllTime
}

/**
 * Данные, которые отображаем на карточке в TopSectionScreen:
 * - [id] ID трека/артиста/альбома/жанра
 * - [title] Название
 * - [minutes] Кол-во минут
 * - [listens] Кол-во прослушиваний
 * - [coverUrl] Ссылка на обложку
 */
data class TopTrackInfo(
    val id: String,
    val title: String,
    val minutes: Int,
    val listens: Int,
    val coverUrl: String = ""
)
