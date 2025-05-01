package com.example.spotan.utils


import com.example.spotan.data.PlayedItem

/** Возвращает первую непустую обложку в группе треков или null. */
fun List<PlayedItem>.firstCover(): String? =
    asSequence()
        .mapNotNull { it.track.album?.images?.firstOrNull()?.url }
        .firstOrNull { it.isNotBlank() }
