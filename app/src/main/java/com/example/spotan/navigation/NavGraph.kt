package com.example.spotan.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.spotan.SpotifyAuthManager
import com.example.spotan.screens.AllTimeStatsScreen
import com.example.spotan.screens.ImportHistoryScreen
import com.example.spotan.screens.MainScreenWithDrawer
import com.example.spotan.screens.MainViewModel
import com.example.spotan.screens.MonthlyStatsScreen
import com.example.spotan.screens.TopCategory
import com.example.spotan.screens.TopPeriod
import com.example.spotan.screens.TopSectionScreen
import com.example.spotan.screens.TopTrackInfo
import com.example.spotan.screens.WeeklyStatsScreen
import com.example.spotan.screens.YearlyStatsScreen
import java.time.LocalDate
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.spotan.screens.RecommendationScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    spotifyAuthManager: SpotifyAuthManager,
    onLogout: () -> Unit,
    onNavigateToImportHistory: () -> Unit,
    onPickFile: () -> Unit,
    onNavigateToStats: (timePeriod: String) -> Unit,
    importedFiles: List<String>
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            MainScreenWithDrawer(
                viewModel = viewModel,
                onLogout = onLogout,
                onNavigateToStats = { timePeriod ->
                    when (timePeriod) {
                        "year" -> navController.navigate("yearlyStats")
                        "month" -> navController.navigate("monthlyStats")
                        "week" -> navController.navigate("weeklyStats")
                        "all_time" -> navController.navigate("allTimeStats")
                        "top" -> navController.navigate("topScreen")
                        "recommendations" -> navController.navigate("recommendations")
                        else -> onNavigateToStats(timePeriod)
                    }
                },
                onImportHistory = onNavigateToImportHistory
            )
        }
        composable("yearlyStats") {
            val availableYears = viewModel.getAvailableYears()
            YearlyStatsScreen(
                availableYears = availableYears,
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable("recommendations") {
            RecommendationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }


        // Новый роут
        composable("monthlyStats") {
            // Для примера, берём год 2023,
            // или можем спросить у пользователя
            val selectedYear = 2024
            val availableMonths = viewModel.getAvailableMonths(selectedYear)

            MonthlyStatsScreen(
                selectedYear = selectedYear,
                availableMonths = availableMonths,
                onBack = { navController.popBackStack() },
                getStatsForMonth = { year, month ->
                    viewModel.getMonthlyStats(year, month)
                }
            )
        }
        composable("allTimeStats") {
            AllTimeStatsScreen(
                onBack = { navController.popBackStack() },
                getAllTimeStats = { viewModel.getAllTimeStats() }
            )
        }
        composable("weeklyStats") {
            // Выбираем какой-то год (например, текущий)
            val thisYear = LocalDate.now().year
            val availableWeeks = viewModel.getAvailableWeeks(thisYear)

            WeeklyStatsScreen(
                selectedYear = thisYear,
                availableWeeks = availableWeeks,
                onBack = { navController.popBackStack() },
                getStatsForWeek = { year, week ->
                    viewModel.getWeeklyStats(year, week)
                }
            )
        }

        composable("topScreen") {
            var selectedCategory by remember { mutableStateOf(TopCategory.Tracks) }
            var selectedPeriod by remember { mutableStateOf(TopPeriod.Month) }

            // 1) Читаем кэш
            val topTracksMap by viewModel.cachedTopTracks.collectAsState()
            val topAlbumsMap by viewModel.cachedTopAlbums.collectAsState()
            val topArtistsMap by viewModel.cachedTopArtists.collectAsState()

            // 2) В зависимости от selectedCategory берём нужный список
            val currentList = when (selectedCategory) {
                TopCategory.Tracks -> {
                    val aggregates = topTracksMap[selectedPeriod] ?: emptyList()
                    aggregates.map { agg ->
                        TopTrackInfo(
                            id = agg.trackName,
                            title = "${agg.trackName} - ${agg.artistName}",
                            minutes = agg.totalMinutes,
                            listens = agg.playCount,
                            coverUrl = agg.coverUrl
                        )
                    }
                }
                TopCategory.Albums -> {
                    val aggregates = topAlbumsMap[selectedPeriod] ?: emptyList()
                    aggregates.map { agg ->
                        TopTrackInfo(
                            id = agg.albumName,
                            title = "${agg.albumName} - ${agg.artistName}",
                            minutes = agg.totalMinutes,
                            listens = agg.playCount,
                            coverUrl = agg.coverUrl
                        )
                    }
                }
                TopCategory.Artists -> {
                    val aggregates = topArtistsMap[selectedPeriod] ?: emptyList()
                    aggregates.map { agg ->
                        TopTrackInfo(
                            id = agg.artistName,
                            title = agg.artistName,
                            minutes = agg.totalMinutes,
                            listens = agg.playCount,
                            coverUrl = agg.coverUrl
                        )
                    }
                }
                TopCategory.Genres -> emptyList() // пока игнорируем
            }

            // 3) Передаём currentList в TopSectionScreen
            TopSectionScreen(
                tracks = currentList,
                selectedCategory = selectedCategory,
                selectedPeriod = selectedPeriod,
                onCategorySelected = { selectedCategory = it },
                onPeriodSelected = { selectedPeriod = it }
            )
        }

        composable("importHistory") {
            ImportHistoryScreen(
                onBack = { navController.popBackStack() },
                onPickFile = onPickFile,
                importedFiles = importedFiles
            )
        }
    }
}

