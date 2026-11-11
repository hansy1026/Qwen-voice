package com.qwen.tts.android

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qwen.tts.android.data.VoiceRecorder
import com.qwen.tts.android.data.db.GenerationEntity
import com.qwen.tts.android.data.db.QwenDatabase
import com.qwen.tts.android.data.db.VoiceProfileEntity
import com.qwen.tts.android.ui.theme.QwenTtsTheme
import com.qwen.tts.studio.engine.QwenEngine
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ModelFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
)

private data class ModelVariant(
    val id: String,
    val label: String,
    val displayName: String,
    val files: List<ModelFile>,
    val talkerName: String,
) {
    val totalBytes: Long = files.sumOf { it.sizeBytes }
}

private data class LanguageOption(val label: String, val id: Int)
private data class BackendOption(val id: String, val label: String, val preference: Int)
private data class GenerationTimeEstimate(val totalMillis: Long, val sampleSize: Int)

private val languageOptions = listOf(
    LanguageOption("英语", 2050),
    LanguageOption("德语", 2053),
    LanguageOption("西班牙语", 2054),
    LanguageOption("法语", 2061),
    LanguageOption("中文", 2055),
    LanguageOption("日语", 2058),
    LanguageOption("韩语", 2064),
    LanguageOption("俄语", 2069),
)

private val backendOptions = listOf(
    BackendOption("cpu", "CPU", QwenEngine.BACKEND_CPU),
)

private val defaultBackendOption = backendOptions.first { it.id == "cpu" }

private object QwenModel {
    private val tokenizer = ModelFile(
        "qwen-tokenizer-12hz-Q4_K_M.gguf", 254_974_752L,
        "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039",
    )
    private val base = ModelFile(
        "qwen-talker-0.6b-base-Q4_K_M.gguf", 628_905_056L,
        "4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226",
    )
    private val custom = ModelFile(
        "qwen-talker-1.7b-customvoice-Q4_K_M.gguf", 1_182_631_296L,
        "cc328834a631bc08bf9f43e62fa23f8a1383d9b429864ce6690cfb172077fc4a",
    )
    val defaultVariant = ModelVariant("base", "0.6B", "0.6B 声音克隆", listOf(tokenizer, base), base.name)
    val customVariant = ModelVariant("custom", "1.7B", "1.7B 预设音色", listOf(tokenizer, custom), custom.name)
    val variants = listOf(defaultVariant, customVariant)
    val bundledFiles = listOf(tokenizer, base, custom)
    val bundledBytes = bundledFiles.sumOf { it.sizeBytes }
    fun variantById(id: String): ModelVariant = variants.firstOrNull { it.id == id } ?: defaultVariant
}

data class QwenTtsUiState(
    val text: String = "",
    val speed: Float = 1f,
    val outputSampleRate: Int = 24_000,
    val selectedPreset: String = "Vivian",
    val stylePrompt: String = "",
    val selectedModelId: String = QwenModel.defaultVariant.id,
    val selectedBackendId: String = defaultBackendOption.id,
    val selectedVoiceId: String? = null,
    val activeBackendName: String? = null,
    val modelReady: Boolean = false,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadBytes: Long = 0L,
    val downloadTotalBytes: Long = QwenModel.defaultVariant.totalBytes,
    val status: String = "离线模型尚未准备",
    val error: String? = null,
    val sampleRate: Int = 0,
    val sampleCount: Int = 0,
    val synthesisMillis: Long = 0,
    val playing: Boolean = false,
    val playingGenerationId: Long? = null,
    val operationElapsedMillis: Long = 0,
    val synthesisFrames: Int = 0,
    val maxAudioTokens: Int = 512,
    val cpuThreads: Int = 0,
    val selectedCpuThreads: Int = 0,
    val selectedLanguageId: Int = 2055,
    val tokenizeMillis: Long = 0,
    val encodeMillis: Long = 0,
    val generateMillis: Long = 0,
    val decodeMillis: Long = 0,
    val decodeFrames: Int = 0,
    val decodeSamples: Long = 0,
    val decodeGraphComputeMillis: Long = 0,
    val savedFileName: String? = null,
    val snackbarMessage: String? = null,
    val estimatedSynthesisMillis: Long? = null,
    val estimateSampleCount: Int = 0,
    val supportsCloning: Boolean? = null,
    val speakerEmbeddingDim: Int = 0,
    val pendingSave: Boolean = false,
)

private data class PendingGeneration(
    val text: String,
    val samples: FloatArray,
    val sampleRate: Int,
    val voiceName: String,
    val voiceId: String?,
    val synthesisMillis: Long,
    var savedFile: File? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("qwen-settings", 0)
    private val modelDir = File(application.filesDir, "qwen3-tts-models")
    private val voiceDir = File(application.filesDir, "voices")
    private val generationDir = File(application.filesDir, "generations")
    private val dao = QwenDatabase.getDatabase(application).qwenDao()
    private val recorder = VoiceRecorder()

    val voices = dao.observeVoices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val generations = dao.observeGenerations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recorderState = recorder.state

    private val _uiState = MutableStateFlow(
        QwenTtsUiState(
            modelReady = isModelReady(QwenModel.defaultVariant),
            downloadTotalBytes = QwenModel.defaultVariant.totalBytes,
            outputSampleRate = preferences.getInt("outputSampleRate", 24_000).takeIf { it == 24_000 || it == 48_000 } ?: 24_000,
        ),
    )
    val uiState = _uiState.asStateFlow()

    @Volatile private var engine: QwenEngine? = null
    private val engineGuard = Any()
    private var disposed = false
    private var generatedAudio: FloatArray? = null
    private var pendingGeneration: PendingGeneration? = null
    @Volatile private var playJob: Job? = null
    private var operationTickerJob: Job? = null
    @Volatile private var activeTrack: AudioTrack? = null

    init {
        val threads = preferredCpuThreadCount()
        _uiState.update { it.copy(selectedCpuThreads = threads, cpuThreads = threads) }
        if (QwenModel.variants.all(::isModelReady)) refreshModelState() else downloadModel()
    }

    fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun updateSpeed(value: Float) {
        if (!_uiState.value.busy) _uiState.update { it.copy(speed = value.coerceIn(0.5f, 1.5f)) }
    }

    fun updateOutputSampleRate(value: Int) {
        if (_uiState.value.busy || value !in listOf(24_000, 48_000)) return
        preferences.edit().putInt("outputSampleRate", value).apply()
        _uiState.update { it.copy(outputSampleRate = value) }
    }

    fun updatePreset(value: String) {
        if (!_uiState.value.busy) _uiState.update { it.copy(selectedPreset = value) }
    }

    fun updateStyle(value: String) {
        if (!_uiState.value.busy) _uiState.update { it.copy(stylePrompt = value) }
    }

    fun updateCpuThreads(value: Int) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(selectedCpuThreads = value, cpuThreads = value) }
        engine?.setCpuThreads(value)
    }

    fun updateLanguage(languageId: Int) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(selectedLanguageId = languageId) }
    }

    fun selectVoice(voiceId: String?) {
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun selectModel(modelId: String) {
        if (_uiState.value.busy || _uiState.value.selectedModelId == modelId) return
        if (pendingGeneration != null) {
            _uiState.update { it.copy(error = "请先重试保存已生成的音频。") }
            return
        }
        val variant = QwenModel.variantById(modelId)
        stopAudio()
        val oldEngine = synchronized(engineGuard) { engine.also { engine = null } }
        oldEngine?.close()
        generatedAudio = null
        val ready = isModelReady(variant)
        _uiState.update {
            it.copy(
                selectedModelId = variant.id,
                selectedVoiceId = null,
                activeBackendName = null,
                modelReady = ready,
                loaded = false,
                status = if (ready) "模型已就绪" else "离线模型尚未准备",
                error = null,
                downloadProgress = 0f,
                downloadBytes = 0L,
                downloadTotalBytes = variant.totalBytes,
                sampleRate = 0,
                sampleCount = 0,
                synthesisMillis = 0,
                synthesisFrames = 0,
                tokenizeMillis = 0,
                encodeMillis = 0,
                generateMillis = 0,
                decodeMillis = 0,
                decodeFrames = 0,
                decodeSamples = 0,
                decodeGraphComputeMillis = 0,
                savedFileName = null,
                estimatedSynthesisMillis = null,
                estimateSampleCount = 0,
                supportsCloning = null,
                speakerEmbeddingDim = 0,
            )
        }
    }

    fun downloadModel() {
        if (_uiState.value.downloading || _uiState.value.busy) return
        viewModelScope.launch {
            startBusy(status = "正在准备内置离线模型", downloading = true)
            _uiState.update { it.copy(downloadProgress = 0f, downloadBytes = 0L, downloadTotalBytes = QwenModel.bundledBytes) }
            val result = runCatching { withContext(Dispatchers.IO) { installBundledFiles() } }
            result.fold(
                onSuccess = { stopBusyTicker(); refreshModelState("离线模型已准备好") },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update { it.copy(busy = false, downloading = false, status = "模型准备失败", error = throwable.message ?: "模型准备失败") }
                },
            )
        }
    }

    fun loadModel() {
        if (!_uiState.value.modelReady || _uiState.value.loaded || _uiState.value.busy) return
        viewModelScope.launch {
            startBusy(status = "正在加载模型")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val native = ensureEngineLoaded(selectedVariant())
                    native.getActiveBackendName().orEmpty() to native.getCpuThreads()
                }
            }

            result.fold(
                onSuccess = { (backend, threads) ->
                    stopBusyTicker()
                    val caps = engine?.getModelCapabilities()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = true,
                            status = if (backend.isBlank()) "模型已加载" else "模型已加载（$backend）",
                            activeBackendName = backend.ifBlank { null },
                            cpuThreads = threads,
                            supportsCloning = caps?.supportsCloning,
                            speakerEmbeddingDim = caps?.speakerEmbeddingDim ?: 0,
                        )
                    }
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = false,
                            status = "加载失败",
                            error = throwable.message ?: "加载失败",
                        )
                    }
                },
            )
        }
    }

    fun synthesize() {
        val text = _uiState.value.text.trim()
        if (text.isEmpty() || _uiState.value.busy) return
        if (text.length > 5000) {
            _uiState.update { it.copy(error = "文本超过 5000 字，请缩短后再生成。") }
            return
        }
        if (pendingGeneration != null) {
            _uiState.update { it.copy(error = "请先重试保存已生成的音频。") }
            return
        }
        viewModelScope.launch {
            val variant = selectedVariant()
            val selectedVoice = _uiState.value.selectedVoiceId?.let { id -> voices.value.firstOrNull { it.voiceId == id } }
            val speed = _uiState.value.speed
            val outputRate = _uiState.value.outputSampleRate
            val segments = splitSpeechText(text)
            val estimate = estimateSynthesisMillis(text)
            startBusy(status = "正在生成语音", resetSynthesis = true, synthesisEstimate = estimate)

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val native = ensureEngineLoaded(variant)
                    var segmentNumber = 1
                    native.setProgressCallback { frames, maxTokens ->
                        _uiState.update {
                            it.copy(
                                loaded = true,
                                status = "正在生成第 $segmentNumber / ${segments.size} 段",
                                synthesisFrames = frames,
                                maxAudioTokens = maxTokens,
                            )
                        }
                    }
                    try {
                        val isPreset = variant.id == QwenModel.customVariant.id
                        val params = QwenEngine.NativeParams(
                            languageId = _uiState.value.selectedLanguageId,
                            speaker = if (isPreset) _uiState.value.selectedPreset else null,
                            instruction = if (isPreset) _uiState.value.stylePrompt.takeIf { it.isNotBlank() } else null,
                            maxAudioTokens = 1024,
                        )
                        val promptPath = selectedVoice?.speakerEmbeddingPath
                        val audioParts = mutableListOf<FloatArray>()
                        var inputRate = 0
                        var synthesisMillis = 0L
                        segments.forEachIndexed { index, segment ->
                            segmentNumber = index + 1
                            val part = if (!isPreset && promptPath?.endsWith(".icl.json") == true) {
                                native.synthesizeWithIclPrompt(segment, promptPath, params)
                            } else {
                                native.synthesize(text = segment, speakerEmbeddingPath = if (isPreset) null else promptPath, params = params)
                            }
                            check(part.success && part.audio != null && part.audio.isNotEmpty()) {
                                part.errorMsg ?: "第 ${index + 1} 段语音生成失败"
                            }
                            val rate = part.sampleRate.takeIf { it > 0 } ?: 24_000
                            if (inputRate == 0) inputRate = rate
                            check(inputRate == rate) { "各段语音采样率不一致" }
                            audioParts.add(part.audio)
                            synthesisMillis += part.timeMs
                        }
                        val silenceSamples = inputRate * 120 / 1000
                        val totalSamples = audioParts.sumOf { it.size.toLong() } + silenceSamples.toLong() * (audioParts.size - 1)
                        check(totalSamples <= Int.MAX_VALUE) { "生成的音频太长" }
                        val joined = FloatArray(totalSamples.toInt())
                        var offset = 0
                        audioParts.forEachIndexed { index, part ->
                            part.copyInto(joined, offset)
                            offset += part.size
                            if (index < audioParts.lastIndex) offset += silenceSamples
                        }
                        val converted = processOutputAudio(joined, inputRate, speed, outputRate)
                        QwenEngine.NativeResult(converted, outputRate, true, null, synthesisMillis)
                    } finally {
                        _uiState.update {
                            it.copy(activeBackendName = native.getActiveBackendName()?.takeIf { name -> name.isNotBlank() })
                        }
                        native.setProgressCallback(null)
                    }
                }
            }

            result.fold(
                onSuccess = { nativeResult ->
                    stopBusyTicker()
                    if (!nativeResult.success || nativeResult.audio == null) {
                        _uiState.update {
                            it.copy(
                                busy = false,
                                loaded = true,
                                status = "生成失败",
                                error = nativeResult.errorMsg ?: "语音生成失败",
                            )
                        }
                        return@fold
                    }

                    generatedAudio = nativeResult.audio
                    val sampleRate = nativeResult.sampleRate.takeIf { it > 0 } ?: 24_000
                    val voiceName = if (variant.id == QwenModel.customVariant.id) _uiState.value.selectedPreset else selectedVoice?.name ?: "默认音色"
                    val pending = PendingGeneration(text, nativeResult.audio, sampleRate, voiceName, selectedVoice?.voiceId, nativeResult.timeMs)
                    pendingGeneration = pending
                    _uiState.update { it.copy(status = "正在保存音频…") }
                    val saveFailure = runCatching { persistGeneratedAudio(pending) }.exceptionOrNull()
                    if (saveFailure == null) pendingGeneration = null
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loaded = true,
                            status = if (saveFailure == null) "生成完成" else "音频已生成但保存失败，可重试保存",
                            sampleRate = sampleRate,
                            sampleCount = nativeResult.audio.size,
                            synthesisMillis = nativeResult.timeMs,
                            tokenizeMillis = nativeResult.tokenizeMs,
                            encodeMillis = nativeResult.encodeMs,
                            generateMillis = nativeResult.generateMs,
                            decodeMillis = nativeResult.decodeMs,
                            decodeFrames = nativeResult.decodeFrames,
                            decodeSamples = nativeResult.decodeSamples,
                            decodeGraphComputeMillis = nativeResult.decodeGraphComputeMs,
                            estimatedSynthesisMillis = null,
                            estimateSampleCount = 0,
                            error = saveFailure?.let { failure ->
                                "保存失败：${failure.message ?: "请检查存储空间"}。音频暂留在内存中。"
                            },
                            pendingSave = saveFailure != null,
                        )
                    }
                    playAudio()
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = "生成失败",
                            error = throwable.message ?: "生成失败",
                        )
                    }
                },
            )
        }
    }

    fun retrySave() {
        val pending = pendingGeneration ?: return
        if (_uiState.value.busy) return
        viewModelScope.launch {
            startBusy(status = "正在重新保存音频…")
            val failure = runCatching { persistGeneratedAudio(pending) }.exceptionOrNull()
            if (failure == null) pendingGeneration = null
            stopBusyTicker()
            _uiState.update {
                it.copy(
                    busy = false,
                    pendingSave = failure != null,
                    status = if (failure == null) "音频已保存" else "保存失败，可重试",
                    error = failure?.let { problem -> problem.message ?: "请检查存储空间" },
                )
            }
        }
    }

    fun startVoiceRecording() {
        if (_uiState.value.busy || recorderState.value.isRecording) return
        if (selectedVariant().id != QwenModel.defaultVariant.id) selectModel(QwenModel.defaultVariant.id)
        val file = File(voiceDir, "pending-${System.currentTimeMillis()}.wav")
        if (!recorder.start(file)) {
            _uiState.update { it.copy(error = "无法启动麦克风录音") }
        }
    }

    fun stopVoiceRecordingAndCreate(name: String, referenceText: String) {
        if (_uiState.value.busy) return
        val trimmedName = name.trim().ifBlank { "音色 ${voices.value.size + 1}" }
        val transcript = referenceText.trim()
        viewModelScope.launch {
            startBusy(status = "正在创建参考音色")
            val created = runCatching {
                val result = recorder.stop() ?: error("录音已经停止，请重新录制。")
                withContext(Dispatchers.IO) {
                    val native = ensureEngineLoaded(selectedVariant())
                    val caps = native.getModelCapabilities()
                    if (caps?.supportsCloning == false) {
                        error("当前模型不支持提取参考音色。")
                    }

                    val voiceId = "voice-${java.util.UUID.randomUUID()}"
                    val targetDir = File(voiceDir, voiceId).apply { mkdirs() }
                    val reference = File(targetDir, "reference.wav")
                    result.file.copyTo(reference, overwrite = true)

                    val embedding = File(targetDir, if (transcript.isEmpty()) "speaker.json" else "prompt.icl.json")
                    val extracted = if (transcript.isEmpty()) native.extractSpeakerEmbedding(reference.absolutePath, embedding.absolutePath)
                        else native.extractIclPrompt(reference.absolutePath, transcript, embedding.absolutePath)
                    if (!extracted) error(native.getLastError() ?: "参考音色提取失败")

                    val voice = VoiceProfileEntity(
                        voiceId = voiceId,
                        name = makeUniqueVoiceName(trimmedName),
                        referenceWavPath = reference.absolutePath,
                        speakerEmbeddingPath = embedding.absolutePath,
                        durationMillis = result.durationMillis,
                    )
                    dao.insertVoice(voice)
                    // The source recording is kept until both extraction and database save succeed.
                    runCatching { result.file.delete() }
                    voice
                }
            }

            created.fold(
                onSuccess = { voice ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            selectedVoiceId = voice.voiceId,
                            status = "音色已保存",
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    stopBusyTicker()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = "音色保存失败",
                            error = throwable.message ?: "音色保存失败",
                        )
                    }
                },
            )
        }
    }

    fun importReference(uri: Uri, name: String, referenceText: String) {
        if (_uiState.value.busy) return
        val trimmedName = name.trim().ifBlank { "音色 ${voices.value.size + 1}" }
        val transcript = referenceText.trim()
        if (selectedVariant().id != QwenModel.defaultVariant.id) selectModel(QwenModel.defaultVariant.id)
        viewModelScope.launch {
            startBusy(status = "正在保存参考音色")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val voiceId = "voice-${java.util.UUID.randomUUID()}"
                    val targetDir = File(voiceDir, voiceId)
                    check(targetDir.mkdirs()) { "无法创建音色目录" }
                    try {
                        val reference = File(targetDir, "reference.wav")
                        val resolver = getApplication<Application>().contentResolver
                        resolver.openInputStream(uri)?.use { input ->
                            reference.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= 50L * 1024 * 1024) { "参考录音不能超过 50 MB" }
                                    output.write(buffer, 0, n)
                                }
                            }
                        } ?: error("无法读取所选录音")
                        val wav = readWav(reference.absolutePath)
                        require(wav.samples.isNotEmpty() && wav.sampleRate in 8_000..96_000) { "请选择有效的 PCM 16 位 WAV 录音" }
                        val native = ensureEngineLoaded(QwenModel.defaultVariant)
                        val prompt = File(targetDir, if (transcript.isEmpty()) "speaker.json" else "prompt.icl.json")
                        val ok = if (transcript.isEmpty()) native.extractSpeakerEmbedding(reference.absolutePath, prompt.absolutePath)
                            else native.extractIclPrompt(reference.absolutePath, transcript, prompt.absolutePath)
                        check(ok) { native.getLastError() ?: "参考音色提取失败" }
                        VoiceProfileEntity(voiceId = voiceId, name = makeUniqueVoiceName(trimmedName),
                            referenceWavPath = reference.absolutePath, speakerEmbeddingPath = prompt.absolutePath,
                            durationMillis = wav.samples.size.toLong() * 1000L / wav.sampleRate)
                    } catch (failure: Exception) {
                        targetDir.deleteRecursively()
                        throw failure
                    }
                }
            }
            result.fold(
                onSuccess = { voice -> dao.insertVoice(voice); stopBusyTicker(); _uiState.update { it.copy(busy = false, selectedVoiceId = voice.voiceId, status = "音色已保存", error = null) } },
                onFailure = { error -> stopBusyTicker(); _uiState.update { it.copy(busy = false, status = "音色保存失败", error = error.message ?: "音色保存失败") } },
            )
        }
    }

    fun cancelVoiceRecording() {
        recorder.cancel()
    }

    fun deleteVoice(voice: VoiceProfileEntity) {
        if (_uiState.value.busy) {
            _uiState.update { it.copy(error = "正在处理语音，请稍后再删除音色。") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val folder = checkNotNull(File(voice.referenceWavPath).canonicalFile.parentFile) { "音色路径异常。" }
                check(folder.parentFile == voiceDir.canonicalFile && folder.name == voice.voiceId) {
                    "音色路径异常，已保留文件。"
                }
                dao.deleteVoice(voice)
                check(folder.deleteRecursively()) { "音色记录已删除，但部分文件无法清理。" }
                if (_uiState.value.selectedVoiceId == voice.voiceId) {
                    _uiState.update { it.copy(selectedVoiceId = null) }
                }
            }.onFailure { problem ->
                _uiState.update { it.copy(error = problem.message ?: "删除音色失败") }
            }
        }
    }

    fun playGeneration(generationId: Long) {
        if (_uiState.value.playing && _uiState.value.playingGenerationId == generationId) {
            stopAudio()
            return
        }
        viewModelScope.launch {
            val generation = withContext(Dispatchers.IO) { dao.getGeneration(generationId) } ?: return@launch
            val loaded = runCatching { withContext(Dispatchers.IO) { readWav(generation.wavPath) } }
            loaded.onSuccess { wav ->
                stopAudio()
                generatedAudio = wav.samples
                _uiState.update {
                    it.copy(
                        sampleRate = wav.sampleRate,
                        sampleCount = wav.samples.size,
                        status = "生成完成",
                        error = null,
                    )
                }
                playAudio(generationId)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message ?: "无法播放历史音频") }
            }
        }
    }

    fun deleteGeneration(generation: GenerationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val audio = File(generation.wavPath).canonicalFile
                check(audio.parentFile == generationDir.canonicalFile) { "音频路径异常，已保留文件。" }
                dao.deleteGeneration(generation)
                check(!audio.exists() || audio.delete()) { "记录已删除，但音频文件无法清理。" }
            }.onFailure { problem ->
                _uiState.update { it.copy(error = problem.message ?: "删除记录失败") }
            }
        }
    }

    fun playAudio(historyGenerationId: Long? = null) {
        val samples = generatedAudio ?: return
        val sampleRate = _uiState.value.sampleRate.takeIf { it > 0 } ?: 24_000
        stopAudio()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                coroutineContext.ensureActive()
                val minimumBuffer = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
                )
                check(minimumBuffer > 0) { "此设备不支持当前音频格式。" }
                val current = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(minimumBuffer, sampleRate * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = current
                activeTrack = current
                coroutineContext.ensureActive()
                _uiState.update { it.copy(playing = true, playingGenerationId = historyGenerationId) }
                current.play()
                var offset = 0
                while (offset < samples.size) {
                    coroutineContext.ensureActive()
                    val written = current.write(samples, offset, minOf(8192, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                    coroutineContext.ensureActive()
                    check(written > 0) { "音频设备未能接收数据。" }
                    offset += written
                }
                val drainDeadline = SystemClock.elapsedRealtime() + 5_000L
                while (current.playbackHeadPosition.toLong() < samples.size.toLong() && SystemClock.elapsedRealtime() < drainDeadline) {
                    coroutineContext.ensureActive()
                    kotlinx.coroutines.delay(50L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: Exception) {
                _uiState.update { it.copy(error = problem.message ?: "音频播放失败") }
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
                if (activeTrack === track) {
                    activeTrack = null
                    _uiState.update { it.copy(playing = false, playingGenerationId = null) }
                }
            }
        }
    }

    fun stopAudio() {
        playJob?.cancel()
        playJob = null
        activeTrack?.let { track -> runCatching { track.stop() } }
        _uiState.update { it.copy(playing = false, playingGenerationId = null) }
    }

    fun suggestedExportFileName(generation: GenerationEntity): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
        return "qwen3-tts-export-${LocalDateTime.now().format(formatter)}-${generation.generationId}.wav"
    }

    fun exportGenerationToUri(generation: GenerationEntity, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        File(generation.wavPath).inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: error("无法打开导出位置")
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            savedFileName = null,
                            snackbarMessage = "导出完成",
                            status = "WAV 已导出",
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            snackbarMessage = throwable.message ?: "无法导出 WAV",
                            error = null,
                        )
                    }
                },
            )
        }
    }

    private fun ensureEngineLoaded(variant: ModelVariant): QwenEngine {
        val native = synchronized(engineGuard) {
            check(!disposed) { "语音引擎已关闭" }
            engine ?: QwenEngine().also { engine = it }
        }
        val backend = selectedBackendOption()
        if (!native.setBackendPreference(backend.preference)) {
            error("无法切换到 ${backend.label} 推理方式")
        }
        val threads = selectedCpuThreadCount()
        native.setCpuThreads(threads)
        if (!_uiState.value.loaded) {
            if (!native.loadModels(modelDir.absolutePath, variant.talkerName)) {
                error(native.getLastError() ?: "模型加载失败")
            }
            val caps = native.getModelCapabilities()
            _uiState.update {
                it.copy(
                    loaded = true,
                    cpuThreads = native.getCpuThreads(),
                    activeBackendName = native.getActiveBackendName()?.takeIf { name -> name.isNotBlank() },
                    supportsCloning = caps?.supportsCloning,
                    speakerEmbeddingDim = caps?.speakerEmbeddingDim ?: 0,
                )
            }
        }
        return native
    }

    private fun preferredCpuThreadCount(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        return when {
            available >= 8 -> 6
            available >= 4 -> 4
            else -> available.coerceAtLeast(1)
        }
    }

    private fun selectedCpuThreadCount(): Int =
        _uiState.value.selectedCpuThreads.takeIf { it > 0 } ?: preferredCpuThreadCount()

    private fun selectedBackendOption(): BackendOption =
        backendOptionById(_uiState.value.selectedBackendId)

    private fun startBusy(
        status: String,
        downloading: Boolean = false,
        resetSynthesis: Boolean = false,
        synthesisEstimate: GenerationTimeEstimate? = null,
    ) {
        operationTickerJob?.cancel()
        val start = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                busy = true,
                downloading = downloading,
                error = null,
                status = status,
                operationElapsedMillis = 0L,
                synthesisFrames = if (resetSynthesis) 0 else it.synthesisFrames,
                sampleCount = if (resetSynthesis) 0 else it.sampleCount,
                synthesisMillis = if (resetSynthesis) 0 else it.synthesisMillis,
                tokenizeMillis = if (resetSynthesis) 0 else it.tokenizeMillis,
                encodeMillis = if (resetSynthesis) 0 else it.encodeMillis,
                generateMillis = if (resetSynthesis) 0 else it.generateMillis,
                decodeMillis = if (resetSynthesis) 0 else it.decodeMillis,
                decodeFrames = if (resetSynthesis) 0 else it.decodeFrames,
                decodeSamples = if (resetSynthesis) 0 else it.decodeSamples,
                decodeGraphComputeMillis = if (resetSynthesis) 0 else it.decodeGraphComputeMillis,
                savedFileName = if (resetSynthesis) null else it.savedFileName,
                estimatedSynthesisMillis = synthesisEstimate?.totalMillis ?: if (resetSynthesis) null else it.estimatedSynthesisMillis,
                estimateSampleCount = synthesisEstimate?.sampleSize ?: if (resetSynthesis) 0 else it.estimateSampleCount,
            )
        }
        operationTickerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250L)
                val elapsed = SystemClock.elapsedRealtime() - start
                _uiState.update { current ->
                    if (current.busy) current.copy(operationElapsedMillis = elapsed) else current
                }
            }
        }
    }

    private fun stopBusyTicker() {
        operationTickerJob?.cancel()
        operationTickerJob = null
    }

    private fun estimateSynthesisMillis(text: String): GenerationTimeEstimate? {
        val ratios = generations.value
            .asSequence()
            .filter { it.synthesisMillis > 0L && it.text.isNotBlank() }
            .take(20)
            .map { generation ->
                generation.synthesisMillis.toDouble() / generation.text.length.coerceAtLeast(1).toDouble()
            }
            .filter { it.isFinite() && it > 0.0 }
            .sorted()
            .toList()

        if (ratios.size < 3) return null

        val trimmed = if (ratios.size >= 5) {
            ratios.drop(1).dropLast(1)
        } else {
            ratios
        }
        val millisPerCharacter = trimmed.average()
        val estimate = (millisPerCharacter * text.length.coerceAtLeast(1)).toLong()
            .coerceAtLeast(1_000L)
        return GenerationTimeEstimate(estimate, ratios.size)
    }

    private fun refreshModelState(status: String? = null) {
        val variant = selectedVariant()
        val ready = isModelReady(variant)
        stopBusyTicker()
        _uiState.update {
            it.copy(
                modelReady = ready,
                busy = false,
                downloading = false,
                downloadTotalBytes = variant.totalBytes,
                status = status ?: if (ready) "模型已就绪" else "离线模型尚未准备",
            )
        }
    }

    private fun selectedVariant(): ModelVariant =
        QwenModel.variantById(_uiState.value.selectedModelId)

    private fun isModelReady(variant: ModelVariant): Boolean =
        variant.files.all { file ->
            val local = File(modelDir, file.name)
            local.isFile && local.length() == file.sizeBytes
        }

    private fun installBundledFiles() {
        modelDir.mkdirs()
        var completed = 0L
        QwenModel.bundledFiles.forEachIndexed { index, file ->
            val target = File(modelDir, file.name)
            if (target.isFile && target.length() == file.sizeBytes) {
                completed += file.sizeBytes
                return@forEachIndexed
            }
            val temp = File(modelDir, file.name + ".extract")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                getApplication<Application>().assets.open("models/" + file.name).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var count = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            count += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 250L) {
                                lastUpdate = now
                                val current = completed + count
                                _uiState.update { it.copy(downloadBytes = current, downloadProgress = current.toFloat() / QwenModel.bundledBytes, status = "正在准备离线模型 ${index + 1}/${QwenModel.bundledFiles.size}") }
                            }
                        }
                        output.fd.sync()
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                require(temp.length() == file.sizeBytes && hash == file.sha256) { "内置模型校验失败：${file.name}" }
                if (target.exists() && !target.delete()) error("无法更新模型：${file.name}")
                if (!temp.renameTo(target)) error("无法保存模型：${file.name}")
            } finally {
                if (temp.exists()) temp.delete()
            }
            completed += file.sizeBytes
        }
    }

    private suspend fun persistGeneratedAudio(pending: PendingGeneration) =
        withContext(NonCancellable + Dispatchers.IO) {
            check(generationDir.isDirectory || generationDir.mkdirs()) { "无法创建音频保存目录" }
            val file = pending.savedFile ?: run {
                val id = java.util.UUID.randomUUID().toString()
                val temp = File(generationDir, ".generation-$id.tmp")
                val target = File(generationDir, "generation-$id.wav")
                try {
                    java.io.FileOutputStream(temp).use { output ->
                        writeWav(output, pending.samples, pending.sampleRate)
                        output.fd.sync()
                    }
                    check(temp.renameTo(target)) { "无法保存生成的音频" }
                    pending.savedFile = target
                    target
                } finally {
                    if (temp.exists()) temp.delete()
                }
            }
            dao.insertGeneration(
                GenerationEntity(
                    text = pending.text,
                    voiceId = pending.voiceId,
                    voiceName = pending.voiceName,
                    wavPath = file.absolutePath,
                    sampleRate = pending.sampleRate,
                    sampleCount = pending.samples.size,
                    synthesisMillis = pending.synthesisMillis,
                ),
            )
        }

    private fun makeUniqueVoiceName(name: String): String {
        val existing = voices.value.map { it.name }.toSet()
        if (name !in existing) return name
        var index = 2
        while ("$name $index" in existing) index += 1
        return "$name $index"
    }

    private fun writeWav(output: OutputStream, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        output.writeAscii("RIFF")
        output.writeIntLe(36 + dataBytes)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(1)
        output.writeIntLe(sampleRate)
        output.writeIntLe(sampleRate * 2)
        output.writeShortLe(2)
        output.writeShortLe(16)
        output.writeAscii("data")
        output.writeIntLe(dataBytes)
        samples.forEach { sample ->
            val clamped = sample.coerceIn(-1f, 1f)
            output.writeShortLe((clamped * 32767f).toInt())
        }
    }

    override fun onCleared() {
        recorder.cancel(discard = false)
        stopAudio()
        stopBusyTicker()
        val oldEngine = synchronized(engineGuard) {
            disposed = true
            engine.also { engine = null }
        }
        // close waits for an in-flight synchronized native call without blocking the UI.
        if (oldEngine != null) Thread({ runCatching { oldEngine.close() } }, "qwen-engine-close").apply {
            isDaemon = true
            start()
        }
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            QwenTtsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QwenTtsApp()
                }
            }
        }
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    object Studio : AppDestination("studio", "语音合成", { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) })
    object Voices : AppDestination("voices", "音色克隆", { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) })
    object History : AppDestination("history", "项目记录", { Icon(Icons.Default.History, contentDescription = null) })
    object Settings : AppDestination("settings", "设置", { Icon(Icons.Default.Settings, contentDescription = null) })
}

private val appDestinations = listOf(
    AppDestination.Studio,
    AppDestination.Voices,
    AppDestination.History,
    AppDestination.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QwenTtsApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Studio.route
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearSnackbarMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(Color(0xFFFF5F57), Color(0xFFFFBD2E), Color(0xFF28C840)).forEach { color ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Qwen-voice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("离线模式", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                appDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(AppDestination.Studio.route) { saveState = true }
                            }
                        },
                        icon = destination.icon,
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Studio.route,
            modifier = Modifier.padding(padding),
            enterTransition = {
                val direction = if (destinationIndex(targetState.destination.route) >= destinationIndex(initialState.destination.route)) {
                    AnimatedContentTransitionScope.SlideDirection.Start
                } else {
                    AnimatedContentTransitionScope.SlideDirection.End
                }
                slideIntoContainer(direction, animationSpec = tween(260))
            },
            exitTransition = {
                val direction = if (destinationIndex(targetState.destination.route) >= destinationIndex(initialState.destination.route)) {
                    AnimatedContentTransitionScope.SlideDirection.Start
                } else {
                    AnimatedContentTransitionScope.SlideDirection.End
                }
                slideOutOfContainer(direction, animationSpec = tween(260))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(260))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(260))
            },
        ) {
            composable(AppDestination.Studio.route) {
                StudioScreen(viewModel)
            }
            composable(AppDestination.Voices.route) {
                VoicesScreen(viewModel)
            }
            composable(AppDestination.History.route) {
                HistoryScreen(viewModel)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(viewModel)
            }
        }
    }
}

private fun destinationIndex(route: String?): Int =
    appDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val voices by viewModel.voices.collectAsState()
    var showVoicePicker by remember { mutableStateOf(false) }
    val isPreset = state.selectedModelId == QwenModel.customVariant.id
    val selectedVoiceName = if (isPreset) state.selectedPreset else voices.firstOrNull { it.voiceId == state.selectedVoiceId }?.name ?: "默认音色"

    if (showVoicePicker) {
        VoicePickerSheet(
            voices = voices,
            selectedVoiceId = state.selectedVoiceId,
            onDismiss = { showVoicePicker = false },
            onVoiceSelected = { voiceId ->
                viewModel.selectVoice(voiceId)
                showVoicePicker = false
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("语音合成", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("选择音色与模型，在手机本地离线生成语音。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QwenModel.variants.forEach { variant ->
                    FilterChip(
                        selected = state.selectedModelId == variant.id,
                        onClick = { viewModel.selectModel(variant.id) },
                        enabled = !state.busy,
                        label = { Text(variant.label) },
                    )
                }
            }
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(state.status, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.pendingSave) {
                OutlinedButton(onClick = viewModel::retrySave, enabled = !state.busy) {
                    Text("重试保存已生成的音频")
                }
            }
            ComposerPanel(
                state = state,
                selectedVoiceName = selectedVoiceName,
                onTextChange = viewModel::updateText,
                onVoicePickerClick = { if (!state.busy) showVoicePicker = true },
                onLanguageChange = viewModel::updateLanguage,
            )
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("语速  ${String.format(Locale.ROOT, "%.2f", state.speed)}×", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = state.speed,
                        onValueChange = viewModel::updateSpeed,
                        enabled = !state.busy,
                        valueRange = 0.5f..1.5f,
                    )
                    Text("调整语速，尽量保持原有音调。", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isPreset) {
                PresetDropdown(state.selectedPreset, !state.busy, viewModel::updatePreset)
                OutlinedTextField(
                    value = state.stylePrompt,
                    onValueChange = viewModel::updateStyle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    label = { Text("情感与风格描述（可选）") },
                    placeholder = { Text("例如：温柔、平静地讲述") },
                )
            }
            if ((state.busy && !state.downloading) || state.sampleCount > 0) {
                ResultPanel(
                    state = state,
                    onPlay = viewModel::playAudio,
                    onStop = viewModel::stopAudio,
                )
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Button(
                onClick = viewModel::synthesize,
                enabled = state.modelReady && state.text.isNotBlank() && !state.busy && !state.pendingSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.busy && !state.downloading) "正在生成…" else "生成语音")
            }
        }
    }
}

@Composable
private fun VoicesScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val voices by viewModel.voices.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    var voiceToDelete by remember { mutableStateOf<VoiceProfileEntity?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importReference(uri, voiceName, referenceText)
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.startVoiceRecording()
    }

    voiceToDelete?.let { voice ->
        AlertDialog(
            onDismissRequest = { voiceToDelete = null },
            title = { Text("删除参考音色？") },
            text = { Text("将删除此应用保存的参考录音副本和音色数据，原始导入文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteVoice(voice); voiceToDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { voiceToDelete = null }) { Text("取消") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("创建参考音色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("音色名称") },
                )
                OutlinedTextField(
                    value = referenceText, onValueChange = { referenceText = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("参考录音文字（可选）") },
                    placeholder = { Text("填写与录音一致的文字；留空仅使用音色特征") },
                )
                OutlinedButton(onClick = { fileLauncher.launch(arrayOf("audio/wav", "audio/x-wav")) },
                    enabled = !state.busy && !recorderState.isRecording, modifier = Modifier.fillMaxWidth()) {
                    Text("选择 WAV 参考录音并保存")
                }
                if (recorderState.isRecording) {
                    LinearProgressIndicator(
                        progress = { recorderState.level.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("录音中 ${formatDuration(recorderState.elapsedMillis)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (recorderState.isRecording) {
                                viewModel.stopVoiceRecordingAndCreate(voiceName, referenceText)
                            } else if (hasPermission) {
                                viewModel.startVoiceRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !state.busy || recorderState.isRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (recorderState.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (recorderState.isRecording) "停止并保存" else "录制声音")
                    }
                    OutlinedButton(
                        onClick = viewModel::cancelVoiceRecording,
                        enabled = recorderState.isRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                }
                if (state.supportsCloning == false) {
                    Text(
                        "当前模型不支持声音克隆。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("音色克隆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (voices.isEmpty()) {
                    Text("还没有保存的音色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    voices.forEach { voice ->
                        VoiceRow(
                            voice = voice,
                            selected = state.selectedVoiceId == voice.voiceId,
                            onSelect = { viewModel.selectVoice(voice.voiceId) },
                            onDelete = { voiceToDelete = voice },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val generations by viewModel.generations.collectAsState()
    var pendingExport by remember { mutableStateOf<GenerationEntity?>(null) }
    var generationToDelete by remember { mutableStateOf<GenerationEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav"),
    ) { uri ->
        val generation = pendingExport
        pendingExport = null
        if (uri != null && generation != null) {
            viewModel.exportGenerationToUri(generation, uri)
        }
    }
    generationToDelete?.let { generation ->
        AlertDialog(
            onDismissRequest = { generationToDelete = null },
            title = { Text("删除生成记录？") },
            text = { Text("将删除该记录和应用内保存的 WAV 音频，已另外导出的文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGeneration(generation); generationToDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { generationToDelete = null }) { Text("取消") } },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (generations.isEmpty()) {
            item {
                Text("还没有生成的语音。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(generations, key = { it.generationId }) { generation ->
            val isPlayingThis = state.playing && state.playingGenerationId == generation.generationId
            var menuExpanded by remember(generation.generationId) { mutableStateOf(false) }
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(generation.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${generation.voiceName} · ${formatSeconds(generation.sampleCount.toDouble() / generation.sampleRate.coerceAtLeast(1))} · ${formatDate(generation.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            if (isPlayingThis) {
                                viewModel.stopAudio()
                            } else {
                                viewModel.playGeneration(generation.generationId)
                            }
                        },
                    ) {
                        Icon(
                            if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPlayingThis) "停止" else "播放")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("导出") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    pendingExport = generation
                                    exportLauncher.launch(viewModel.suggestedExportFileName(generation))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    generationToDelete = generation
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ModelPanel(
            state = state,
            onDownload = viewModel::downloadModel,
            onLoad = viewModel::loadModel,
        )
        RuntimePanel(
            state = state,
            onCpuThreadsChange = viewModel::updateCpuThreads,
        )
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WAV 导出采样率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("16 位单声道；48 kHz 由模型原始音频转换得到。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24_000, 48_000).forEach { rate ->
                        FilterChip(
                            selected = state.outputSampleRate == rate,
                            onClick = { viewModel.updateOutputSampleRate(rate) },
                            enabled = !state.busy,
                            label = { Text("${rate / 1000} kHz") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPanel(
    state: QwenTtsUiState,
    selectedVoiceName: String,
    onTextChange: (String) -> Unit,
    onVoicePickerClick: () -> Unit,
    onLanguageChange: (Int) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                label = { Text("输入文本") },
                supportingText = { Text("${state.text.length} / 5000") },
                isError = state.text.length > 5000,
            )
            OutlinedButton(
                onClick = onVoicePickerClick,
                enabled = !state.busy && state.selectedModelId == QwenModel.defaultVariant.id,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text("当前音色", style = MaterialTheme.typography.labelMedium)
                    Text(selectedVoiceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LanguageDropdown(
                selectedLanguageId = state.selectedLanguageId,
                enabled = !state.busy,
                onLanguageChange = onLanguageChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdown(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val voices = listOf("Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, enabled = enabled,
            modifier = Modifier.menuAnchor().fillMaxWidth(), label = { Text("预设音色") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice -> DropdownMenuItem(text = { Text(voice) }, onClick = { onSelect(voice); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    voices: List<VoiceProfileEntity>,
    selectedVoiceId: String?,
    onDismiss: () -> Unit,
    onVoiceSelected: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("选择参考音色", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            VoicePickerItem(
                title = "默认音色",
                subtitle = "使用模型默认声音",
                selected = selectedVoiceId == null,
                onClick = { onVoiceSelected(null) },
            )
            voices.forEach { voice ->
                VoicePickerItem(
                    title = voice.name,
                    subtitle = "参考录音 ${formatDuration(voice.durationMillis)}",
                    selected = selectedVoiceId == voice.voiceId,
                    onClick = { onVoiceSelected(voice.voiceId) },
                )
            }
        }
    }
}

@Composable
private fun VoicePickerItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = "已选择", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(selected = selected, onClick = onSelect, label = { Text(if (selected) "已选" else "使用") })
        Column(Modifier.weight(1f)) {
            Text(voice.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDuration(voice.durationMillis)} reference · ${formatDate(voice.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguageId: Int,
    enabled: Boolean,
    onLanguageChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = languageOptions.firstOrNull { it.id == selectedLanguageId } ?: languageOptions.first()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text("语言") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languageOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onLanguageChange(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelPanel(
    state: QwenTtsUiState,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
) {
    val selectedVariant = QwenModel.variantById(state.selectedModelId)
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedVariant.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "模型文件 ${formatBytes(selectedVariant.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(if (state.modelReady) "已就绪" else "未就绪", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            if (state.downloading) {
                LinearProgressIndicator(progress = { state.downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(state.downloadBytes)} / ${formatBytes(state.downloadTotalBytes)}", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDownload,
                    enabled = !state.busy && !state.modelReady,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.modelReady) "模型已就绪" else "准备内置模型")
                }
                OutlinedButton(
                    onClick = onLoad,
                    enabled = state.modelReady && !state.loaded && !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.loaded) "已加载" else "加载模型")
                }
            }
        }
    }
}

@Composable
private fun RuntimePanel(
    state: QwenTtsUiState,
    onCpuThreadsChange: (Int) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Text("运行性能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "使用手机 CPU 离线推理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6).forEach { threads ->
                    FilterChip(
                        selected = state.selectedCpuThreads == threads,
                        onClick = { onCpuThreadsChange(threads) },
                        enabled = !state.busy,
                        label = { Text("$threads 线程") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(state: QwenTtsUiState, onPlay: () -> Unit, onStop: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.busy && !state.downloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                GenerationStats(state)
            } else {
                if (state.sampleCount > 0 && state.sampleRate > 0) {
                    Text(
                        "音频 ${formatSeconds(state.sampleCount.toDouble() / state.sampleRate.toDouble())} · 生成用时 ${formatDuration(state.synthesisMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { if (state.playing) onStop() else onPlay() },
                    enabled = state.sampleCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        if (state.playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.playing) "停止" else "播放")
                }
            }
        }
    }
}

@Composable
private fun GenerationStats(state: QwenTtsUiState) {
    val generatedAudioSeconds = state.synthesisFrames * 0.08
    val remainingMillis = state.estimatedSynthesisMillis
        ?.minus(state.operationElapsedMillis)
        ?.coerceAtLeast(0L)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "已用时 ${formatDuration(state.operationElapsedMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.synthesisFrames > 0) {
            Text(
                "当前段已生成约 ${formatSeconds(generatedAudioSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (remainingMillis != null && state.estimateSampleCount >= 3) {
            Text(
                "预计还需 ${formatDuration(remainingMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class WavData(val samples: FloatArray, val sampleRate: Int)

private fun readWav(path: String): WavData {
    val source = File(path)
    require(source.length() in 44L..(50L * 1024 * 1024)) { "请选择不超过 50 MB 的 WAV 音频" }
    val bytes = source.readBytes()
    fun id(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
    fun intLe(offset: Int): Int = (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
    fun shortLe(offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    require(id(0) == "RIFF" && id(8) == "WAVE") { "请选择 WAV 文件" }
    var cursor = 12
    var sampleRate = 0
    var channels = 0
    var bits = 0
    var audioFormat = 0
    var dataOffset = -1
    var dataSize = 0
    while (cursor + 8 <= bytes.size) {
        val chunkId = id(cursor)
        val size = intLe(cursor + 4)
        val payload = cursor + 8
        require(size >= 0 && size <= bytes.size - payload) { "WAV 文件不完整" }
        if (chunkId == "fmt ") {
            require(size >= 16) { "WAV 格式信息不完整" }
            audioFormat = shortLe(payload)
            channels = shortLe(payload + 2)
            sampleRate = intLe(payload + 4)
            bits = shortLe(payload + 14)
        } else if (chunkId == "data") {
            dataOffset = payload
            dataSize = size
            break
        }
        val next = payload.toLong() + size + (size and 1)
        require(next <= bytes.size) { "WAV 文件不完整" }
        cursor = next.toInt()
    }
    require(dataOffset >= 0 && audioFormat == 1 && bits == 16 && channels in 1..2 && sampleRate in 8_000..96_000) {
        "请选择单声道或立体声的 PCM 16 位 WAV 音频"
    }
    val frameBytes = channels * 2
    require(dataSize >= frameBytes && dataSize % frameBytes == 0) { "WAV 音频数据为空或不完整" }
    val samples = FloatArray(dataSize / frameBytes)
    var offset = dataOffset
    for (i in samples.indices) {
        var sum = 0f
        repeat(channels) {
            val value = ((bytes[offset + 1].toInt() shl 8) or (bytes[offset].toInt() and 0xff)).toShort()
            sum += value / 32768f
            offset += 2
        }
        samples[i] = sum / channels
    }
    return WavData(samples, sampleRate)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.1f %s".format(value, units[unitIndex])
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "%d:%02d".format(Locale.US, minutes, seconds) else "%ds".format(Locale.US, seconds)
}

private fun formatSeconds(seconds: Double): String =
    "%.1fs".format(Locale.US, seconds.coerceAtLeast(0.0))

private fun formatDate(epochMillis: Long): String {
    val date = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
    return date.format(java.util.Date(epochMillis))
}

private fun backendOptionById(id: String): BackendOption =
    backendOptions.firstOrNull { it.id == id } ?: defaultBackendOption

private fun OutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

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
