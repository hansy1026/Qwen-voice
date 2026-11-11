"""On-disk settings, cloned voices and generated projects."""

from __future__ import annotations

import json
import os
import shutil
import uuid
from datetime import datetime
from pathlib import Path

import numpy as np
import soundfile as sf


def _atomic_json(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    try:
        with temporary.open("x", encoding="utf-8") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _id(value):
    try:
        result = str(uuid.UUID(str(value)))
    except (ValueError, TypeError, AttributeError):
        raise ValueError("无效的记录编号。") from None
    if result != str(value).lower():
        raise ValueError("无效的记录编号。")
    return result


class Storage:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.data = self.root / "data"
        self.voices = self.data / "voices"
        self.projects = self.data / "projects"
        self.settings_path = self.data / "settings.json"

    def load_settings(self):
        defaults = {"sample_rate": 24000, "output_dir": str(self.root / "outputs")}
        if not self.settings_path.exists():
            return defaults
        try:
            data = json.loads(self.settings_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ValueError(f"读取设置失败，原文件已保留：{exc}") from exc
        if not isinstance(data, dict):
            raise ValueError("设置文件格式无效，原文件已保留。")
        defaults.update(data)
        if defaults["sample_rate"] not in (24000, 48000):
            raise ValueError("设置文件中的采样率无效。")
        self._output_dir(defaults["output_dir"])
        return defaults

    def _output_dir(self, value):
        if not isinstance(value, str) or not value.strip():
            raise ValueError("请选择保存目录。")
        output = Path(value)
        if not output.is_absolute():
            raise ValueError("保存目录必须是绝对路径。")
        output = output.resolve()
        if self.root.drive and output.drive.lower() != self.root.drive.lower():
            raise ValueError(f"请将生成文件保存在 {self.root.drive} 盘。")
        return output

    def save_settings(self, settings):
        if not isinstance(settings, dict) or settings.get("sample_rate") not in (24000, 48000):
            raise ValueError("请选择 24 kHz 或 48 kHz 采样率。")
        self._output_dir(settings.get("output_dir"))
        _atomic_json(self.settings_path, settings)

    @staticmethod
    def _list_records(directory, filename):
        if not directory.exists():
            return []
        records = []
        for folder in directory.iterdir():
            if not folder.is_dir() or folder.is_symlink():
                continue
            try:
                _id(folder.name)
                record = json.loads((folder / filename).read_text(encoding="utf-8"))
                if isinstance(record, dict) and record.get("id") == folder.name:
                    records.append(record)
            except (OSError, ValueError, json.JSONDecodeError):
                continue
        return sorted(records, key=lambda x: x.get("created_at", ""), reverse=True)

    def list_voices(self):
        return self._list_records(self.voices, "voice.json")

    def import_voice(self, name, audio_path, reference_text, x_vector_only=False):
        name = str(name).strip()
        reference_text = str(reference_text or "").strip()
        if not name or len(name) > 80:
            raise ValueError("请填写不超过 80 字的音色名称。")
        if not x_vector_only and not reference_text:
            raise ValueError("请填写参考录音的文字内容。")
        source = Path(audio_path)
        if not source.is_file():
            raise FileNotFoundError("参考录音不存在。")
        try:
            info = sf.info(str(source))
        except (RuntimeError, OSError) as exc:
            raise ValueError("无法读取该音频，请选择可读取的音频文件。") from exc
        if not info.frames or not info.samplerate:
            raise ValueError("参考音频为空。")
        voice_id = str(uuid.uuid4())
        folder = self.voices / voice_id
        folder.mkdir(parents=True, exist_ok=False)
        try:
            suffix = source.suffix.lower()
            if not suffix or len(suffix) > 10 or not suffix[1:].isalnum():
                suffix = ".wav"
            target = folder / ("reference" + suffix)
            shutil.copyfile(source, target)
            record = {
                "kind": "clone", "id": voice_id, "name": name,
                "reference_path": str(target), "reference_text": reference_text,
                "x_vector_only": bool(x_vector_only),
                "created_at": datetime.now().astimezone().isoformat(timespec="seconds"),
            }
            _atomic_json(folder / "voice.json", record)
            return record
        except Exception:
            shutil.rmtree(folder)
            raise

    def _delete_record(self, directory, identifier, metadata_file):
        folder = directory / _id(identifier)
        if folder.is_symlink() or not folder.is_dir():
            raise FileNotFoundError("找不到该记录。")
        try:
            metadata = json.loads((folder / metadata_file).read_text(encoding="utf-8"))
            if not isinstance(metadata, dict) or metadata.get("id") != folder.name:
                raise ValueError("记录编号不匹配。")
        except (OSError, ValueError) as exc:
            raise ValueError("记录不完整，为避免误删已保留文件。") from exc
        shutil.rmtree(folder)

    def delete_voice(self, identifier):
        self._delete_record(self.voices, identifier, "voice.json")

    def list_history(self):
        return self._list_records(self.projects, "metadata.json")

    def save_generation(self, audio, sample_rate, metadata):
        if sample_rate not in (24000, 48000):
            raise ValueError("采样率无效。")
        samples = np.asarray(audio, dtype=np.float32)
        if samples.ndim != 1 or not samples.size or not np.isfinite(samples).all():
            raise ValueError("生成的音频无效，无法保存。")
        if not isinstance(metadata, dict):
            raise ValueError("项目信息无效。")
        output_dir = self._output_dir(self.load_settings()["output_dir"])
        output_dir.mkdir(parents=True, exist_ok=True)
        project_id = str(uuid.uuid4())
        folder = self.projects / project_id
        folder.mkdir(parents=True, exist_ok=False)
        output = output_dir / f"qwen-{project_id}.wav"
        created_audio = False
        try:
            with output.open("xb") as stream:
                created_audio = True
                sf.write(stream, np.clip(samples, -1.0, 1.0), sample_rate, format="WAV", subtype="PCM_16")
                stream.flush()
                os.fsync(stream.fileno())
            text = str(metadata.get("text", ""))
            record = {key: metadata.get(key, default) for key, default in (
                ("text", ""), ("voice_id", ""), ("voice_name", ""), ("voice_kind", ""),
                ("model_id", "0.6B"), ("speed", 1.0), ("style", ""),
            )}
            record.update({
                "id": project_id,
                "created_at": datetime.now().astimezone().isoformat(timespec="seconds"),
                "title": str(metadata.get("title") or text[:24] or "未命名音频"),
                "audio_path": str(output), "duration": len(samples) / sample_rate,
                "sample_rate": sample_rate,
            })
            _atomic_json(folder / "metadata.json", record)
            return record
        except Exception:
            shutil.rmtree(folder)
            if created_audio:
                output.unlink(missing_ok=True)
            raise

    def delete_history(self, identifier):
        self._delete_record(self.projects, identifier, "metadata.json")
