# 第三方项目与许可证

- Android 应用基于 Danmoreng/qwen3-tts-android，MIT，原始许可见 android/LICENSE。https://github.com/Danmoreng/qwen3-tts-android
- Android 原生引擎使用 Danmoreng/qwen3-tts.cpp，MIT；其源码通过 Git 子模块引用，并包含各自的许可证。https://github.com/Danmoreng/qwen3-tts.cpp
- GGML 使用 MIT 许可证，并由原生引擎的嵌套子模块引用。https://github.com/ggml-org/ggml
- Qwen3-TTS 官方项目和各模型按上游指定许可证提供；模型并不包含在本仓库内。https://github.com/QwenLM/Qwen3-TTS
- Android GGUF 模型转换版由 Serveurperso 提供，采用 Apache License 2.0；安装包内随附许可文本与相关组件声明。https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF
- Jetpack Compose、AndroidX Media3、PySide6、PyTorch 和其他依赖按各自许可证分发。重新分发二进制程序时应同时遵循其许可和随附的第三方声明。