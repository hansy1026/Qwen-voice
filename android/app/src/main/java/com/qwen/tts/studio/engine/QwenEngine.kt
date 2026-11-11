package com.qwen.tts.studio.engine

class QwenEngine : AutoCloseable {
    private var nativePtr: Long = 0

    companion object {
        const val BACKEND_AUTO: Int = 0
        const val BACKEND_CPU: Int = 1
        const val BACKEND_GPU: Int = 2
    }

    class NativeParams(
        val languageId: Int = 2050,
        val instruction: String? = null,
        val speaker: String? = null,
        val maxAudioTokens: Int = 512,
    )

    class NativeResult(
        val audio: FloatArray?,
        val sampleRate: Int,
        val success: Boolean,
        val errorMsg: String?,
        val timeMs: Long,
    ) {
        val tokenizeMs: Long = 0L
        val encodeMs: Long = 0L
        val generateMs: Long = 0L
        val decodeMs: Long = 0L
        val decodeFrames: Int = 0
        val decodeSamples: Long = 0L
        val decodeGraphComputeMs: Long = 0L
    }

    class NativeCapabilities(
        val loaded: Boolean,
        val supportsCloning: Boolean,
        val supportsNamedSpeakers: Boolean,
        val supportsInstruction: Boolean,
        val speakerEmbeddingDim: Int,
        val modelKind: Int,
        val speakerCount: Int,
    )

    fun interface ProgressCallback {
        fun onProgress(tokensGenerated: Int, maxTokens: Int)
    }

    init {
        System.loadLibrary("qwen3_tts_jni")
        nativePtr = nativeInit()
    }

    private fun pointer(): Long = nativePtr.also { check(it != 0L) { "语音引擎已关闭" } }

    @Synchronized
    fun loadModels(modelDir: String, modelName: String? = null): Boolean =
        nativeLoadModels(pointer(), modelDir, modelName)

    @Synchronized
    fun synthesize(
        text: String,
        referenceWav: String? = null,
        speakerEmbeddingPath: String? = null,
        params: NativeParams = NativeParams(),
    ): NativeResult =
        nativeSynthesize(pointer(), text, referenceWav, speakerEmbeddingPath, params)

    @Synchronized
    fun synthesizeWithIclPrompt(text: String, promptPath: String, params: NativeParams = NativeParams()): NativeResult =
        nativeSynthesizeWithIclPrompt(pointer(), text, promptPath, params)

    @Synchronized
    fun extractIclPrompt(referenceWav: String, referenceText: String, outputPath: String): Boolean =
        nativeExtractIclPrompt(pointer(), referenceWav, referenceText, outputPath)

    @Synchronized
    fun getLastError(): String? = nativeGetLastError(pointer())

    @Synchronized
    fun getActiveBackendName(): String? = nativeGetActiveBackendName()

    @Synchronized
    fun setBackendPreference(preference: Int): Boolean = nativeSetBackendPreference(preference)

    @Synchronized
    fun getCompiledBackendMask(): Int = nativeGetCompiledBackendMask()

    @Synchronized
    fun setCpuThreads(nThreads: Int): Boolean = nativeSetCpuThreads(nThreads)

    @Synchronized
    fun getCpuThreads(): Int = nativeGetCpuThreads()

    @Synchronized
    fun setProgressCallback(callback: ProgressCallback?): Boolean =
        nativeSetProgressCallback(pointer(), callback)

    @Synchronized
    fun getModelCapabilities(): NativeCapabilities? =
        nativeGetModelCapabilities(pointer())

    @Synchronized
    fun extractSpeakerEmbedding(referenceWav: String, outputPath: String): Boolean =
        nativeExtractSpeakerEmbedding(pointer(), referenceWav, outputPath)

    @Synchronized
    fun getAvailableSpeakers(): List<String> {
        val raw = nativeGetAvailableSpeakers(pointer()).orEmpty()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    @Synchronized
    override fun close() {
        if (nativePtr != 0L) {
            nativeFree(nativePtr)
            nativePtr = 0
        }
    }

    private external fun nativeInit(): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeSetBackendPreference(preference: Int): Boolean
    private external fun nativeGetCompiledBackendMask(): Int
    private external fun nativeSetCpuThreads(nThreads: Int): Boolean
    private external fun nativeGetCpuThreads(): Int
    private external fun nativeSetProgressCallback(ptr: Long, callback: ProgressCallback?): Boolean
    private external fun nativeGetActiveBackendName(): String?
    private external fun nativeLoadModels(ptr: Long, modelDir: String, modelName: String?): Boolean
    private external fun nativeSynthesize(
        ptr: Long,
        text: String,
        referenceWav: String?,
        speakerEmbeddingPath: String?,
        params: NativeParams?,
    ): NativeResult
    private external fun nativeSynthesizeWithIclPrompt(ptr: Long, text: String, promptPath: String, params: NativeParams?): NativeResult
    private external fun nativeExtractIclPrompt(ptr: Long, referenceWav: String, referenceText: String, outputPath: String): Boolean
    private external fun nativeGetLastError(ptr: Long): String?
    private external fun nativeGetModelCapabilities(ptr: Long): NativeCapabilities?
    private external fun nativeExtractSpeakerEmbedding(ptr: Long, referenceWav: String, outputPath: String): Boolean
    private external fun nativeGetAvailableSpeakers(ptr: Long): String?
}
