package com.example.spotan.screens

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotan.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.example.spotan.utils.firstCover
open class MainViewModel(
    private val token: String,
    private val playedItemDao: PlayedItemDao // <-- Добавляем DAO
) : ViewModel() {

    // 1) Полная история (импорт + всё остальное).
    protected val _listeningHistory = MutableStateFlow<List<PlayedItem>>(emptyList())
    val listeningHistory: StateFlow<List<PlayedItem>> = _listeningHistory

    // 2) Ежедневная (или недавняя) история
    private val _todaysHistory = MutableStateFlow<List<PlayedItem>>(emptyList())
    val todaysHistory: StateFlow<List<PlayedItem>> = _todaysHistory

    // Остальные поля
    protected val _topTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    open val topTracks: StateFlow<List<SpotifyTrack>> = _topTracks

    private val _trendsInfo = MutableStateFlow<TrendsInfo?>(null)
    val trendsInfo: StateFlow<TrendsInfo?> = _trendsInfo
    private val _cachedRecommendations = MutableStateFlow<Map<String, List<Recommendation>>>(emptyMap())
    val cachedRecommendations: StateFlow<Map<String, List<Recommendation>>> = _cachedRecommendations


    private val _topAlbums = MutableStateFlow<List<AlbumAggregate>>(emptyList())
    val topAlbums: StateFlow<List<AlbumAggregate>> = _topAlbums

    private val _currentlyPlaying = MutableStateFlow<CurrentlyPlayingResponse?>(null)
    val currentlyPlaying: StateFlow<CurrentlyPlayingResponse?> = _currentlyPlaying

    private val _topArtists = MutableStateFlow<List<SpotifyArtistFull>>(emptyList())
    val topArtists: StateFlow<List<SpotifyArtistFull>> = _topArtists

    private val _userProfile = MutableStateFlow<SpotifyUserProfile?>(null)
    val userProfile: StateFlow<SpotifyUserProfile?> = _userProfile
    private val _cachedTopTracks = MutableStateFlow<Map<TopPeriod, List<TrackAggregate>>>(emptyMap())
    val cachedTopTracks: StateFlow<Map<TopPeriod, List<TrackAggregate>>> = _cachedTopTracks
    private val _cachedTopAlbums = MutableStateFlow<Map<TopPeriod, List<AlbumAggregateLocal>>>(emptyMap())
    val cachedTopAlbums: StateFlow<Map<TopPeriod, List<AlbumAggregateLocal>>> = _cachedTopAlbums
    private val _cachedTopArtists = MutableStateFlow<Map<TopPeriod, List<ArtistAggregate>>>(emptyMap())
    val cachedTopArtists: StateFlow<Map<TopPeriod, List<ArtistAggregate>>> = _cachedTopArtists
    // Кэш для годовой статистики (например, список пар: (месяц, число прослушиваний))
    private val _cachedYearlyStats = MutableStateFlow<Map<Int, Triple<List<Pair<String, Int>>, List<Pair<String, Int>>, Map<String, Int>>>>(emptyMap())
    val cachedYearlyStats: StateFlow<Map<Int, Triple<List<Pair<String, Int>>, List<Pair<String, Int>>, Map<String, Int>>>> get() = _cachedYearlyStats

// Аналогично для месячной или недельной статистики – если требуется.


    private val spotifyApi: SpotifyApi
    private suspend fun ensureImagesAndRecalc() {
        fillMissingAlbumDataForTracksAndAlbums()
        recalcAllTop()
    }
    init {
        // 1) Инициализация Retrofit
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            })
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        spotifyApi = retrofit.create(SpotifyApi::class.java)
        viewModelScope.launch {
            listeningHistory.collect { newList ->
                // Когда список меняется, пересчитаем кэш "Мой топ"
                recalcAllTop()
            }
        }

        // 2) При старте загружаем из локальной БД
        viewModelScope.launch {
          //  playedItemDao.clearAll()
            try {

                val dbItems = playedItemDao.getAll()
                val converted = dbItems.map { dbItem ->
                    PlayedItem(
                        track = SpotifyTrack(
                            name = dbItem.trackName,
                            artists = listOf(SpotifyArtist(name = dbItem.artistName)),
                            durationMs = dbItem.durationMs
                        ),
                        playedAt = dbItem.playedAt
                    )
                }

                _listeningHistory.value = converted
                Log.d("MainViewModel", "Загрузили ${converted.size} треков из локальной БД")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainViewModel", "Ошибка при чтении из БД", e)
            }
        }
        loadFromDatabase()

        // Инициируем запросы
        fetchTopTracks()
        fetchTopArtists()
        fetchTopAlbums()
        fetchUserProfile()

        // Периодически обновляем "сегодняшнюю" историю и тренды
        viewModelScope.launch {
            while (true) {
                delay(60000L)  // 1 минута
                fetchTrends()
                refreshTodaysHistory()  // обновляем лишь сегодняшние треки
                fetchTopArtists()
            }
        }
        viewModelScope.launch {
            listeningHistory.collect { newList ->
                recalcYearlyStatsCache()
                // Можно вызвать и пересчёт для месячных/недельных, если нужно
            }
        }
        // Периодическое обновление текущего трека (каждые 5 секунд)
        viewModelScope.launch {
            while (true) {
                try {
                    _currentlyPlaying.value = spotifyApi.getCurrentlyPlaying()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000)
            }
        }
    }

    // ====== FETCH API FUNCTIONS ======
    private suspend fun recalcAllTop() = withContext(Dispatchers.Default) {
        // Для треков
        val monthTracks = getTopTracksFromHistory(limit = 50, period = TopPeriod.Month)
        val halfYearTracks = getTopTracksFromHistory(limit = 50, period = TopPeriod.HalfYear)
        val allTimeTracks = getTopTracksFromHistory(limit = 50, period = TopPeriod.AllTime)

        // Для альбомов
        val monthAlbums = getTopAlbumsFromHistory(limit = 50, period = TopPeriod.Month)
        val halfYearAlbums = getTopAlbumsFromHistory(limit = 50, period = TopPeriod.HalfYear)
        val allTimeAlbums = getTopAlbumsFromHistory(limit = 50, period = TopPeriod.AllTime)

        // Для артистов
        val monthArtists = getTopArtistsFromHistory(limit = 50, period = TopPeriod.Month)
        val halfYearArtists = getTopArtistsFromHistory(limit = 50, period = TopPeriod.HalfYear)
        val allTimeArtists = getTopArtistsFromHistory(limit = 50, period = TopPeriod.AllTime)

        // Собираем в мапы
        val tracksMap = mapOf(
            TopPeriod.Month to monthTracks,
            TopPeriod.HalfYear to halfYearTracks,
            TopPeriod.AllTime to allTimeTracks
        )
        val albumsMap = mapOf(
            TopPeriod.Month to monthAlbums,
            TopPeriod.HalfYear to halfYearAlbums,
            TopPeriod.AllTime to allTimeAlbums
        )
        val artistsMap = mapOf(
            TopPeriod.Month to monthArtists,
            TopPeriod.HalfYear to halfYearArtists,
            TopPeriod.AllTime to allTimeArtists
        )

        // Записываем в StateFlow
        _cachedTopTracks.value = tracksMap
        _cachedTopAlbums.value = albumsMap
        _cachedTopArtists.value = artistsMap
    }
    private suspend fun recalcYearlyStatsCache() = withContext(Dispatchers.Default) {
        // Для каждого года, который встречается в listeningHistory:
        val years = getAvailableYears()
        val cache = mutableMapOf<Int, Triple<List<Pair<String, Int>>, List<Pair<String, Int>>, Map<String, Int>>>()
        for (year in years) {
            cache[year] = getYearlyStats(year)
        }
        _cachedYearlyStats.value = cache
    }
    private fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val profile = spotifyApi.getCurrentUserProfile()
                _userProfile.value = profile
                Log.d("ViewModel", "Получен профиль пользователя: ${profile.displayName}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun loadFromDatabase() {
        viewModelScope.launch {
            val dbItems = playedItemDao.getRecent(100)
            dbItems.forEach { Log.d("CheckDB", "Row id=${it.id}, cover=${it.coverUrl}") }
            val converted = dbItems.map { dbItem ->
                val artist = SpotifyArtist(
                    name = dbItem.artistName,
                    coverUrl = dbItem.artistCoverUrl
                )

                val track = SpotifyTrack(
                    name = dbItem.trackName,
                    durationMs = dbItem.durationMs,
                    artists = listOf(artist), // 👈 используем artist с coverUrl
                    album = SpotifyAlbum(
                        name = "",
                        images = listOf(SpotifyImage(url = dbItem.coverUrl ?: ""))
                    )
                )

                PlayedItem(
                    track = track,
                    playedAt = dbItem.playedAt
                )
            }

            _listeningHistory.value = converted
            Log.d("MainViewModel", "Загружено из БД: ${converted.size} треков")
        }
    }


    private suspend fun fetchArtistImages(artistIds: List<String>): Map<String, String> {
        val artistMap = mutableMapOf<String, String>()
        val chunks = artistIds.distinct().chunked(50)

        for (chunk in chunks) {
            try {
                val idsParam = chunk.joinToString(",")
                val response = spotifyApi.getArtists(idsParam)
                response.artists?.forEach { artist ->
                    val id = artist.id ?: return@forEach
                    val imageUrl = artist.images?.firstOrNull()?.url
                    if (!imageUrl.isNullOrEmpty()) {
                        artistMap[id] = imageUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return artistMap
    }

    private fun fetchTopArtists() {
        viewModelScope.launch {
            try {
                val response: SpotifyTopArtistsResponse = spotifyApi.getTopArtists(
                    timeRange = "short_term",
                    limit = 10
                )
                _topArtists.value = response.items
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchTopAlbums() {
        viewModelScope.launch {
            try {
                val response: SpotifyTopTracksResponse = spotifyApi.getTopTracks(
                    timeRange = "short_term",
                    limit = 50
                )
                val aggregated = aggregateTopAlbums(response.items)
                _topAlbums.value = aggregated
                Log.d("ViewModel", "Получено ${aggregated.size} альбомов")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun fetchTrends() {
        val now = Instant.now()
        val todayThreshold = now.minus(24, ChronoUnit.HOURS)
        val yesterdayThreshold = now.minus(48, ChronoUnit.HOURS)

        // Для трендов можно использовать todaysHistory, но здесь, для примера, берём _listeningHistory
        val allItems = _todaysHistory.value

        var todayCount = 0
        var todayDurationMs = 0
        var yesterdayCount = 0
        var yesterdayDurationMs = 0

        allItems.forEach { item ->
            val playedAt = Instant.parse(item.playedAt)
            when {
                playedAt.isAfter(todayThreshold) -> {
                    todayCount++
                    todayDurationMs += item.track.durationMs ?: 0
                }
                playedAt.isAfter(yesterdayThreshold) && playedAt.isBefore(todayThreshold) -> {
                    yesterdayCount++
                    yesterdayDurationMs += item.track.durationMs ?: 0
                }
            }
        }

        val listensChange = calculateChange(todayCount, yesterdayCount)
        val minutesToday = todayDurationMs / 60000
        val minutesYesterday = yesterdayDurationMs / 60000
        val minutesChange = calculateChange(minutesToday, minutesYesterday)

        _trendsInfo.value = TrendsInfo(
            listens = todayCount,
            listensChange = listensChange,
            minutes = minutesToday,
            minutesChange = minutesChange
        )

        Log.d("Trends", "Today: $todayCount listens ($minutesToday min), Yesterday: $yesterdayCount ($minutesYesterday min)")
    }
    private fun fetchTopTracks() {
        viewModelScope.launch {
            try {
                val response: SpotifyTopTracksResponse = spotifyApi.getTopTracks(
                    timeRange = "short_term",
                    limit = 5
                )
                _topTracks.value = response.items
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Обновляем только «сегодняшние» треки (не затирая _listeningHistory).
     */
    private fun refreshTodaysHistory() {
        viewModelScope.launch {
            try {
                val startOfDay = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()

                val allItems = mutableListOf<PlayedItem>()
                var before: Long? = null

                do {
                    val response = spotifyApi.getRecentlyPlayed(limit = 50, before = before)
                    allItems.addAll(response.items)
                    before = response.cursors?.before?.toLongOrNull()
                } while (before != null)

                // Фильтруем только за сегодня
                val filtered = allItems.filter {
                    Instant.parse(it.playedAt).isAfter(startOfDay)
                }
                _todaysHistory.value = filtered
                Log.d("History", "Сегодня: ${filtered.size} треков")

                // Добавляем новые "сегодняшние" треки к _listeningHistory
                val updatedAllHistory = _listeningHistory.value.toMutableList()
                updatedAllHistory.addAll(filtered)
                _listeningHistory.value = updatedAllHistory

                // Сохраняем в локальную БД вместе с обложками
                val dbItems = filtered.map { item ->
                    val albumCoverUrl = item.track.album?.images?.firstOrNull()?.url ?: ""
                    val artistCoverUrl = item.track.artists?.firstOrNull()?.coverUrl
                        ?: item.track.artists?.firstOrNull()?.name?.let { fetchArtistCoverByName(it) }
                    DbPlayedItem(
                        trackName = item.track.name ?: "",
                        artistName = item.track.artists?.firstOrNull()?.name ?: "",
                        durationMs = item.track.durationMs ?: 0,
                        playedAt = item.playedAt,
                        coverUrl = albumCoverUrl,
                        artistCoverUrl = artistCoverUrl
                    )
                }
                playedItemDao.insertAll(dbItems)

            } catch (e: Exception) {
                Log.e("History", "Error fetching todaysHistory", e)
            }
        }
    }


    // ====== IMPORT FUNCTIONS ======

    /**
     * Импорт JSON с историей и добавление в _listeningHistory + сохранение в БД.
     */
    fun importHistoryFromJson(json: String) {
        try {
            val gson = Gson()
            val listType = object : TypeToken<List<StreamingHistoryItem>>() {}.type
            val streamingHistory: List<StreamingHistoryItem> = gson.fromJson(json, listType)

            val formatterInput = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

            val newHistoryItems = streamingHistory.map { item ->
                val localDateTime = LocalDateTime.parse(item.endTime, formatterInput)
                val instant = localDateTime.toInstant(ZoneOffset.UTC)
                val track = SpotifyTrack(
                    name = item.trackName,
                    artists = listOf(SpotifyArtist(name = item.artistName)),
                    durationMs = item.msPlayed
                )
                PlayedItem(
                    track = track,
                    playedAt = instant.toString()
                )
            }

            val updated = _listeningHistory.value.toMutableList()
            updated.addAll(newHistoryItems)
            _listeningHistory.value = updated
            Log.d("MainViewModel", "Импортировано ${newHistoryItems.size}, итого: ${_listeningHistory.value.size}")

            // Пишем новые записи в БД
            viewModelScope.launch {
                val dbItems = newHistoryItems.map { playedItem ->
                    val coverUrl = playedItem.track.album?.images?.firstOrNull()?.url ?: ""

                    DbPlayedItem(
                        trackName = playedItem.track.name ?: "",
                        artistName = playedItem.track.artists?.firstOrNull()?.name ?: "",
                        durationMs = playedItem.track.durationMs ?: 0,
                        playedAt = playedItem.playedAt,
                        coverUrl = coverUrl
                    )
                }
                playedItemDao.insertAll(dbItems)
            }


        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MainViewModel", "Ошибка импорта JSON", e)
        }
    }
    fun fetchRecommendationsForTimePeriod(
        period: RecommendationPeriod,
        onResult: (List<SpotifyTrack>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Пример: берем последние 30 дней из истории и извлекаем seed-треки и seed-артистов
                val threshold = Instant.now().minus(30, ChronoUnit.DAYS)
                val recentTracks = _listeningHistory.value.filter { Instant.parse(it.playedAt) >= threshold }

                // Используем уникальные trackId и artistId для seed
                val seedTrackIds = recentTracks.mapNotNull { it.track.id }.distinct().take(3)
                val seedArtistIds = recentTracks.mapNotNull { it.track.artists?.firstOrNull()?.id }.distinct().take(2)

                // Если оба списка пусты, можно попробовать задать фиксированные seed_genres, например:
                val seedTracksParam = if (seedTrackIds.isNotEmpty()) seedTrackIds.joinToString(",") else null
                val seedArtistsParam = if (seedArtistIds.isNotEmpty()) seedArtistIds.joinToString(",") else null

                // Если ни треки, ни артисты не заданы, зададим хотя бы какой-нибудь фиксированный жанр:
                val seedGenresParam = if (seedTracksParam == null && seedArtistsParam == null) "pop" else null

                // Пример настроек targetEnergy и targetValence по временам суток:
                val (targetEnergy, targetValence) = when (period) {
                    RecommendationPeriod.Morning -> Pair(0.5, 0.7)
                    RecommendationPeriod.Work -> Pair(0.7, 0.5)
                    RecommendationPeriod.Night -> Pair(0.3, 0.3)
                }
                Log.d("RecSeed", "seedTracksParam=$seedTracksParam, seedArtistsParam=$seedArtistsParam, seedGenresParam=$seedGenresParam")
                val recommendationsResponse = spotifyApi.getRecommendations(
                    seedTracks = seedTracksParam,
                    seedArtists = seedArtistsParam,
                    genre = seedGenresParam,
                    targetEnergy = targetEnergy.toDouble(),
                    targetValence = targetValence.toDouble()
                )
                onResult(recommendationsResponse.tracks)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(emptyList())
            }
        }
    }
    fun fetchRecommendationsForTrack(trackKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.recommendationApi.getRecommendations(
                    trackKey = trackKey,
                    alpha = 0.5f,
                    topK = 5
                )
                // Теперь response.recommendations содержит список рекомендованных треков
                // Например, можно сохранить их в отдельное состояние:
                Log.d("Recommendation", "Получены рекомендации: ${response.recommendations}")
                // Если нужно, переключись на Dispatchers.Main для обновления UI
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Recommendation", "Ошибка получения рекомендаций", e)
            }
        }
    }




    fun convertDbItemToPlayedItem(dbItem: DbPlayedItem): PlayedItem {
        // Создаем новый SpotifyTrack, заполняя coverUrl из DbPlayedItem
        val track = SpotifyTrack(
            id = null, // Если нет trackId, или можно добавить отдельное поле для него
            name = dbItem.trackName,
            artists = listOf(SpotifyArtist(name = dbItem.artistName)),
            durationMs = dbItem.durationMs,
            album = SpotifyAlbum(
                name = "",  // Можно оставить пустым, если нет других данных
                images = listOf(SpotifyImage(url = dbItem.coverUrl ?: ""))
            )
        )
        return PlayedItem(
            track = track,
            playedAt = dbItem.playedAt
        )
    }

    fun importHistoryFromZip(zipUri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry: ZipEntry? = zipInputStream.nextEntry

                    val allImportedItems = mutableListOf<PlayedItem>()
                    while (entry != null) {
                        val fileName = entry.name
                        if (fileName.endsWith(".json", ignoreCase = true)) {
                            val jsonContent = zipInputStream.bufferedReader().readText()
                            // Парсим JSON
                            val gson = Gson()
                            val listType = object : TypeToken<List<DetailedStreamingHistoryItem>>() {}.type
                            val detailedHistory: List<DetailedStreamingHistoryItem> =
                                gson.fromJson(jsonContent, listType)

                            // Превращаем DetailedStreamingHistoryItem -> PlayedItem
                            val parsedItems = detailedHistory.mapNotNull { convertDetailedItemToPlayedItem(it) }

                            // === ДОзагружаем обложки ===
                            // Если в convertDetailedItemToPlayedItem мы прописали track.id,
                            // тогда сейчас у нас есть playedItem.track.id для каждого трека.
                            fillAlbumDataForItems(parsedItems)

                            allImportedItems.addAll(parsedItems)
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.close()

                    // Теперь у allImportedItems есть album.images (если удалось дозагрузить)
                    if (allImportedItems.isNotEmpty()) {
                        // 1) Объединяем с текущим _listeningHistory
                        val updated = _listeningHistory.value.toMutableList()
                        updated.addAll(allImportedItems)
                        _listeningHistory.value = updated

                        // 2) Сохраняем в БД
                        saveToDatabase(allImportedItems)

                        Log.d("MainViewModel", "Импортировано ${allImportedItems.size} треков")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Ошибка импорта ZIP", e)
            }
        }
    }
    private suspend fun fillAlbumDataForItems(items: List<PlayedItem>) {
        // 1) Собираем все trackId
        val allTrackIds = items.mapNotNull { it.track.id }.toSet()
        if (allTrackIds.isEmpty()) return

        // 2) Разбиваем на пачки по 50 (Spotify ограничивает макс. 50 ID за один запрос)
        val chunkedIds = allTrackIds.chunked(50)

        // Для хранения результата: trackId -> SpotifyTrack
        val trackMap = mutableMapOf<String, SpotifyTrack>()

        for (chunk in chunkedIds) {
            // Превращаем List<String> в "id1,id2,id3"
            val idsParam = chunk.joinToString(",")
            try {
                // Запрашиваем пачку
                val response = spotifyApi.getTracksByIds(idsParam)
                Log.d("BatchLoad", "Response = ${response}")
                val returnedTracks = response.tracks ?: emptyList()
                // Сохраняем в trackMap по id
                for (t in returnedTracks) {
                    val tId = t.id
                    if (!tId.isNullOrBlank()) {
                        trackMap[tId] = t
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3) Теперь у нас есть trackMap с заполненными album.images
        // Проходимся по items и, если trackMap.containsKey(id), вставляем эти данные
        for (playedItem in items) {
            val tId = playedItem.track.id ?: continue
            val fullTrack = trackMap[tId] ?: continue
            // Копируем поля, которые нам нужны (album, artists, durationMs, ...)
            playedItem.track.album = fullTrack.album
            playedItem.track.artists = fullTrack.artists
            playedItem.track.durationMs = fullTrack.durationMs
            // (если хотите перезаписать name, тоже можно)
        }
        val artistIds = trackMap.values.flatMap { it.artists ?: emptyList() }
            .mapNotNull { it.id }
        val artistImageMap = fetchArtistImages(artistIds)

        for (playedItem in items) {
            val tId = playedItem.track.id ?: continue
            val fullTrack = trackMap[tId] ?: continue

            playedItem.track.album = fullTrack.album
            playedItem.track.artists = fullTrack.artists
            playedItem.track.durationMs = fullTrack.durationMs

            val firstArtist = fullTrack.artists?.firstOrNull()
            val artistCoverUrl = firstArtist?.id?.let { artistImageMap[it] }
            firstArtist?.coverUrl = artistCoverUrl
        }

    }
    private suspend fun fillMissingAlbumDataForTracksAndAlbums() = withContext(Dispatchers.Default) {
        // Фильтруем записи, у которых есть track.id, но поле album не заполнено или нет изображений
        val itemsToUpdate = _listeningHistory.value.filter { playedItem ->
            val track = playedItem.track
            track.id != null && (track.album == null || track.album?.images.isNullOrEmpty())
        }
        if (itemsToUpdate.isEmpty()) return@withContext

        // Собираем уникальные trackId
        val trackIds = itemsToUpdate.mapNotNull { it.track.id }.toSet()
        val trackMap = mutableMapOf<String, SpotifyTrack>()

        // Разбиваем список trackIds на партии до 50
        val chunks = trackIds.chunked(50)
        for (chunk in chunks) {
            val idsParam = chunk.joinToString(",")
            try {
                val response = spotifyApi.getTracksByIds(idsParam)
                response.tracks?.forEach { t ->
                    t.id?.let { trackMap[it] = t }
                }
                kotlinx.coroutines.delay(200) // задержка для rate limit
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Обновляем объекты PlayedItem с новыми данными
        itemsToUpdate.forEach { playedItem ->
            val tid = playedItem.track.id ?: return@forEach
            val fullTrack = trackMap[tid] ?: return@forEach
            playedItem.track.album = fullTrack.album
            playedItem.track.artists = fullTrack.artists
            playedItem.track.durationMs = fullTrack.durationMs
        }

        // Обновляем _listeningHistory (при необходимости, обновление "на месте" - если объекты mutable, достаточно просто обновить)
        _listeningHistory.value = _listeningHistory.value.toList()

        // Обновляем записи в базе: можно вызвать saveToDatabase() для этих записей, если хотите сохранить дозагруженные обложки
        saveToDatabase(itemsToUpdate)
    }




    private fun convertDetailedItemToPlayedItem(item: DetailedStreamingHistoryItem): PlayedItem? {
        val ts = item.ts ?: return null
        val instant = runCatching { Instant.parse(ts) }.getOrNull() ?: return null

        val trackName = item.master_metadata_track_name ?: "Unknown Track"
        val artistName = item.master_metadata_album_artist_name ?: "Unknown Artist"
        val msPlayed = item.ms_played ?: 0

        val trackId = parseTrackId(item.spotify_track_uri)
        val track = SpotifyTrack(
            id = trackId,
            name = trackName,
            artists = listOf(SpotifyArtist(name = artistName)),
            durationMs = msPlayed.toInt()
        )

        return PlayedItem(track = track, playedAt = instant.toString())
    }


    /** Выделяем часть после "spotify:track:" */
    private fun parseTrackId(uri: String?): String? {
        if (uri.isNullOrEmpty()) return null
        if (!uri.startsWith("spotify:track:")) return null
        return uri.substringAfter("spotify:track:")
    }

    private suspend fun saveToDatabase(newItems: List<PlayedItem>) {
        val dbItems = newItems.map { playedItem ->
            val artist = playedItem.track.artists?.firstOrNull()
            val artistName = artist?.name ?: ""
            val albumCoverUrl = playedItem.track.album?.images?.firstOrNull()?.url ?: ""

            // Загружаем обложку артиста, если она не была установлена ранее
            val artistCoverUrl = artist?.coverUrl ?: fetchArtistCoverByName(artistName)

         //   Log.d("ArtistCover", "Для артиста $artistName обложка = $artistCoverUrl")

            DbPlayedItem(
                trackName = playedItem.track.name ?: "",
                artistName = artistName,
                durationMs = playedItem.track.durationMs ?: 0,
                playedAt = playedItem.playedAt,
                coverUrl = albumCoverUrl,
                artistCoverUrl = artistCoverUrl
            )
        }
        playedItemDao.insertAll(dbItems)
    }





    // ====== STATS ======
    // Здесь остался ваш исходный код (getYearlyStats, getMonthlyStats, getWeeklyStats и т.д.)
    // Ничего менять не нужно, всё работает на основе _listeningHistory.

    private fun calculateChange(current: Int, previous: Int): String {
        return if (previous > 0) {
            val change = ((current - previous).toDouble() / previous * 100).toInt()
            if (change >= 0) "+$change%" else "$change%"
        } else {
            "N/A"
        }
    }

    private fun aggregateTopAlbums(tracks: List<SpotifyTrack>): List<AlbumAggregate> {
        return tracks.filter { it.album != null && !it.album!!.name.isNullOrEmpty() }
            .groupBy { it.album!!.name!! }
            .map { (albumName, trackList) ->
                val sample = trackList.first()
                AlbumAggregate(
                    albumId = albumName,
                    albumName = albumName,
                    albumImages = sample.album!!.images ?: emptyList(),
                    artistName = sample.artists?.firstOrNull()?.name ?: "Unknown Artist",
                    trackCount = trackList.size
                )
            }
    }
    fun getWeeklyStats(year: Int, week: Int): Triple<
            List<Pair<String, Int>>,
            List<Pair<String, Int>>,
            Map<String, Int>
            > {
        // 1) Фильтруем треки, попадающие в нужный год и нужную «номер недели»
        val itemsForWeek = _listeningHistory.value.filter { playedItem ->
            val instant = runCatching { Instant.parse(playedItem.playedAt) }.getOrNull() ?: return@filter false
            val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val itemYear = dateTime.year
            // Номер недели по ISO (понедельник – первый день)
            val itemWeek = dateTime.get(WeekFields.ISO.weekOfWeekBasedYear())
            (itemYear == year) && (itemWeek == week)
        }

        // 2) Для наглядности группируем/агрегируем статистику по дням недели.
        //    dayOfWeek.value даёт 1..7 (1 = Понедельник, 7 = Воскресенье).
        val listensMap = mutableMapOf<Int, Int>()
        val timeMap = mutableMapOf<Int, Int>()

        itemsForWeek.forEach { item ->
            val instant = Instant.parse(item.playedAt)
            val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val dayOfWeek = localDateTime.dayOfWeek.value  // 1..7
            listensMap[dayOfWeek] = (listensMap[dayOfWeek] ?: 0) + 1
            val minutes = (item.track.durationMs ?: 0) / 60000
            timeMap[dayOfWeek] = (timeMap[dayOfWeek] ?: 0) + minutes
        }

        // 3) Для удобства подготовим список названий дней недели.
        //    Можно, например, сделать массив [“Пн”, “Вт”, …, “Вс”] и брать index = dayOfWeek-1.
        val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        // 4) Формируем выходные данные — пары (название_дня, число) для графика
        val dailyData = (1..7).map { dayOfWeek ->
            dayNames[dayOfWeek - 1] to (listensMap[dayOfWeek] ?: 0)
        }
        val dailyTimeData = (1..7).map { dayOfWeek ->
            dayNames[dayOfWeek - 1] to (timeMap[dayOfWeek] ?: 0)
        }

        // 5) Если нужно, формируем жанровую статистику (пустая карта, если не реализовано)
        val genreData = emptyMap<String, Int>()

        return Triple(dailyData, dailyTimeData, genreData)
    }

    fun getAvailableWeeks(year: Int): List<Int> {
        // Аналогично monthlyStats: собираем из истории, какие "weekOfWeekBasedYear" встречаются
        return _listeningHistory.value.mapNotNull { playedItem ->
            val instant = runCatching { Instant.parse(playedItem.playedAt) }.getOrNull() ?: return@mapNotNull null
            val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            if (dateTime.year == year) dateTime.get(WeekFields.ISO.weekOfWeekBasedYear()) else null
        }.distinct().sorted()
    }
    fun getAvailableYears(): List<Int> {
        return _listeningHistory.value.mapNotNull { playedItem ->
            try {
                Instant.parse(playedItem.playedAt)
                    .atZone(ZoneId.systemDefault())
                    .year
            } catch (e: Exception) {
                null
            }
        }.distinct().sorted()
    }
    fun getYearlyStats(year: Int): Triple<List<Pair<String, Int>>, List<Pair<String, Int>>, Map<String, Int>> {
        val itemsForYear = _listeningHistory.value.filter { playedItem ->
            try {
                val y = Instant.parse(playedItem.playedAt)
                    .atZone(ZoneId.systemDefault()).year
                y == year
            } catch (e: Exception) {
                false
            }
        }

        val monthNames = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
        val listensMap = mutableMapOf<Int, Int>()
        val timeMap = mutableMapOf<Int, Int>()

        itemsForYear.forEach { item ->
            try {
                val month = Instant.parse(item.playedAt)
                    .atZone(ZoneId.systemDefault()).monthValue
                listensMap[month] = (listensMap[month] ?: 0) + 1
                val minutes = (item.track.durationMs ?: 0) / 60000
                timeMap[month] = (timeMap[month] ?: 0) + minutes
            } catch (e: Exception) { /* ignore */ }
        }

        val monthlyData = (1..12).map { month ->
            monthNames[month - 1] to (listensMap[month] ?: 0)
        }
        val monthlyTimeData = (1..12).map { month ->
            monthNames[month - 1] to (timeMap[month] ?: 0)
        }

        val genreData = emptyMap<String, Int>()
        return Triple(monthlyData, monthlyTimeData, genreData)
    }
    fun getMonthlyStats(year: Int, month: Int): Triple<
            List<Pair<String, Int>>,
            List<Pair<String, Int>>,
            Map<String, Int>
            > {
        // Фильтруем все треки, которые относятся к этому году и месяцу
        val itemsForMonth = _listeningHistory.value.filter { playedItem ->
            try {
                val instant = Instant.parse(playedItem.playedAt)
                val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
                (localDateTime.year == year) && (localDateTime.monthValue == month)
            } catch (e: Exception) {
                false
            }
        }

        // Узнаём, сколько дней в выбранном месяце (с учётом високосных годов)
        val ym = YearMonth.of(year, month)
        val daysInMonth = ym.lengthOfMonth()

        // Счётчики прослушиваний и суммарных минут по дням
        val listensMap = mutableMapOf<Int, Int>()
        val timeMap = mutableMapOf<Int, Int>()

        // Наполняем listensMap[day] и timeMap[day]
        itemsForMonth.forEach { item ->
            val instant = Instant.parse(item.playedAt)
            val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val day = localDateTime.dayOfMonth
            // +1 к количеству прослушиваний
            listensMap[day] = (listensMap[day] ?: 0) + 1

            // Считаем минуты
            val minutes = (item.track.durationMs ?: 0) / 60000
            timeMap[day] = (timeMap[day] ?: 0) + minutes
        }

        // Формируем списки для графиков: (день, значение)
        // day.toString() -> listensMap[day] (или 0, если нет)
        val dailyData = (1..daysInMonth).map { day ->
            day.toString() to (listensMap[day] ?: 0)
        }
        val dailyTimeData = (1..daysInMonth).map { day ->
            day.toString() to (timeMap[day] ?: 0)
        }

        // Аналогично жанры, если хотите. Пока пусть будет пустая мапа:
        val genreData = emptyMap<String, Int>()

        return Triple(dailyData, dailyTimeData, genreData)
    }
    fun getAllTimeStats(): Triple<
            List<Pair<String, Int>>,     // Годы и кол-во треков (year -> listens)
            List<Pair<String, Int>>,     // Годы и суммарное время в минутах (year -> totalMinutes)
            Map<String, Int>             // Жанры (genre -> кол-во), если нужно
            > {
        // Группируем все PlayedItem по году (year)
        val groupedByYear = _listeningHistory.value.groupBy { playedItem ->
            val instant = runCatching { Instant.parse(playedItem.playedAt) }.getOrNull() ?: return@groupBy null
            instant.atZone(ZoneId.systemDefault()).year
        }.filterKeys { it != null } // убираем возможные null
            .mapKeys { it.key as Int } // преобразуем ключ в Int

        // Сортируем годы по возрастанию
        val sortedYears = groupedByYear.keys.sorted()

        // Список (Год -> количество прослушиваний)
        val yearlyListensData = sortedYears.map { year ->
            val items = groupedByYear[year] ?: emptyList()
            year.toString() to items.size
        }

        // Список (Год -> суммарное время в минутах)
        val yearlyTimeData = sortedYears.map { year ->
            val items = groupedByYear[year] ?: emptyList()
            val totalMinutes = items.sumOf { it.track.durationMs?.div(60000) ?: 0 }
            year.toString() to totalMinutes
        }

        // Если нужно собрать жанровую статистику за всё время:
        // (ПРИМЕР! Здесь genreData пуст, но можно самостоятельно реализовать).
        val genreData = emptyMap<String, Int>()

        return Triple(yearlyListensData, yearlyTimeData, genreData)
    }

    /**
     * Список "доступных" месяцев за весь период (опционально).
     * Например, возвращаем набор всех (year, month), которые есть в истории.
     * Но для простоты можно возвращать 1..12 и год, выбрав сами нужные.
     */
    fun getAvailableMonths(year: Int): List<Int> {
        // Можно получить все monthValue из треков, где год == [year]
        val months = _listeningHistory.value.mapNotNull { playedItem ->
            val instant = runCatching { Instant.parse(playedItem.playedAt) }.getOrNull() ?: return@mapNotNull null
            val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            if (dateTime.year == year) dateTime.monthValue else null
        }.distinct().sorted()
        return months
    }
    /**
     * Агрегат по трекам (считаем playCount, totalMinutes).
     */
    data class TrackAggregate(
        val trackName: String,
        val artistName: String,
        val playCount: Int,
        val totalMinutes: Int,
        val coverUrl: String // возможно, обложка из первого встреченного альбома
    )

    /**
     * Агрегат по альбомам
     */
    data class AlbumAggregateLocal(
        val albumName: String,
        val artistName: String,
        val playCount: Int,
        val totalMinutes: Int,
        val coverUrl: String
    )

    /**
     * Агрегат по артистам
     */
    data class ArtistAggregate(
        val artistName: String,
        val playCount: Int,
        val totalMinutes: Int,
        val coverUrl: String // можно не указывать, если у вас нет обложки артиста
    )

    fun getTopTracksFromHistory(limit: Int = 50, period: TopPeriod): List<TrackAggregate> {
        val now = Instant.now()
        val filteredList = when (period) {
            TopPeriod.Month -> {
                val threshold = now.minus(30, ChronoUnit.DAYS)
                _listeningHistory.value.filter { Instant.parse(it.playedAt) >= threshold }
            }
            TopPeriod.HalfYear -> {
                val threshold = now.minus(180, ChronoUnit.DAYS)
                _listeningHistory.value.filter { Instant.parse(it.playedAt) >= threshold }
            }
            TopPeriod.AllTime -> _listeningHistory.value
        }

        // ВАЖНО: используем filteredList, а не _listeningHistory.value
        val grouped = filteredList.groupBy { item ->
            val trackName = item.track.name ?: ""
            val artistName = item.track.artists?.firstOrNull()?.name ?: ""
            trackName to artistName
        }

        val aggregates = grouped.map { (key, items) ->
            val (trackName, artistName) = key
            val playCount = items.size
            val totalMinutes = items.sumOf { (it.track.durationMs ?: 0) / 60000 }
            val coverUrl = items.firstOrNull()?.track?.album?.images?.firstOrNull()?.url ?: ""
                // Log.d("CoverCheck", "CoverUrl for $trackName is $coverUrl")
            TrackAggregate(
                trackName = trackName,
                artistName = artistName,
                playCount = playCount,
                totalMinutes = totalMinutes,
                coverUrl = coverUrl
            )

        }

        val sorted = aggregates.sortedByDescending { it.playCount }

        return sorted.take(limit)
    }


    fun getTopAlbumsFromHistory(limit: Int = 50, period: TopPeriod): List<AlbumAggregateLocal> {
        val now = Instant.now()
        val filteredList = when (period) {
            TopPeriod.Month -> {
                val threshold = now.minus(30, ChronoUnit.DAYS)
                _listeningHistory.value.filter {
                    Instant.parse(it.playedAt) >= threshold
                }
            }
            TopPeriod.HalfYear -> {
                val threshold = now.minus(180, ChronoUnit.DAYS)
                _listeningHistory.value.filter {
                    Instant.parse(it.playedAt) >= threshold
                }
            }
            TopPeriod.AllTime -> _listeningHistory.value
        }
        val grouped = filteredList.groupBy { item ->
            // альбом + исполнитель (или только альбом)
            val albumName = item.track.album?.name ?: ""
            val artistName = item.track.artists?.firstOrNull()?.name ?: ""
            albumName to artistName
        }

        val aggregates = grouped.map { (key, items) ->
            val albumName = key.first
            val artistName = key.second
            val playCount = items.size
            val totalMinutes = items.sumOf { (it.track.durationMs ?: 0) / 60000 }
            val coverUrl = items.firstOrNull()?.track?.album?.images?.firstOrNull()?.url ?: ""
            AlbumAggregateLocal(
                albumName = albumName,
                artistName = artistName,
                playCount = playCount,
                totalMinutes = totalMinutes,
                coverUrl = coverUrl
            )
        }
        val sorted = aggregates.sortedByDescending { it.playCount }
        return sorted.take(limit)
    }
    private suspend fun fetchArtistCoverByName(name: String): String? {
        return try {
            val result = spotifyApi.searchArtist(name)
            result.artists?.items?.firstOrNull()?.images?.firstOrNull()?.url
        } catch (e: Exception) {
            Log.e("ArtistCover", "Ошибка при поиске артиста $name", e)
            null
        }
    }
    private val _recommendations = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val recommendations: StateFlow<List<SpotifyTrack>> = _recommendations

    fun getTopTracksForWeek(limit: Int = 5, year: Int, week: Int): List<TrackAggregate> {
        // Фильтруем _listeningHistory по заданной неделе (используем getAvailableWeeks и weekOfWeekBasedYear)
        val filtered = _listeningHistory.value.filter { playedItem ->
            val instant = runCatching { Instant.parse(playedItem.playedAt) }.getOrNull() ?: return@filter false
            val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            (localDateTime.year == year) && (localDateTime.get(WeekFields.ISO.weekOfWeekBasedYear()) == week)
        }
        // Группируем по (track name, artist name)
        val grouped = filtered.groupBy {
            val tName = it.track.name ?: ""
            val aName = it.track.artists?.firstOrNull()?.name ?: ""
            tName to aName
        }
        // Формируем агрегаты — чем больше прослушиваний, тем выше агрегат
        val aggregates = grouped.map { (key, items) ->
            val (tName, aName) = key
            val playCount = items.size
            val totalMinutes = items.sumOf { (it.track.durationMs ?: 0) / 60000 }
            val coverUrl = items.firstOrNull()?.track?.album?.images?.firstOrNull()?.url ?: ""
            TrackAggregate(tName, aName, playCount, totalMinutes, coverUrl)
        }
        return aggregates.sortedByDescending { it.playCount }.take(limit)
    }
    suspend fun fetchTrackDetail(query: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        try {
            // Выполняем запрос к Spotify Search API
            val response = spotifyApi.searchTrack(query, type = "track", limit = 1)
            val tracks = response.tracks?.items ?: emptyList()
            if (tracks.isNotEmpty()) {
                return@withContext tracks.first()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ViewModel", "Ошибка при поиске трека для запроса: $query", e)
        }
        return@withContext null
    }

    fun fetchRecommendationsForWeeklyTop() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentYear = LocalDate.now().year
                val currentWeek = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear())
                // Получаем топ-5 треков за неделю
                val weeklyTop = getTopTracksForWeek(limit = 5, year = currentYear, week = currentWeek)
                if (weeklyTop.isNotEmpty()) {
                    val recommendationsBySeed = mutableMapOf<String, List<Recommendation>>()

                    // Для каждого топ-трека формируем seed-строку в формате "artist - song name" (в нижнем регистре)
                    weeklyTop.forEach { seed ->
                        val seedTrackKey = ("${seed.artistName} - ${seed.trackName}").lowercase()
                        Log.d("Recommendation", "Seed track: $seedTrackKey")
                        try {
                            val response = RetrofitInstance.recommendationApi.getRecommendations(
                                trackKey = seedTrackKey,
                                alpha = 0.5f,
                                topK = 5
                            )
                            // Если получили рекомендации, сохраняем их для данного seed
                            recommendationsBySeed[seedTrackKey] = response.recommendations
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            Log.e("Recommendation", "Ошибка для seed $seedTrackKey", ex)
                            recommendationsBySeed[seedTrackKey] = emptyList()
                        }
                    }

                    _cachedRecommendations.value = recommendationsBySeed
                    Log.d("Recommendation", "Рекомендации по seed: $recommendationsBySeed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Recommendation", "Ошибка получения рекомендаций", e)
            }
        }
    }





    fun getTopArtistsFromHistory(limit: Int = 50, period: TopPeriod): List<ArtistAggregate> {
        val now = Instant.now()
        val filteredList = when (period) {
            TopPeriod.Month -> {
                val threshold = now.minus(30, ChronoUnit.DAYS)
                _listeningHistory.value.filter {
                    Instant.parse(it.playedAt) >= threshold
                }
            }
            TopPeriod.HalfYear -> {
                val threshold = now.minus(180, ChronoUnit.DAYS)
                _listeningHistory.value.filter {
                    Instant.parse(it.playedAt) >= threshold
                }
            }
            TopPeriod.AllTime -> _listeningHistory.value
        }

        val allArtistItems = mutableListOf<Pair<String, PlayedItem>>()
        filteredList.forEach { playedItem ->
            val artists = playedItem.track.artists ?: emptyList()
            if (artists.isEmpty()) {
                allArtistItems.add("" to playedItem)
            } else {
                artists.forEach { art ->
                    val artistName = art.name ?: ""
                    allArtistItems.add(artistName to playedItem)
                }
            }
        }
        val artistCoverMap = _listeningHistory.value.associate { item ->
            val name = item.track.artists?.firstOrNull()?.name ?: ""
            val coverUrl = item.track.artists?.firstOrNull()?.coverUrl ?: ""
            name to coverUrl
        }
        // ВАЖНО: группировать нужно именно allArtistItems
        val grouped = allArtistItems.groupBy { pair -> pair.first }  // key = artistName

        val aggregates = grouped.map { (artistName, pairs) ->
            val items = pairs.map { it.second }
            val playCount = items.size
            val totalMinutes = items.sumOf { (it.track.durationMs ?: 0) / 60000 }
            ArtistAggregate(
                artistName = artistName,
                playCount = playCount,
                totalMinutes = totalMinutes,
                coverUrl = artistCoverMap[artistName] ?: ""

            )
        }

        val sorted = aggregates.sortedByDescending { it.playCount }
        return sorted.take(limit)
    }


}
