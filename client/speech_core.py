import math
import pyaudio
import requests
import json
import base64
import os
import sys
from datetime import datetime
from config import AUDIO_CONFIG, SILENCE_CONFIG, SERVER_CONFIG, WAKEUP_CONFIG, GLOBAL_STATE

# ========================= 工具函数（解耦复用）=========================
def calculate_min_audio_size():
    """计算最小有效音频字节数（基于音频配置动态计算）"""
    return int(
        AUDIO_CONFIG["rate"] * 16 / 8 * AUDIO_CONFIG["channels"] * SILENCE_CONFIG["min_speech_duration"]
    )

def is_audio_silent(audio_chunk):
    """判断音频块是否为静音（RMS算法）"""
    if not audio_chunk:
        return True
    rms = (sum(byte**2 for byte in audio_chunk) / len(audio_chunk)) ** 0.5
    return rms < SILENCE_CONFIG["threshold"]

# ========================= 音频播放相关（独立模块）=========================
def init_audio_player():
    """初始化音频播放器（绑定到全局状态）"""
    try:
        player = pyaudio.PyAudio()
        stream = player.open(
            format=AUDIO_CONFIG["format"],
            channels=AUDIO_CONFIG["channels"],
            rate=AUDIO_CONFIG["rate"],
            output=True,
            frames_per_buffer=AUDIO_CONFIG["chunk"],
            start=False
        )
        GLOBAL_STATE["audio_player"] = player
        GLOBAL_STATE["play_stream"] = stream
        return True
    except Exception as e:
        print(f"音频播放器初始化失败：{str(e)}")
        return False

def play_wakeup_beep():
    """简化版唤醒蜂鸣（柔和中音，仅2段）"""
    stream = GLOBAL_STATE["play_stream"]
    if not stream:
        return

    # 柔和蜂鸣参数：低频率+稍长时长，避免尖锐
    freq1, dur1 = 440, 0.2  # 第一段：中音（440Hz，0.2秒）
    freq2, dur2 = 330, 0.3  # 第二段：低音（330Hz，0.3秒）
    sample_rate = AUDIO_CONFIG["rate"]

    try:
        stream.start_stream()
        # 生成并播放两段蜂鸣
        for freq, dur in [(freq1, dur1), (freq2, dur2)]:
            # 生成正弦波音频（直接转字节，跳过列表拼接）
            samples = (32767 * math.sin(2 * math.pi * freq * i / sample_rate) for i in range(int(sample_rate * dur)))
            audio_bytes = b''.join(int(s).to_bytes(2, 'little', signed=True) for s in samples)
            # 直接播放（复用配置的chunk_size，无需补0）
            for i in range(0, len(audio_bytes), AUDIO_CONFIG["chunk"]):
                stream.write(audio_bytes[i:i+AUDIO_CONFIG["chunk"]])
        stream.stop_stream()
    except Exception as e:
        print(f"蜂鸣播放失败：{str(e)}")
        if stream.is_active():
            stream.stop_stream()

def play_response_audio(audio_base64):
    """播放服务端回复音频（使用全局状态中的音频流）"""
    stream = GLOBAL_STATE["play_stream"]
    if not stream:
        return

    try:
        audio_bytes = base64.b64decode(audio_base64)
        if len(audio_bytes) < 1000:
            return

        if not stream.is_active():
            stream.start_stream()

        chunk_size = AUDIO_CONFIG["chunk"]
        for i in range(0, len(audio_bytes), chunk_size):
            chunk = audio_bytes[i:i+chunk_size]
            if len(chunk) < chunk_size:
                chunk += b'\x00' * (chunk_size - len(chunk))
            stream.write(chunk)

        stream.stop_stream()
    except Exception as e:
        if stream.is_active():
            stream.stop_stream()

# ========================= 录音相关（独立模块）=========================
def record_audio_with_detection():
    """录音并检测有效语音（使用全局状态中的麦克风配置）"""
    min_audio_size = calculate_min_audio_size()

    try:
        p = pyaudio.PyAudio()
        stream = p.open(
            format=AUDIO_CONFIG["format"],
            channels=AUDIO_CONFIG["channels"],
            rate=AUDIO_CONFIG["rate"],
            input=True,
            input_device_index=GLOBAL_STATE["bluetooth_mic_index"] if GLOBAL_STATE["bluetooth_mic_index"] != -1 else None,
            frames_per_buffer=AUDIO_CONFIG["chunk"]
        )
    except Exception as e:
        print(f"录音设备初始化失败：{str(e)}")
        return None

    print("\n请说话（6秒内无有效语音自动退出）...")
    frames = []
    continuous_speech_count = 0
    speech_detected = False
    silence_start_time = None

    total_chunks = int(AUDIO_CONFIG["rate"] / AUDIO_CONFIG["chunk"] * AUDIO_CONFIG["record_seconds"])
    for _ in range(total_chunks):
        try:
            data = stream.read(AUDIO_CONFIG["chunk"])
        except Exception as e:
            stream.stop_stream()
            stream.close()
            p.terminate()
            return None

        frames.append(data)
        current_max_volume = max(abs(byte) for byte in data)
        is_speech_chunk = not is_audio_silent(data) and current_max_volume > SILENCE_CONFIG["max_background_noise"]

        if is_speech_chunk:
            continuous_speech_count += 1
            silence_start_time = None
            if continuous_speech_count >= SILENCE_CONFIG["continuous_chunks"]:
                speech_detected = True
        else:
            continuous_speech_count = 0
            if not silence_start_time:
                silence_start_time = datetime.now()
            else:
                silence_duration = (datetime.now() - silence_start_time).total_seconds()
                if silence_duration >= AUDIO_CONFIG["timeout_seconds"]:
                    print("连续6秒无有效语音，退出对话...")
                    stream.stop_stream()
                    stream.close()
                    p.terminate()
                    return None

    stream.stop_stream()
    stream.close()
    p.terminate()
    audio_bytes = bytes().join(frames)

    if not speech_detected:
        print("未检测到有效语音，退出对话...")
        return None
    if len(audio_bytes) < min_audio_size:
        print("音频过短，退出对话...")
        return None

    return audio_bytes

# ========================= 服务端交互（独立模块）=========================
def send_audio_to_server(audio_bytes):
    """发送音频到服务端并处理响应（更新全局会话ID）"""
    if not audio_bytes:
        return True

    params = {}
    if GLOBAL_STATE["conversation_id"]:
        params["conversationId"] = GLOBAL_STATE["conversation_id"]

    try:
        response = requests.post(
            SERVER_CONFIG["url"],
            data=audio_bytes,
            params=params,
            headers={"Content-Type": "audio/pcm;rate=16000"},
            timeout=SERVER_CONFIG["timeout"]
        )
        response.raise_for_status()
        result = response.json()
    except Exception as e:
        print(f"服务端交互异常：{str(e)}")
        return True

    # 更新全局会话ID（用于连续对话上下文）
    if result.get("conversationId"):
        GLOBAL_STATE["conversation_id"] = result["conversationId"]

    # 处理服务端错误
    if result.get("error"):
        print(f"服务端错误：{result['error']}")
        return True

    chat_reply = result.get("chatReply", "").strip()
    if not chat_reply:
        print("未获取到有效回复")
        return True

    # 显示核心对话内容
    print("\n" + "="*50)
    print(f"你说的是：{result.get('recognizedText', '无')}")
    print(f"回复：{chat_reply}")
    print("="*50 + "\n")

    # 播放回复音频
    if result.get("audioBase64"):
        play_response_audio(result["audioBase64"])

    return False

# ========================= 唤醒相关（独立模块）=========================
def get_ppn_file_path():
    """获取唤醒词模型文件路径（基于程序运行目录）"""
    current_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(current_dir, WAKEUP_CONFIG["ppn_file_name"])

def init_wakeup_engine():
    """初始化唤醒引擎（绑定到全局状态）"""
    try:
        import pvporcupine
        import pvrecorder
    except ImportError:
        print("唤醒模块导入失败：请安装 pvporcupine 和 pvrecorder")
        return False

    ppn_path = get_ppn_file_path()
    if not os.path.exists(ppn_path):
        print(f"唤醒词模型文件不存在：{ppn_path}")
        return False

    try:
        porcupine = pvporcupine.create(
            access_key=WAKEUP_CONFIG["access_key"],
            keyword_paths=[ppn_path],
            sensitivities=[WAKEUP_CONFIG["sensitivity"]]
        )
        recorder = pvrecorder.PvRecorder(
            device_index=GLOBAL_STATE["bluetooth_mic_index"],
            frame_length=porcupine.frame_length
        )
        recorder.start()

        # 初始化唤醒相关全局状态
        GLOBAL_STATE["porcupine"] = porcupine
        GLOBAL_STATE["wakeup_recorder"] = recorder
        print("唤醒功能初始化成功，监听唤醒词：Hi-Siri（英文）")
        return True
    except Exception as e:
        error_msg = str(e).lower()
        if "invalid access key" in error_msg:
            print("唤醒Access Key无效")
        elif "device" in error_msg:
            print("未检测到麦克风或被占用")
        elif "quota" in error_msg:
            print("唤醒服务免费额度已用完")
        else:
            print(f"唤醒引擎初始化失败：{str(e)}")
        return False

def listen_wakeup_word():
    """监听唤醒词（使用全局状态中的唤醒引擎实例）"""
    porcupine = GLOBAL_STATE["porcupine"]
    recorder = GLOBAL_STATE["wakeup_recorder"]
    if not porcupine or not recorder:
        return

    print("\n唤醒监听已启动，请说 'Hi-Siri' 触发对话（按 Ctrl+C 停止）")

    try:
        while True:
            pcm = recorder.read()
            if porcupine.process(pcm) != -1:
                print("\n检测到唤醒词，进入对话模式...")
                play_wakeup_beep()
                start_continuous_chat()
                print("\n继续监听唤醒词：Hi-Siri（按 Ctrl+C 停止）")
    except KeyboardInterrupt:
        print("\n用户主动停止监听")
    except Exception as e:
        print(f"\n唤醒监听异常：{str(e)}")

# ========================= 连续对话核心（业务逻辑）=========================
def start_continuous_chat():
    """连续对话业务逻辑（依赖全局状态和各独立模块）"""
    while True:
        audio_data = record_audio_with_detection()
        if not audio_data:
            print("对话已退出")
            break

        need_exit = send_audio_to_server(audio_data)
        if need_exit:
            print("对话已退出")
            break

# ========================= 资源释放（统一管理）=========================
def release_all_resources():
    """释放所有全局状态中的资源（避免泄露）"""
    # 释放音频播放资源
    if GLOBAL_STATE["play_stream"]:
        GLOBAL_STATE["play_stream"].stop_stream()
        GLOBAL_STATE["play_stream"].close()
    if GLOBAL_STATE["audio_player"]:
        GLOBAL_STATE["audio_player"].terminate()

    # 释放唤醒资源
    if GLOBAL_STATE["wakeup_recorder"] and GLOBAL_STATE["wakeup_recorder"].is_recording:
        GLOBAL_STATE["wakeup_recorder"].stop()
    if GLOBAL_STATE["porcupine"]:
        GLOBAL_STATE["porcupine"].delete()