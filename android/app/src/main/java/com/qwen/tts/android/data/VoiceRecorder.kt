package com.qwen.tts.android.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class RecorderState(
    val isRecording: Boolean = false,
    val elapsedMillis: Long = 0L,
    val level: Float = 0f,
    val outputPath: String? = null,
)

data class RecordingResult(val file: File, val durationMillis: Long)

class VoiceRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()
    private val recording = AtomicBoolean(false)
    private val sampleRate = 24_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        .coerceAtLeast(sampleRate / 5)
    private var cleaning = false
    private var session: Session? = null

    private class Session(val record: AudioRecord, val file: File, val startedAt: Long) {
        val bytes = ByteArrayOutputStream()
        var job: Job? = null
    }

    private val _state = MutableStateFlow(RecorderState())
    val state = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(file: File): Boolean = synchronized(guard) {
        if (session != null || cleaning) return@synchronized false
        file.parentFile?.mkdirs()
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioEncoding)
                        .build(),
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()
        } catch (_: Exception) {
            return@synchronized false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@synchronized false
        }
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                return@synchronized false
            }
        } catch (_: Exception) {
            record.release()
            return@synchronized false
        }
        val current = Session(record, file, System.currentTimeMillis())
        session = current
        recording.set(true)
        _state.value = RecorderState(isRecording = true, outputPath = file.absolutePath)
        current.job = scope.launch {
            val buffer = ShortArray(minBufferSize / 2)
            while (recording.get()) {
                val count = record.read(buffer, 0, buffer.size)
                if (count < 0) break
                if (count == 0) continue
                var sumSquares = 0.0
                for (i in 0 until count) {
                    val sample = buffer[i].toInt()
                    current.bytes.write(sample and 0xff)
                    current.bytes.write((sample shr 8) and 0xff)
                    sumSquares += sample.toDouble() * sample.toDouble()
                }
                if (recording.get()) {
                    val rms = sqrt(sumSquares / count.toDouble()) / 32768.0
                    _state.value = RecorderState(
                        isRecording = true,
                        elapsedMillis = System.currentTimeMillis() - current.startedAt,
                        level = rms.toFloat().coerceIn(0f, 1f),
                        outputPath = file.absolutePath,
                    )
                }
            }
        }
        true
    }

    suspend fun stop(): RecordingResult? {
        val current = synchronized(guard) {
            if (!recording.getAndSet(false)) return null
            cleaning = true
            checkNotNull(session)
        }
        runCatching { current.record.stop() }
        return withContext(NonCancellable + Dispatchers.IO) {
            try {
                current.job?.join()
                check(current.bytes.size() > 0) { "录音为空，请重新录制。" }
                writeRecording(current)
                RecordingResult(current.file, System.currentTimeMillis() - current.startedAt)
            } finally {
                finish(current)
            }
        }
    }

    fun cancel(discard: Boolean = true) {
        val current = synchronized(guard) {
            if (!recording.getAndSet(false)) return
            cleaning = true
            checkNotNull(session)
        }
        runCatching { current.record.stop() }
        scope.launch {
            try {
                current.job?.join()
                if (discard) {
                    current.file.delete()
                } else if (current.bytes.size() > 0) {
                    // Preserve unfinished user audio if the activity is destroyed.
                    writeRecording(current)
                }
            } finally {
                finish(current)
            }
        }
    }

    private fun finish(current: Session) {
        runCatching { current.record.release() }
        synchronized(guard) {
            if (session === current) session = null
            cleaning = false
        }
        _state.value = RecorderState(isRecording = false, outputPath = current.file.absolutePath)
    }

    private fun writeRecording(current: Session) {
        current.file.outputStream().use { output -> writeWav(output, current.bytes.toByteArray(), sampleRate) }
    }

    private fun writeWav(output: OutputStream, pcmBytes: ByteArray, rate: Int) {
        output.writeAscii("RIFF")
        output.writeIntLe(36 + pcmBytes.size)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(1)
        output.writeIntLe(rate)
        output.writeIntLe(rate * 2)
        output.writeShortLe(2)
        output.writeShortLe(16)
        output.writeAscii("data")
        output.writeIntLe(pcmBytes.size)
        output.write(pcmBytes)
    }
}

private fun OutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))
private fun OutputStream.writeIntLe(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
    write((value shr 16) and 0xff)
    write((value shr 24) and 0xff)
}
private fun OutputStream.writeShortLe(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
}
