package com.example.spotan.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTimeStatsScreen(
    onBack: () -> Unit,
    // Функция, которая достаёт «общую» статистику
    getAllTimeStats: () -> Triple<
            List<Pair<String, Int>>,  // yearlyListensData
            List<Pair<String, Int>>,  // yearlyTimeData
            Map<String, Int>          // genreData
            >
) {
    // 1) Получаем из ViewModel агрегированные данные
    val (yearlyListensData, yearlyTimeData, genreData) = getAllTimeStats()

    // 2) Подсчитываем общие цифры
    val totalListens = yearlyListensData.sumOf { it.second }
    val totalMinutes = yearlyTimeData.sumOf { it.second }
    val yearsCount = yearlyListensData.size
    // Среднее кол-во прослушиваний на год
    val averagePerYear = if (yearsCount > 0) totalListens / yearsCount else 0

    // Самый «продуктивный» год (по количеству треков)
    val bestYear = yearlyListensData.maxByOrNull { it.second }?.first ?: "N/A"

    // 3) Готовим данные для «накопительного» роста
    //    (Количество прослушиваний по годам складываем последовательно)
    val cumulativeList = yearlyListensData.map { it.second }
        .runningFold(0) { acc, value -> acc + value }
        .drop(1) // убираем начальный 0

    // Превратим в List<Pair<String, Int>> – (название_года, накопленный_итог)
    val cumulativePairs = yearlyListensData.mapIndexed { index, (yearStr, _) ->
        yearStr to cumulativeList[index]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Статистика за всё время",
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
            // 4) Карточка-сводка
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Общее за всё время",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Всего прослушиваний: $totalListens",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Всего минут: $totalMinutes",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Среднее на год: $averagePerYear",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Самый продуктивный год: $bestYear",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                    }
                }
            }

            // 5) Столбчатая диаграмма: «Количество прослушиваний по годам»
            item {
                Text(
                    text = "Прослушивания по годам",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPBarChart(
                    data = yearlyListensData,
                    description = "Годы",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 6) Столбчатая диаграмма: «Минуты по годам»
            item {
                Text(
                    text = "Общее время (мин) по годам",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPBarChart(
                    data = yearlyTimeData,
                    description = "Годы",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 7) Круговая диаграмма: жанры (если нужно)
            item {
                Text(
                    text = "Распределение по жанрам (за всё время)",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPPieChart(
                    data = genreData,
                    description = "Жанры (All Time)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }

            // 8) Линейный график: накопительный рост прослушиваний по годам
            item {
                Text(
                    text = "Накопительный рост (по годам)",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MPLineChart(
                    data = cumulativePairs,
                    description = "Кумулятивно по годам",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}
