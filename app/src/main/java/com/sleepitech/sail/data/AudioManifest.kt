package com.sleepitech.sail.data

import com.google.gson.annotations.SerializedName

/**
 * Represents the root of the local_audio_manifest.json file.
 */
data class AudioManifest(
    @SerializedName("navigation_sounds")
    val navigationSounds: Map<String, String> = emptyMap(),

    @SerializedName("background_sounds")
    val backgroundSounds: Map<String, String> = emptyMap()
)
