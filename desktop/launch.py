from pathlib import Path
import os
import sys

ROOT = Path(os.environ.get("QWEN_VOICE_HOME") or (Path(sys.executable).resolve().parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent.parent)).resolve()
os.environ["QWEN_VOICE_HOME"] = str(ROOT)
for name, folder in {"HF_HOME": "hf-cache", "MODELSCOPE_CACHE": "modelscope-cache", "NUMBA_CACHE_DIR": "numba-cache", "MPLCONFIGDIR": "mpl-cache", "TEMP": "temp", "TMP": "temp"}.items():
    path = ROOT / "work" / folder
    path.mkdir(parents=True, exist_ok=True)
    os.environ[name] = str(path)
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
os.environ["TOKENIZERS_PARALLELISM"] = "false"
sys.dont_write_bytecode = True
if getattr(sys, "frozen", False):
    os.chdir(sys._MEIPASS)
from app import main
sys.exit(main())