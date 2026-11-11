"""Local Qwen3-TTS inference for the desktop application."""

from __future__ import annotations

import gc
import math
import re
from pathlib import Path

import numpy as np


SPEAKERS = (
    "Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric",
    "Ryan", "Aiden", "Ono_Anna", "Sohee",
)
MODEL_NAMES = {
    ("preset", "0.6B"): "Qwen--Qwen3-TTS-12Hz-0.6B-CustomVoice",
    ("preset", "1.7B"): "Qwen--Qwen3-TTS-12Hz-1.7B-CustomVoice",
    ("clone", "0.6B"): "Qwen--Qwen3-TTS-12Hz-0.6B-Base",
}


def split_text(text: str, limit: int = 120) -> list[str]:
    """Split text into short sequential chunks without dropping characters."""
    chunks = []
    pending = ""
    for sentence in re.split(r"(?<=[。！？!?；;\n])", text):
        while len(sentence) > limit:
            if pending:
                chunks.append(pending)
                pending = ""
            cut = max(sentence.rfind("，", 0, limit + 1), sentence.rfind(",", 0, limit + 1))
            cut = cut + 1 if cut >= limit // 3 else limit
            chunks.append(sentence[:cut])
            sentence = sentence[cut:]
        if len(pending) + len(sentence) > limit:
            chunks.append(pending)
            pending = ""
        pending += sentence
    if pending:
        chunks.append(pending)
    return [chunk for chunk in chunks if chunk.strip()]


class Engine:
    """One instance is owned and called by a single background worker."""

    def __init__(self, root):
        self.root = Path(root)
        self._model = None
        self._model_key = None

    def load(self, kind: str, model_id: str, progress=None):
        key = (kind, model_id)
        if key not in MODEL_NAMES:
            raise ValueError("该音色不支持所选模型；克隆音色目前只支持 0.6B。")
        if self._model is not None and self._model_key == key:
            return self._model
        import torch

        self._model = None
        self._model_key = None
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
        if not torch.cuda.is_available():
            raise RuntimeError("未检测到可用的 NVIDIA CUDA 显卡。")
        model_path = self.root / "work" / "modelscope-cache" / "models" / MODEL_NAMES[key] / "snapshots" / "master"
        if not (model_path / "model.safetensors").is_file():
            raise FileNotFoundError(f"没有找到模型文件：{model_path}")
        if progress:
            progress("正在加载模型，首次加载可能需要一些时间…")
        from qwen_tts import Qwen3TTSModel

        self._model = Qwen3TTSModel.from_pretrained(
            str(model_path), device_map="cuda:0", dtype=torch.bfloat16,
            attn_implementation="sdpa",
        )
        self._model_key = key
        return self._model

    def synthesize(self, text, voice, model_id, speed, style, sample_rate, progress=None):
        text = str(text).strip()
        if not text:
            raise ValueError("请输入要合成的文本。")
        if len(text) > 5000:
            raise ValueError("输入文本不能超过 5000 字。")
        if not isinstance(voice, dict):
            raise ValueError("请选择音色。")
        kind = voice.get("kind")
        if (kind, model_id) not in MODEL_NAMES:
            raise ValueError("该音色不支持所选模型；克隆音色目前只支持 0.6B。")
        try:
            speed = float(speed)
        except (TypeError, ValueError):
            raise ValueError("语速值无效。") from None
        if not math.isfinite(speed) or not 0.5 <= speed <= 2.0:
            raise ValueError("语速必须在 0.5 到 2.0 倍之间。")
        if sample_rate not in (24000, 48000):
            raise ValueError("采样率只能为 24 kHz 或 48 kHz。")
        style = (style or "").strip()
        if style and (kind != "preset" or model_id != "1.7B"):
            raise ValueError("情感描述仅支持 1.7B 预设音色。")
        if kind == "preset" and voice.get("id") not in SPEAKERS:
            raise ValueError("所选预设音色不存在。")
        if kind == "clone":
            ref_path = Path(voice.get("reference_path") or "")
            if not ref_path.is_file():
                raise FileNotFoundError("找不到该克隆音色的参考录音。")
            if not voice.get("x_vector_only") and not (voice.get("reference_text") or "").strip():
                raise ValueError("请填写参考录音的文字内容。")

        model = self.load(kind, model_id, progress)
        pieces = []
        native_rate = None
        parts = split_text(text)
        for index, part in enumerate(parts, 1):
            if progress:
                progress(f"正在生成语音（第 {index}/{len(parts)} 段）…")
            if kind == "preset":
                wavs, sr = model.generate_custom_voice(
                    text=part, speaker=voice["id"], language="Auto",
                    instruct=style if model_id == "1.7B" else None,
                )
            else:
                wavs, sr = model.generate_voice_clone(
                    text=part, language="Auto", ref_audio=str(ref_path),
                    ref_text=voice.get("reference_text", ""),
                    x_vector_only_mode=bool(voice.get("x_vector_only")),
                )
            audio = np.asarray(wavs[0], dtype=np.float32).reshape(-1)
            if not len(audio) or not np.isfinite(audio).all():
                raise RuntimeError("模型没有生成有效的音频，请重试。")
            if native_rate is not None and sr != native_rate:
                raise RuntimeError("生成音频的采样率不一致。")
            native_rate = int(sr)
            pieces.append(audio)
        if not pieces:
            raise ValueError("文本不包含可合成的内容。")
        audio = np.concatenate(pieces)
        if speed != 1.0:
            if progress:
                progress("正在调整语速…")
            import librosa
            audio = librosa.effects.time_stretch(audio, rate=speed)
        if native_rate != sample_rate:
            if progress:
                progress("正在转换采样率…")
            from scipy.signal import resample_poly
            divisor = math.gcd(native_rate, sample_rate)
            audio = resample_poly(audio, sample_rate // divisor, native_rate // divisor)
        audio = np.clip(audio, -1.0, 1.0).astype(np.float32)
        return {"audio": audio, "sample_rate": sample_rate, "duration": len(audio) / sample_rate}
