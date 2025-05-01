package com.example.spotan.data

import com.google.gson.annotations.SerializedName

data class SpotifyTopTracksResponse(
    @SerializedName("items") val items: List<SpotifyTrack> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null
)
data class CurrentlyPlayingResponse(
    @SerializedName("item") val item: SpotifyTrack?,
    @SerializedName("is_playing") val isPlaying: Boolean?,
    @SerializedName("progress_ms") val progressMs: Int?,
    // при желании добавьте другие поля (например, device, shuffle_state, repeat_state и т.д.)
)
data class SpotifyTrack(
    val id: String? = null,
    var name: String? = null,
    @SerializedName("duration_ms") var durationMs: Int? = null,
    var artists: List<SpotifyArtist>? = null,
    var album: SpotifyAlbum? = null
)
data class TrendsInfo(
    val listens: Int,
    val listensChange: String,
    val minutes: Int,
    val minutesChange: String
)
data class AlbumAggregate(
    val albumId: String,
    val albumName: String,
    val albumImages: List<SpotifyImage>, // предполагается, что SpotifyImage уже определён
    val artistName: String,
    val trackCount: Int
)

data class SpotifyArtistsResponse(
    val artists: List<SpotifyArtistFull>?
)
data class SpotifyArtistSearchResponse(
    val artists: SpotifyArtistSearchItems?
)

data class SpotifyArtistSearchItems(
    val items: List<SpotifyArtistFull>
)

data class SpotifyArtist(
    val name: String? = null,
    val id: String? = null,
    val images: List<SpotifyImage>? = null,
    var coverUrl: String? = null // <- новое поле
)
data class SpotifyTopArtistsResponse(
    val items: List<SpotifyArtistFull> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null
)

data class SpotifyArtistFull(
    val id: String? = null,
    val name: String? = null,
    val images: List<SpotifyImage>? = null,
    val genres: List<String>? = null,
    val popularity: Int? = null,
    // Можно добавить другие поля при необходимости
)
data class StreamingHistoryItem(
    val endTime: String,    // Формат: "yyyy-MM-dd HH:mm", например: "2019-01-01 12:00"
    val artistName: String,
    val trackName: String,
    val msPlayed: Int
)
data class DetailedStreamingHistoryItem(
    val ts: String?, // "2023-10-27T19:58:14Z"
    val platform: String?,
    val ms_played: Long?,
    val conn_country: String?,
    val ip_addr: String?,

    val master_metadata_track_name: String?,
    val master_metadata_album_artist_name: String?,
    val master_metadata_album_album_name: String?,

    val spotify_track_uri: String?,

    val episode_name: String?,
    val episode_show_name: String?,
    val spotify_episode_uri: String?,
    val audiobook_title: String?,
    val audiobook_uri: String?,
    val audiobook_chapter_uri: String?,
    val audiobook_chapter_title: String?,

    val reason_start: String?,
    val reason_end: String?,
    val shuffle: Boolean?,
    val skipped: Boolean?,
    val offline: Boolean?,
    val offline_timestamp: Long?,
    val incognito_mode: Boolean?
)

data class RecentlyPlayedResponse(
    val items: List<PlayedItem> = emptyList(),
    val next: String? = null,
    val cursors: Cursors? = null,
    val limit: Int? = null
)
data class Cursors(
    val after: String? = null,
    val before: String? = null
)
data class SpotifyTracksBatchResponse(
    val tracks: List<SpotifyTrack>?
)

data class PlayedItem(
    @SerializedName("track") val track: SpotifyTrack,
    @SerializedName("played_at") val playedAt: String
)
data class SpotifyUserProfile(
    val id: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val images: List<SpotifyImage>? = null
)
data class SpotifyAlbum(
    val images: List<SpotifyImage>? = null,
    val name: String? = null
)

data class SpotifyImage(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrack>,
    val seeds: List<Seed>
)

data class Seed(
    val initialPoolSize: Int?,
    val afterFilteringSize: Int?,
    val afterRelinkingSize: Int?,
    val id: String?,
    val type: String?,
    val href: String?
)
data class SpotifyTrackSearchResponse(
    val tracks: Tracks?
)
data class Tracks(
    val items: List<SpotifyTrack>?
)