package com.example.spotan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.spotan.data.AppDatabase
import com.example.spotan.navigation.AppNavHost
import com.example.spotan.screens.*
import com.example.spotan.ui.theme.SpotanTheme
import java.io.BufferedReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    private lateinit var spotifyAuthManager: SpotifyAuthManager
    private var isAuthorized by mutableStateOf(false)
    private var token: String? = null
    private var importResult by mutableStateOf<List<String>>(emptyList())

    // 1) Создаём/инициализируем базу данных Room (ленивая инициализация).
    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "my_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    // 2) Создаём фабрику для MainViewModel и передаём туда playedItemDao и token (или "fake_token")
    private val mainViewModel by viewModels<MainViewModel> {
        MainViewModelFactory(
            token = token ?: "fake_token",
            playedItemDao = db.playedItemDao()
        )
    }

    // Лаунчер для выбора файла (ZIP)
    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleZipFile(uri)
        } else {
            Toast.makeText(this, "Файл не выбран", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleZipFile(uri: Uri) {
        mainViewModel.importHistoryFromZip(uri, contentResolver)
    }


    private val authResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            spotifyAuthManager.handleAuthorizationResponse(result.data!!) { success, error ->
                if (success) {
                    isAuthorized = true
                    token = spotifyAuthManager.getAccessToken()
                    Toast.makeText(this, "Авторизация успешна!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка авторизации: $error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем SpotifyAuthManager
        spotifyAuthManager = SpotifyAuthManager(this)
        token = spotifyAuthManager.getAccessToken()
        isAuthorized = !token.isNullOrEmpty()

        setContent {
            SpotanTheme {
                if (isAuthorized && !token.isNullOrEmpty()) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        viewModel = mainViewModel,
                        spotifyAuthManager = spotifyAuthManager,
                        onLogout = {
                            spotifyAuthManager.logout()
                            isAuthorized = false
                            token = null
                        },
                        onNavigateToImportHistory = { navController.navigate("importHistory") },
                        onPickFile = { openDocumentLauncher.launch(arrayOf("application/zip")) },
                        onNavigateToStats = { timePeriod ->
                            if (timePeriod == "year") {
                                navController.navigate("yearlyStats")
                            } else {
                                Toast.makeText(this, "Переход: $timePeriod", Toast.LENGTH_SHORT).show()
                            }
                        },
                        importedFiles = importResult
                    )
                } else {
                    AuthorizationScreen(
                        onAuthorize = {
                            val authIntent = spotifyAuthManager.getAuthorizationRequestIntent()
                            authResultLauncher.launch(authIntent)
                        }
                    )
                }
            }
        }
    }
}
