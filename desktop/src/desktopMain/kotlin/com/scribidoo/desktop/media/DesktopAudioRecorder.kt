package com.scribidoo.desktop.media

import com.scribidoo.desktop.data.DesktopDocumentStorageManager
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

class DesktopAudioRecorder {
    private var targetLine: TargetDataLine? = null
    private var recordThread: Thread? = null
    var isRecording = false
        private set

    val audioDir: File
        get() = File(DesktopDocumentStorageManager.rootDir, "audio").apply { if (!exists()) mkdirs() }

    fun startRecording(): File? {
        if (isRecording) return null

        val format = AudioFormat(44100.0f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Microphone line not supported by system")
            return null
        }

        val outputFile = File(audioDir, "voice_${UUID.randomUUID()}.wav")

        try {
            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(format)
            line.start()
            targetLine = line
            isRecording = true

            recordThread = thread(start = true, name = "ScribidooAudioRecordThread") {
                try {
                    val stream = AudioInputStream(line)
                    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, outputFile)
                } catch (e: IOException) {
                    if (isRecording) e.printStackTrace()
                }
            }

            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return null
        }
    }

    fun stopRecording(): Boolean {
        if (!isRecording) return false
        isRecording = false
        try {
            targetLine?.stop()
            targetLine?.close()
            targetLine = null
            recordThread?.join(1500)
            recordThread = null
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
