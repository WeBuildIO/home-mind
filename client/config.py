import os
import pyaudio

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))

# 音频配置
AUDIO = {
    "format": pyaudio.paInt16,
    "channels": 1,
    "rate": 16000,
    "chunk": 1024,
    "record_seconds": 5,
}

# 服务端配置
SERVER = {
    "url": "http://192.168.2.102:8080/api/speech/recognize-chat",
    "timeout": 60,
}

# Snowboy唤醒配置
SNOWBOY = {
    "model_path": os.path.join(ROOT_DIR, "models", "wakeword.pmdl"),
    "sensitivity": 0.45,
    "apply_frontend": True,
}