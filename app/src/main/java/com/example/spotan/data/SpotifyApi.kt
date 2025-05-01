package com.example.spotan.data

import retrofit2.http.GET
import retrofit2.http.Query

interface SpotifyApi {
    @GET("me/player/currently-playing")
    suspend fun getCurrentlyPlaying(): CurrentlyPlayingResponse?
    @GET("me/top/tracks")
    suspend fun getTopTracks(
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int
    ): SpotifyTopTracksResponse
    @GET("me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Query("limit") limit: Int = 50,
        @Query("before") before: Long? = null
    ): RecentlyPlayedResponse
    @GET("me/top/artists")
    suspend fun getTopArtists(
        @Query("time_range") timeRange: String,
        @Query("limit") limit: Int
    ): SpotifyTopArtistsResponse
    @GET("me")
    suspend fun getCurrentUserProfile(): SpotifyUserProfile
    // Важно: этот метод вернёт объект, у которого поле tracks (List<SpotifyTrack>).
    @GET("tracks")
    suspend fun getTracksByIds(
        @Query("ids") commaSeparatedIds: String
    ): SpotifyTracksBatchResponse
    @GET("artists")
    suspend fun getArtists(
        @Query("ids") ids: String
    ): SpotifyArtistsResponse
    @GET("search")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("limit") limit: Int = 1
    ): SpotifyArtistSearchResponse
    @GET("recommendations")
    suspend fun getRecommendations(
        @Query("seed_artists") seedArtists: String?,
        @Query("seed_genres") genre: String?,
        @Query("seed_tracks") seedTracks: String?,
        @Query("target_energy") targetEnergy: Double?,
        @Query("target_valence") targetValence:Double?
    ): SpotifyRecommendationsResponse
    @GET("search")
    suspend fun searchTrack(
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 1
    ): SpotifyTrackSearchResponse


}