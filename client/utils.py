"""通用工具函数：音频采集、音频播放、时间格式化（无业务依赖）"""
import pyaudio
import base64
from datetime import datetime
from config import AUDIO

# ---------------------- 配置（从config.py读取，保持统一） ----------------------
SILENCE_TIMEOUT = 5      # 静默超时（秒）
SILENCE_THRESHOLD = 500  # 静音阈值（字节）

def format_timestamp(timestamp: int) -> str:
    """格式化时间戳（毫秒→YYYY-MM-DD HH:MM:SS）"""
    try:
        return datetime.fromtimestamp(timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")
    except:
        return "未知时间"

def record_audio_with_silence_detect() -> bytes | None:
    """采集PCM音频（含静默检测）→ 返回：音频字节串/None（静默超时）"""
    p = pyaudio.PyAudio()
    stream = p.open(
        format=AUDIO["format"],
        channels=AUDIO["channels"],
        rate=AUDIO["rate"],
        input=True,
        frames_per_buffer=AUDIO["chunk"]
    )

    print(f"\n🎤 请说话（{SILENCE_TIMEOUT}秒无声音退出）...")
    frames = []
    silence_start = None
    max_frames = int(AUDIO["rate"] / AUDIO["chunk"] * AUDIO["record_seconds"])

    for _ in range(max_frames):
        data = stream.read(AUDIO["chunk"])
        frames.append(data)

        # 静默检测逻辑
        if len(data) < SILENCE_THRESHOLD:
            silence_start = silence_start or time.time()
            if time.time() - silence_start >= SILENCE_TIMEOUT:
                print("⌛ 静默超时，退出对话...")
                stream.stop_stream()
                stream.close()
                p.terminate()
                return None
        else:
            silence_start = None

    print("✅ 采集结束，正在处理...")
    stream.stop_stream()
    stream.close()
    p.terminate()
    return bytes().join(frames)

def play_audio_from_base64(audio_base64: str) -> None:
    """从Base64解码并播放音频"""
    try:
        print("🔊 播放回复...")
        audio_bytes = base64.b64decode(audio_base64)
        if len(audio_bytes) < 100:
            print("⚠️  音频数据异常")
            return

        # 初始化临时播放器（避免依赖全局资源）
        p = pyaudio.PyAudio()
        stream = p.open(
            format=AUDIO["format"],
            channels=AUDIO["channels"],
            rate=AUDIO["rate"],
            output=True,
            frames_per_buffer=AUDIO["chunk"]
        )

        # 分块播放
        for i in range(0, len(audio_bytes), AUDIO["chunk"]):
            stream.write(audio_bytes[i:i+AUDIO["chunk"]])

        stream.stop_stream()
        stream.close()
        p.terminate()
        print("🔊 播放完成！\n" + "-"*50)
    except Exception as e:
        print(f"❌ 播放失败：{str(e)}")