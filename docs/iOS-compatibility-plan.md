# iPhone 设计与兼容性准备

本文件是未来实现的准备，不代表已生成或验证 iOS 应用。手机竖屏视觉概念见同目录 05-mobile-concept.png；图片中的示例文字和功能需以最终实现为准。

- 界面沿用碧蓝 #45C4BE、奶油粉 #F4E6DD，采用可缩放矢量圆角和适配安全区域的布局。保留合成、音色库/克隆、记录与设置的主要流程；左上角三色圆点作为装饰，不承担窗口控制功能。
- 模型推理目标为设备本地离线；评估 C++ GGML 或 Apple 平台原生运行时，逐项验证 0.6B 声音克隆、参考录音/文字、1.7B 预设音色及情感描述。必须在目标机型测量内存、耗电、发热、速度与音质，不能从 Android 或 Windows 测试推断 iOS 结果。
- 手机安装所需的模型选择、体积、首次安装方式和版本更新方式需要在真实 iOS 打包方案中验证；不假设安卓 GGUF 或 JNI 可以直接用于 iOS。
- 用户没有 Mac 或 iPhone，暂不制作 IPA、不宣称 iOS 已构建、签名或真机验证。未来需要 macOS/Xcode 构建和签名环境以及实际 iPhone。发布 App Store 还需要相应 Apple 开发者账户与审核。
- 后续准备：选定目标 iPhone 和最低 iOS 版本；在 Mac 上编译并测试底层库；验证录音权限、文件导入与导出、后台状态处理、音频播放；为模型及开源依赖核对许可证；再决定分发方式。

参考：
- https://doc.qt.io/qtforpython-6/deployment/deployment-pyside6-ios-deploy.html
- https://docs.flutter.dev/deployment/ios
- https://github.com/Danmoreng/qwen3-tts.cpp
