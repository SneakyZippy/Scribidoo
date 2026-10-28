package com.scribidoo.app.media

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlaybackManager(private val scope: CoroutineScope) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    var currentlyPlayingPath by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set

    fun play(
        filePath: String,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        onComplete: () -> Unit = {}
    ) {
        val file = File(filePath)
        if (!file.exists()) return

        // If already playing this file, toggle pause
        if (currentlyPlayingPath == filePath && isPlaying) {
            pause()
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }
            mediaPlayer = player

            val totalDuration = player.duration.toLong()
            val start = trimStartMs.coerceIn(0L, totalDuration)
            val end = if (trimEndMs > start && trimEndMs <= totalDuration) trimEndMs else totalDuration

            if (start > 0) {
                player.seekTo(start.toInt())
            }

            player.start()
            currentlyPlayingPath = filePath
            isPlaying = true
            currentPositionMs = start

            player.setOnCompletionListener {
                stop()
                onComplete()
            }

            // Progress tracking loop
            progressJob = scope.launch(Dispatchers.Main) {
                while (isActive && isPlaying) {
                    val pos = player.currentPosition.toLong()
                    currentPositionMs = pos
                    if (pos >= end) {
                        stop()
                        onComplete()
                        break
                    }
                    delay(80)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        currentlyPlayingPath = null
        isPlaying = false
        currentPositionMs = 0L
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            currentPositionMs = positionMs
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
