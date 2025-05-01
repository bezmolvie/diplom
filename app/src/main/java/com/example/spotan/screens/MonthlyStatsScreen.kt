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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyStatsScreen(
    selectedYear: Int,
    availableMonths: List<Int>,
    onBack: () -> Unit,
    getStatsForMonth: (year: Int, month: Int) -> Triple<
            List<Pair<String, Int>>,
            List<Pair<String, Int>>,
            Map<String, Int>
            >
) {
    // 1) Определяем, какой месяц выбрать изначально
    val currentMonth = LocalDate.now().monthValue
    val initialMonth = when {
        currentMonth in availableMonths -> currentMonth
        else -> availableMonths.lastOrNull() ?: 1
    }
    var selectedMonth by remember { mutableStateOf(initialMonth) }

    // 2) Получаем статистику (dailyData, dailyTimeData, genreData)
    val (dailyData, dailyTimeData, genreData) = getStatsForMonth(selectedYear, selectedMonth)

    // 3) Для линейного графика – накопительные данные
    val cumulativeData = dailyData.map { it.second }
        .runningFold(0) { acc, value -> acc + value }
        .drop(1) // убираем лишний 0

    // 4) Полные названия месяцев (для отображения в заголовке)
    val monthNamesFull = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )
    val monthTitle = if (selectedMonth in 1..12) {
        monthNamesFull[selectedMonth - 1]
    } else {
        "Неизвестный месяц"
    }

    // 5) Массив отображаемых лейблов для вкладок (например, первые 3 буквы)
    val monthsForTabs = availableMonths.map { m ->
        monthNamesFull[m - 1].take(3) // "Янв", "Фев", "Мар", ...
    }
    val selectedIndex = availableMonths.indexOf(selectedMonth).coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Статистика за $monthTitle $selectedYear",
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
            // 6) Кастомный ScrollableTabRow для переключения месяцев
            item {
                if (availableMonths.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        // Отключаем стандартную разделительную линию
                        divider = { /* пусто */ },
                        // Кастомизируем индикатор (зелёная полоска)
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = Color(0xFF1DB954), // Spotify-зелёный

                            )
                        }
                    ) {
                        monthsForTabs.forEachIndexed { i, label ->
                            Tab(
                                selected = (i == selectedIndex),
                                onClick = {
                                    selectedMonth = availableMonths[i]
                                },
                                text = {
                                    // Стилизуем текст вкладок
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
                            text = "Сводка за $monthTitle $selectedYear",
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
                            text = "Лучший день: $bestDay число",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                }
            }

            // 8) Столбчатая диаграмма: прослушивания по дням
            item {
                Text(
                    text = "Прослушивания по дням",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPBarChart(
                    data = dailyData,
                    description = "Дни месяца",
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
                    description = "Минуты по дням",
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
                    description = "Жанры за месяц",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 11) Линейный график: накопительный рост
            item {
                Text(
                    text = "Накопительный рост прослушиваний (по дням)",
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
