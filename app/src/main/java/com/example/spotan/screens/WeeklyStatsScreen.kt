package com.example.spotan.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyStatsScreen(
    selectedYear: Int,
    availableWeeks: List<Int>,
    onBack: () -> Unit,
    // Функция, которая даёт Triple из
    // (dailyData, dailyTimeData, genreData) за конкретную неделю
    getStatsForWeek: (year: Int, week: Int) -> Triple<
            List<Pair<String, Int>>,
            List<Pair<String, Int>>,
            Map<String, Int>
            >
) {
    // 1) Определяем «текущую» неделю года для первоначального выбора
    val currentWeek = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear())
    val initialWeek = when {
        currentWeek in availableWeeks -> currentWeek
        else -> availableWeeks.lastOrNull() ?: 1 // если списки пусты
    }
    var selectedWeek by remember { mutableStateOf(initialWeek) }

    // 2) Получаем статистику для выбранной недели
    val (dailyData, dailyTimeData, genreData) = getStatsForWeek(selectedYear, selectedWeek)

    // 3) Для линейного графика — накопительные данные (по дням внутри недели)
    val cumulativeData = dailyData.map { it.second }
        .runningFold(0) { acc, value -> acc + value }
        .drop(1) // убираем начальный 0

    // 4) Титульная строка "Неделя N (год GGGG)"
    val weekTitle = "Неделя $selectedWeek"

    // 5) Массив для вкладок. Можно показывать "W1, W2, W3..." или на русском "Н1, Н2"
    val weeksForTabs = availableWeeks.map { w -> "W$w" }
    val selectedIndex = availableWeeks.indexOf(selectedWeek).coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Статистика за $weekTitle $selectedYear",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 6) Таб с прокруткой для переключения между доступными неделями
            item {
                if (availableWeeks.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        divider = { /* пусто */ },
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = Color(0xFF1DB954) // Условно «зелёный Spotify»
                            )
                        }
                    ) {
                        weeksForTabs.forEachIndexed { i, label ->
                            Tab(
                                selected = (i == selectedIndex),
                                onClick = {
                                    selectedWeek = availableWeeks[i]
                                },
                                text = {
                                    Text(
                                        text = label,
                                        color = if (i == selectedIndex) Color.White else Color.Gray,
                                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 7) Сводная карточка
            item {
                val totalListens = dailyData.sumOf { it.second }
                val daysCount = dailyData.size
                val averageListens = if (daysCount > 0) totalListens / daysCount else 0
                val bestDay = dailyData.maxByOrNull { it.second }?.first ?: "N/A"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Сводка за $weekTitle $selectedYear",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Всего прослушиваний: $totalListens",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Среднее в день: $averageListens",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Лучший день недели: $bestDay",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                }
            }

            // 8) Столбчатая диаграмма: прослушивания по дням недели
            item {
                Text(
                    text = "Прослушивания по дням",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPBarChart(
                    data = dailyData,
                    description = "Дни (Пн..Вс)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 9) Столбчатая диаграмма: общее время прослушиваний
            item {
                Text(
                    text = "Общее время (мин) по дням",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPBarChart(
                    data = dailyTimeData,
                    description = "Минуты (Пн..Вс)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 10) Круговая диаграмма: жанры
            item {
                Text(
                    text = "Распределение по жанрам",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPPieChart(
                    data = genreData,
                    description = "Жанры за неделю",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 11) Линейный график: накопительный рост
            item {
                Text(
                    text = "Накопительный рост прослушиваний (за неделю)",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val cumulativePairs = dailyData.mapIndexed { index, (dayStr, _) ->
                    dayStr to cumulativeData[index]
                }

                MPLineChart(
                    data = cumulativePairs,
                    description = "График (накопительно)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}
