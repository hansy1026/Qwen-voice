from pathlib import Path
from PyInstaller.utils.hooks import collect_all, collect_submodules, copy_metadata

root = Path(SPECPATH)
datas = copy_metadata("qwen-tts")
binaries = []
hiddenimports = ["transformers.models.auto", "transformers.models.qwen2.tokenization_qwen2_fast", "transformers.models.mimi.modeling_mimi"]
hiddenimports += collect_submodules("transformers.models.mimi")
hiddenimports += collect_submodules("transformers.models.qwen2")
for package in ("qwen_tts", "librosa", "numba"):
    package_data, package_binaries, package_imports = collect_all(package)
    datas += package_data
    binaries += package_binaries
    hiddenimports += package_imports

analysis = Analysis([str(root / "launch.py")], pathex=[str(root)], binaries=binaries,
                    datas=datas, hiddenimports=hiddenimports, hookspath=[], hooksconfig={},
                    runtime_hooks=[], excludes=[], noarchive=False, optimize=0)
# Qt uses Windows system ICU; other bundled copies can be incompatible.
analysis.binaries = [item for item in analysis.binaries if item[0].lower() != "icuuc.dll"]
pyz = PYZ(analysis.pure)
exe = EXE(pyz, analysis.scripts, [], exclude_binaries=True, name="Qwen-voice", debug=False,
          bootloader_ignore_signals=False, strip=False, upx=True, console=False,
          disable_windowed_traceback=False, argv_emulation=False, target_arch=None,
          codesign_identity=None, entitlements_file=None)
coll = COLLECT(exe, analysis.binaries, analysis.datas, strip=False, upx=True,
               upx_exclude=[], name="Qwen-voice-Windows")