package com.scribidoo.desktop.media

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
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

class DesktopAudioPlayer(private val scope: CoroutineScope) {
    private var currentClip: Clip? = null
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

        if (currentlyPlayingPath == filePath && isPlaying) {
            pause()
            return
        }

        stop()

        try {
            val audioStream = AudioSystem.getAudioInputStream(file)
            val clip = AudioSystem.getClip()
            clip.open(audioStream)
            currentClip = clip

            val totalDurationMs = clip.microsecondLength / 1000L
            val startMs = trimStartMs.coerceIn(0L, totalDurationMs)
            val endMs = if (trimEndMs > startMs && trimEndMs <= totalDurationMs) trimEndMs else totalDurationMs

            clip.microsecondPosition = startMs * 1000L
            clip.start()
            currentlyPlayingPath = filePath
            isPlaying = true
            currentPositionMs = startMs

            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP && isPlaying) {
                    val pos = clip.microsecondPosition / 1000L
                    if (pos >= endMs || pos >= totalDurationMs) {
                        stop()
                        onComplete()
                    }
                }
            }

            progressJob = scope.launch(Dispatchers.Default) {
                while (isActive && isPlaying) {
                    val pos = (clip.microsecondPosition / 1000L)
                    currentPositionMs = pos
                    if (pos >= endMs) {
                        stop()
                        onComplete()
                        break
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        currentClip?.let {
            if (it.isRunning) {
                it.stop()
                isPlaying = false
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        currentClip?.let {
            if (!it.isRunning) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun stop() {
        try {
            progressJob?.cancel()
            progressJob = null
            currentClip?.let {
                if (it.isRunning) it.stop()
                it.close()
            }
            currentClip = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentlyPlayingPath = null
            isPlaying = false
            currentPositionMs = 0L
        }
    }
}
