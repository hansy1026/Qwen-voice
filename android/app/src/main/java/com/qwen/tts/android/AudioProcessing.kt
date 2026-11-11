package com.qwen.tts.android

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Keep pitch unchanged when applying speed and optional sample-rate conversion.
@androidx.annotation.OptIn(UnstableApi::class)
internal fun processOutputAudio(samples: FloatArray, inputRate: Int, speed: Float, outputRate: Int): FloatArray {
    require(inputRate > 0 && outputRate > 0 && speed in 0.5f..1.5f)
    if (speed == 1f && inputRate == outputRate) return samples
    val processor = SonicAudioProcessor()
    processor.setSpeed(speed)
    processor.setPitch(1f)
    processor.setOutputSampleRateHz(outputRate)
    processor.configure(AudioProcessor.AudioFormat(inputRate, 1, C.ENCODING_PCM_16BIT))
    processor.flush()
    val collected = ByteArrayOutputStream()
    fun drain() {
        val output = processor.output
        if (output.hasRemaining()) {
            val bytes = ByteArray(output.remaining())
            output.get(bytes)
            collected.write(bytes)
        }
    }
    try {
        var start = 0
        while (start < samples.size) {
            val length = minOf(8192, samples.size - start)
            val buffer = ByteBuffer.allocateDirect(length * 2).order(ByteOrder.nativeOrder())
            for (index in start until start + length) {
                buffer.putShort((samples[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            buffer.flip()
            processor.queueInput(buffer)
            check(!buffer.hasRemaining()) { "音频转换未能处理全部数据" }
            drain()
            start += length
        }
        processor.queueEndOfStream()
        while (!processor.isEnded) drain()
        val bytes = collected.toByteArray()
        check(bytes.isNotEmpty() && bytes.size % 2 == 0) { "音频转换未产生有效结果" }
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get().toFloat() / 32768f }
    } finally {
        processor.reset()
    }
}

internal fun splitSpeechText(text: String, maxLength: Int = 120): List<String> {
    require(maxLength >= 2)
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + maxLength, text.length)
        if (end < text.length) {
            if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            val floor = start + maxLength / 2
            val preferred = (end - 1 downTo floor).firstOrNull { text[it] in "。！？.!?；;，,\n " }
            if (preferred != null) end = preferred + 1
        }
        val segment = text.substring(start, end).trim()
        if (segment.isNotEmpty()) parts.add(segment)
        start = end
    }
    return parts
}
