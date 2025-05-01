package com.example.spotan.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlin.math.cos
import kotlin.math.sin
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.components.AxisBase
import kotlin.math.min
/**
 * Главный экран со статистикой за год, использует MPAndroidChart
 *
 * [availableYears] - список лет, для которых есть данные (например, listOf(2021, 2022, 2023))
 * [onBack] - действие при нажатии на кнопку "Назад" в top bar
 * [getStatsForYear] - функция, возвращающая тройку:
 *   - monthlyData: Список пар (Месяц, Кол-во прослушиваний)
 *   - monthlyTimeData: Список пар (Месяц, Общее время прослушиваний)
 *   - genreData: Словарь (Жанр -> Количество)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyStatsScreen(
    availableYears: List<Int>,
    onBack: () -> Unit,
    viewModel: MainViewModel  // передаём ViewModel для доступа к кэшу статистики
) {
    // Если availableYears пуст или нет, используем значение по умолчанию, например 2021.
    var selectedYear by remember { mutableStateOf(availableYears.lastOrNull() ?: 2021) }
    // Читаем кэш: map, где ключ – год, значение – агрегированная статистика:
    val cachedStatsMap by viewModel.cachedYearlyStats.collectAsState()
    // Достаём статистику для выбранного года:
    val stats = cachedStatsMap[selectedYear]

    // Если статистика еще не готова – можем показать индикатор загрузки
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика за $selectedYear", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        if (stats == null) {
            // Пока статистика не вычислена, показываем CircularProgressIndicator
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            // stats - тройка: monthlyData, monthlyTimeData, genreData
            val monthlyData = stats.first        // List<Pair<String, Int>>
            val monthlyTimeData = stats.second     // List<Pair<String, Int>>
            // Накопительный рост: вычисляем cumulativeData из monthlyData
            val cumulativeData = monthlyData.map { it.second }
                .runningFold(0) { acc, value -> acc + value }
                .drop(1)  // убираем первый 0

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Табы для выбора года
                item {
                    if (availableYears.isNotEmpty()) {
                        TabRow(selectedTabIndex = availableYears.indexOf(selectedYear)) {
                            availableYears.forEach { year ->
                                Tab(
                                    selected = selectedYear == year,
                                    onClick = { selectedYear = year },
                                    text = { Text("$year") }
                                )
                            }
                        }
                    }
                }
                // Общая сводка
                item {
                    val totalListens = monthlyData.sumOf { it.second }
                    val averageListens = if (monthlyData.isNotEmpty()) totalListens / monthlyData.size else 0
                    val bestMonth = monthlyData.maxByOrNull { it.second }?.first ?: "N/A"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Общая статистика за $selectedYear",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Всего прослушиваний: $totalListens",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                            Text(
                                text = "Среднее в месяц: $averageListens",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                            Text(
                                text = "Лучший месяц: $bestMonth",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                            )
                        }
                    }
                }
                // Столбчатая диаграмма: прослушивания по месяцам
                item {
                    Text(
                        text = "Прослушивания по месяцам",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MPBarChart(
                        data = monthlyData,
                        description = "Прослушивания",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                // Столбчатая диаграмма: общее время прослушиваний
                item {
                    Text(
                        text = "Общее время прослушиваний (мин)",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MPBarChart(
                        data = monthlyTimeData,
                        description = "Минуты прослушивания",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                // Круговая диаграмма: распределение по жанрам
                item {
                    Text(
                        text = "Распределение по жанрам",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MPPieChart(
                        data = stats.third,
                        description = "Жанры",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                // Линейный график: накопительный рост
                item {
                    Text(
                        text = "Накопительный рост прослушиваний",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val cumulativePairs = monthlyData.mapIndexed { index, pair ->
                        pair.first to cumulativeData.getOrElse(index) { 0 }
                    }
                    MPLineChart(
                        data = cumulativePairs,
                        description = "Накопительный рост",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }
        }
    }
}

/**
 * BarChart: вывод столбчатой диаграммы с отключёнными подписями над столбцами,
 * но с метками по оси X (месяцы). Метки показываются только для целых значений.
 */
@Composable
fun MPBarChart(
    data: List<Pair<String, Int>>,
    description: String = "",
    modifier: Modifier = Modifier
) {
    val entries = data.mapIndexed { index, pair ->
        BarEntry(index.toFloat(), pair.second.toFloat())
    }

    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                this.description.text = description
                this.setDrawGridBackground(false)
                this.setPinchZoom(false)
                this.axisRight.isEnabled = false
                this.legend.isEnabled = false
                this.setScaleEnabled(true)
            }
        },
        update = { barChart ->
            val dataSet = BarDataSet(entries, "").apply {
                color = android.graphics.Color.rgb(29, 185, 84)
                setDrawValues(false) // не рисовать цифры над столбцами
            }
            val barData = BarData(dataSet).apply {
                barWidth = 0.5f
            }
            barChart.data = barData

            // Настройка оси X
            barChart.xAxis.apply {
                textColor = android.graphics.Color.WHITE
                position = XAxis.XAxisPosition.BOTTOM
                // Чтобы показывать только целые метки (месяцы)
                granularity = 1f
                isGranularityEnabled = true
                labelRotationAngle = -45f

                // Если вы хотите *всегда* 12 меток (по кол-ву месяцев), включайте:
                // setLabelCount(data.size, true)

                // Переопределяем, чтобы отдавать месяц только для целых X
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        // Если число целое -> показываем месяц, иначе - пусто
                        return if (value % 1 == 0f) {
                            val index = value.toInt().coerceIn(data.indices)
                            data[index].first
                        } else {
                            ""
                        }
                    }
                }
            }

            // Ось Y
            barChart.axisLeft.apply {
                textColor = android.graphics.Color.WHITE
                axisMinimum = 0f
            }

            barChart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * LineChart: вывод линейного графика с отключёнными подписями над точками,
 * метки оси X показываются только на целых значениях (месяцах).
 */
@Composable
fun MPLineChart(
    data: List<Pair<String, Int>>,
    description: String = "",
    modifier: Modifier = Modifier
) {
    val entries = data.mapIndexed { index, pair ->
        Entry(index.toFloat(), pair.second.toFloat())
    }

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                this.description.text = description
                this.setDrawGridBackground(false)
                this.setPinchZoom(false)
                this.axisRight.isEnabled = false
                this.legend.isEnabled = false
                this.setScaleEnabled(true)
            }
        },
        update = { lineChart ->
            val dataSet = LineDataSet(entries, "").apply {
                color = android.graphics.Color.rgb(29, 185, 84)
                lineWidth = 3f
                circleRadius = 4f
                setCircleColor(android.graphics.Color.WHITE)
                // Отключаем числовые подписи над точками
                setDrawValues(false)
            }
            val lineData = LineData(dataSet)
            lineChart.data = lineData

            // Ось X (только целые)
            lineChart.xAxis.apply {
                textColor = android.graphics.Color.WHITE
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                isGranularityEnabled = true
                labelRotationAngle = -45f

                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                        return if (value % 1 == 0f) {
                            val index = value.toInt().coerceIn(data.indices)
                            data[index].first
                        } else {
                            ""
                        }
                    }
                }
            }

            // Ось Y
            lineChart.axisLeft.apply {
                textColor = android.graphics.Color.WHITE
                axisMinimum = 0f
            }

            lineChart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * PieChart: если нет данных или сумма = 0, пишем "Нет данных" в центре.
 * Иначе показываем процентное распределение по жанрам.
 */
@Composable
fun MPPieChart(
    data: Map<String, Int>,
    description: String = "",
    modifier: Modifier = Modifier
) {
    val entries = data.map { (genre, value) ->
        PieEntry(value.toFloat(), genre)
    }

    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                this.description.text = description
                this.isDrawHoleEnabled = true
                this.setUsePercentValues(true)
                this.setEntryLabelColor(android.graphics.Color.WHITE)
                this.legend.isEnabled = false
            }
        },
        update = { pieChart ->
            val totalSum = data.values.sum()
            if (totalSum == 0 || entries.isEmpty()) {
                pieChart.data = null
                pieChart.centerText = "Нет данных"
                pieChart.invalidate()
                return@AndroidView
            } else {
                pieChart.centerText = ""
            }

            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(
                    android.graphics.Color.rgb(233, 30, 99),   // розовый
                    android.graphics.Color.rgb(156, 39, 176),  // фиолетовый
                    android.graphics.Color.rgb(33, 150, 243),  // голубой
                    android.graphics.Color.rgb(76, 175, 80),   // зелёный
                    android.graphics.Color.rgb(255, 193, 7),   // жёлтый
                    android.graphics.Color.rgb(255, 87, 34),   // оранжевый
                )
                setDrawValues(true)
                valueTextColor = android.graphics.Color.WHITE
                valueTextSize = 12f
            }

            val pieData = PieData(dataSet).apply {
                setValueFormatter(PercentFormatter(pieChart)) // показываем проценты
            }

            pieChart.data = pieData
            pieChart.invalidate()
        },
        modifier = modifier
    )
}
