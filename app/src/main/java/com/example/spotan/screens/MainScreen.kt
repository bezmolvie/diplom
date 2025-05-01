package com.example.spotan.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.spotan.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenWithDrawer(
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onNavigateToStats: (String) -> Unit,
    onImportHistory: () -> Unit
) {
    val currentPlaying by viewModel.currentlyPlaying.collectAsState()
    val trends by viewModel.trendsInfo.collectAsState()
    val topAlbums by viewModel.topAlbums.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val history by viewModel.todaysHistory.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF181818)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1DB954),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("History") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1DB954),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("Top") },
                    selected = false,
                    onClick = { onNavigateToStats("top") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1DB954),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1DB954),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Spotan", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E1E1E).copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                ,
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout", color = Color.White) }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        when (selectedTab) {
            0 -> HomeContent(
                Modifier.padding(paddingValues).fillMaxSize(),
                currentPlaying, trends, topAlbums, topArtists, topTracks
            )
            1 -> HistoryContent(
                Modifier.padding(paddingValues).fillMaxSize(),
                history, onImportHistory
            )
            2 -> ProfileContent(
                Modifier.padding(paddingValues), onNavigateToStats
            )
        }
    }
}

@Composable
fun HomeContent(
    modifier: Modifier,
    currentPlaying: CurrentlyPlayingResponse?,
    trends: TrendsInfo?,
    topAlbums: List<AlbumAggregate>,
    topArtists: List<SpotifyArtistFull>,
    topTracks: List<SpotifyTrack>
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item { HeroPlayingCard(currentPlaying) }
        item { TrendsCard(trends) }
        item { SectionTitle("Top Albums") }
        item { HorizontalCarousel(items = topAlbums) { AlbumCardItem(it) } }
        item { SectionTitle("Top Artists") }
        item { HorizontalCarousel(items = topArtists) { ArtistCardItem(it) } }
        item { SectionTitle("Top Tracks") }
        item { HorizontalCarousel(items = topTracks) { TopTrackItem(it) } }
    }
}

@Composable
fun HeroPlayingCard(current: CurrentlyPlayingResponse?) {
    val track = current?.item
    val progress = (current?.progressMs?.toFloat() ?: 0f) / (track?.durationMs?.toFloat() ?: 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        track?.album?.images?.firstOrNull()?.url?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF121212).copy(alpha = 0.9f)))
                    )
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Сейчас играет",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            Text(
                text = track?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track?.artists?.joinToString { it.name.orEmpty() }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(0xFF1DB954),
                trackColor = Color.Gray
            )
        }
    }
}

@Composable
fun TrendsCard(trends: TrendsInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Your Trends Today",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
            )
            Spacer(Modifier.height(8.dp))
            if (trends == null) {
                Text("Loading...", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(title = "${trends.listens} listens", change = trends.listensChange)
                    MetricItem(title = "${trends.minutes} min.", change = trends.minutesChange)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
fun <T> HorizontalCarousel(
    items: List<T>,
    itemContent: @Composable (T) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items) { item ->
            Box(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            ) { itemContent(item) }
        }
    }
}

@Composable
fun HistoryContent(
    modifier: Modifier,
    history: List<PlayedItem>,
    onImportHistory: () -> Unit
) {
    Column(modifier = modifier.padding(16.dp)) {
        Button(
            onClick = onImportHistory,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import History") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history.take(50)) { HistoryItemRow(it) }
        }
    }
}

@Composable
fun ProfileContent(
    modifier: Modifier,
    onNavigateToStats: (String) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
        listOf(
            "week" to "Week",
            "month" to "Month",
            "6_months" to "6 Months",
            "year" to "Year",
            "all_time" to "All Time",
            "recommendations" to "Recommendations"
        ).forEach { (key, label) ->
            Card(
                modifier = Modifier.clickable { onNavigateToStats(key) }.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
            }
        }
    }
}

@Composable
fun MetricItem(title: String, change: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowDropUp, contentDescription = null, tint = Color(0xFF1DB954))
            Text(text = change, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1DB954)))
        }
    }
}

@Composable
fun AlbumCardItem(album: AlbumAggregate) {
    val coverUrl = album.albumImages.firstOrNull()?.url
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.width(150.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = album.albumName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            Text(
                text = album.artistName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
            Text(
                text = "Tracks: ${album.trackCount}",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
            )
        }
    }
}

@Composable
fun ArtistCardItem(artist: SpotifyArtistFull) {
    val imageUrl = artist.images?.firstOrNull()?.url
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.width(150.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = artist.name.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            artist.genres?.take(2)?.let { genres ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = genres.joinToString(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun TopTrackItem(track: SpotifyTrack) {
    val imageUrl = track.album?.images?.firstOrNull()?.url
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.name.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artists?.joinToString { it.name.orEmpty() }.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
        }
    }
}

@Composable
fun HistoryItemRow(item: PlayedItem) {
    val instant = java.time.Instant.parse(item.playedAt)
    val dateTimeString = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.track.album?.images?.firstOrNull()?.url,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    item.track.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
                Text(
                    item.track.artists?.joinToString { it.name.orEmpty() }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
                Text(
                    dateTimeString,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )
            }
        }
    }
}
