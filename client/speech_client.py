import pyaudio
import requests
import json
import base64
import os
import sys
from datetime import datetime

# 核心配置（关键参数，易调整）
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 16000
CHUNK = 1024
RECORD_SECONDS = 6  # 录音时长
CONTINUOUS_TIMEOUT = 6  # 无声音超时退出时间
SPRING_BOOT_URL = "http://localhost:8080/api/speech/recognize-chat"
conversation_id = None  # 全局会话ID

# 静音检测配置（防误判核心）
SILENCE_THRESHOLD = 2  # 静音阈值（越小越严格）
MIN_SPEECH_DURATION = 0.8  # 最小有效语音时长（秒）
MIN_AUDIO_SIZE = int(RATE * 16 / 8 * CHANNELS * MIN_SPEECH_DURATION)  # 最小音频字节数
MAX_BACKGROUND_NOISE = 12  # 背景噪音上限
CONTINUOUS_SPEECH_CHUNKS = 4  # 连续非静音块阈值

# 唤醒配置（一次触发）
ACCESS_KEY = "xxx"  # 替换为你的Key
PPN_FILE_NAME = "Hi-Siri_en_windows_v3_0_0.ppn"
SENSITIVITY = 0.85  # 唤醒灵敏度
WAKEUP_FEEDBACK = True  # 唤醒蜂鸣提示
WAKEUP_LOG_FILE = "wakeup_log.txt"
PPN_FILE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), PPN_FILE_NAME)

# 初始化音频资源
player = pyaudio.PyAudio()
play_stream = player.open(
    format=FORMAT,
    channels=CHANNELS,
    rate=RATE,
    output=True,
    frames_per_buffer=CHUNK,
    start=False
)

porcupine = None
wakeup_recorder = None
bluetooth_mic_index = -1  # 麦克风索引缓存

def is_silent(audio_chunk):
    """判断音频块是否为静音（RMS算法）"""
    rms = (sum(byte**2 for byte in audio_chunk) / len(audio_chunk)) ** 0.5
    return rms < SILENCE_THRESHOLD

def record_and_check_speech():
    """录音并检测是否有有效说话"""
    p = pyaudio.PyAudio()
    stream = p.open(
        format=FORMAT,
        channels=CHANNELS,
        rate=RATE,
        input=True,
        input_device_index=bluetooth_mic_index if bluetooth_mic_index != -1 else None,
        frames_per_buffer=CHUNK
    )

    print("\n请说话（6秒内无有效语音自动退出）...")
    frames = []
    continuous_speech_count = 0
    speech_detected = False
    silence_start_time = None

    # 录音循环
    for _ in range(int(RATE / CHUNK * RECORD_SECONDS)):
        data = stream.read(CHUNK)
        frames.append(data)

        # 检测当前块是否为有效语音
        current_max_volume = max(abs(byte) for byte in data)
        is_speech_chunk = not is_silent(data) and current_max_volume > MAX_BACKGROUND_NOISE

        if is_speech_chunk:
            continuous_speech_count += 1
            silence_start_time = None
            if continuous_speech_count >= CONTINUOUS_SPEECH_CHUNKS:
                speech_detected = True
        else:
            continuous_speech_count = 0
            # 静音超时判断
            if not silence_start_time:
                silence_start_time = datetime.now()
            else:
                silence_duration = (datetime.now() - silence_start_time).total_seconds()
                if silence_duration >= CONTINUOUS_TIMEOUT:
                    print("连续6秒无有效语音，退出连续对话...")
                    stream.stop_stream()
                    stream.close()
                    p.terminate()
                    return None

    # 释放资源
    stream.stop_stream()
    stream.close()
    p.terminate()
    audio_bytes = bytes().join(frames)

    # 有效语音验证
    if not speech_detected:
        print("未检测到有效语音，退出连续对话...")
        return None
    if len(audio_bytes) < MIN_AUDIO_SIZE:
        print(f"音频过短（{len(audio_bytes)}字节 < 最小{MIN_AUDIO_SIZE}字节），退出连续对话...")
        return None

    print("检测到有效语音，正在识别回复...")
    return audio_bytes

def play_audio(audio_base64):
    """播放回复音频"""
    try:
        print("正在播放回复...")
        audio_bytes = base64.b64decode(audio_base64)
        if len(audio_bytes) < 1000:
            print("音频数据异常：字节数过少")
            return

        if not play_stream.is_active():
            play_stream.start_stream()

        for i in range(0, len(audio_bytes), CHUNK):
            chunk = audio_bytes[i:i+CHUNK]
            if len(chunk) < CHUNK:
                chunk += b'\x00' * (CHUNK - len(chunk))
            play_stream.write(chunk)

        play_stream.stop_stream()
        print("回复播放完成")
    except Exception as e:
        print(f"音频播放失败：{str(e)}")
        if play_stream.is_active():
            play_stream.stop_stream()

def play_wakeup_feedback():
    """唤醒蜂鸣提示"""
    try:
        if sys.platform == "win32":
            import winsound
            winsound.Beep(1800, 500)
        else:
            os.system("beep -f 1800 -l 500")
        print("已唤醒，请立即说话...")
    except Exception as e:
        print(f"唤醒提示失败：{e}")

def write_wakeup_log(wakeup_word):
    """写入唤醒日志（简化版）"""
    try:
        log_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        with open(WAKEUP_LOG_FILE, "a", encoding="utf-8") as f:
            f.write(f"[{log_time}] - 唤醒成功 | 唤醒词：{wakeup_word} | 会话ID：{conversation_id or '无'}\n")
    except Exception as e:
        print(f"日志写入失败：{e}")

def send_audio_and_chat(audio_bytes):
    """发送音频到服务端并处理回复"""
    global conversation_id
    params = {"conversationId": conversation_id} if conversation_id else {}

    try:
        response = requests.post(
            SPRING_BOOT_URL,
            data=audio_bytes,
            params=params,
            headers={"Content-Type": "audio/pcm;rate=16000"},
            timeout=60
        )
        response.raise_for_status()
        result = response.json()

        if result.get("conversationId"):
            conversation_id = result["conversationId"]

        print("\n" + "="*50)
        print(f"时间：{datetime.fromtimestamp(result.get('timestamp', 0)/1000).strftime('%Y-%m-%d %H:%M:%S')}")

        # 服务端报错或无回复，退出
        if result.get("error"):
            print(f"服务端错误：{result['error']}")
            print("="*50 + "\n")
            return True
        chat_reply = result.get("chatReply", "").strip()
        if not chat_reply:
            print("未获取到有效回复")
            print("="*50 + "\n")
            return True

        # 正常回复处理
        recognized_text = result.get("recognizedText", "无")
        print(f"你说的是：{recognized_text}")
        print(f"回复：{chat_reply}")
        print(f"会话ID：{conversation_id or '无'}")

        if result.get("audioBase64"):
            play_audio(result["audioBase64"])
        print("="*50 + "\n")
        return False

    except Exception as e:
        print(f"\n对话异常：{str(e)}")
        return True

def continuous_chat():
    """连续对话核心逻辑"""
    print("\n已进入连续对话模式")
    print(f"会话ID：{conversation_id or '首次对话'}")

    while True:
        audio_data = record_and_check_speech()
        if not audio_data:
            print("\n连续对话已退出（会话ID已保留）")
            print("-"*50)
            break

        need_exit = send_audio_and_chat(audio_data)
        if need_exit:
            print("\n连续对话已退出（会话ID已保留）")
            print("-"*50)
            break

def init_wakeup_engine():
    """初始化唤醒引擎"""
    global porcupine, wakeup_recorder
    try:
        if not os.path.exists(PPN_FILE_PATH):
            print(f"错误：未找到唤醒词模型文件 {PPN_FILE_NAME}")
            return False

        porcupine = pvporcupine.create(
            access_key=ACCESS_KEY,
            keyword_paths=[PPN_FILE_PATH],
            sensitivities=[SENSITIVITY]
        )

        wakeup_recorder = pvrecorder.PvRecorder(
            device_index=bluetooth_mic_index,
            frame_length=porcupine.frame_length
        )
        wakeup_recorder.start()
        print("唤醒功能初始化成功")
        print(f"监听唤醒词：Hi-Siri（英文）")
        print(f"灵敏度：{SENSITIVITY} | 唤醒提示：{'开启' if WAKEUP_FEEDBACK else '关闭'}")
        return True
    except Exception as e:
        error_msg = str(e).lower()
        if "invalid access key" in error_msg:
            print("错误：唤醒Access Key无效")
        elif "device" in error_msg:
            print("错误：未检测到麦克风或麦克风被占用")
        elif "quota" in error_msg:
            print("错误：唤醒服务免费额度已用完")
        else:
            print(f"错误：唤醒引擎初始化失败：{e}")
        return False

def listen_for_wakeup():
    """监听唤醒词"""
    global porcupine, wakeup_recorder
    print("\n唤醒监听已启动，请说 'Hi-Siri' 触发对话（按 Ctrl+C 停止）")
    print("-"*50)

    try:
        while True:
            pcm = wakeup_recorder.read()
            if porcupine.process(pcm) != -1:
                wakeup_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                print(f"\n[{wakeup_time}] 检测到唤醒词：Hi-Siri")

                if WAKEUP_FEEDBACK:
                    play_wakeup_feedback()
                write_wakeup_log("Hi-Siri")
                continuous_chat()

                print("\n继续监听唤醒词：Hi-Siri（按 Ctrl+C 停止）")
                print("-"*50)
    except KeyboardInterrupt:
        print("\n用户主动停止监听")
    except Exception as e:
        print(f"\n监听异常：{e}")

def main():
    """主函数"""
    global porcupine, wakeup_recorder
    try:
        print("语音连续对话工具（简化维护版）")
        print(f"服务端地址：{SPRING_BOOT_URL}")
        print(f"核心规则：一次唤醒直接录音，{CONTINUOUS_TIMEOUT}秒无有效语音自动退出")
        print("-"*50)

        if not init_wakeup_engine():
            print("程序退出")
            return
        listen_for_wakeup()
    finally:
        # 释放资源
        print("\n释放资源中...")
        play_stream.stop_stream()
        play_stream.close()
        player.terminate()
        if wakeup_recorder and wakeup_recorder.is_recording:
            wakeup_recorder.stop()
        if porcupine:
            porcupine.delete()
        print("资源释放完成")

# 导入唤醒模块
import pvporcupine
import pvrecorder

if __name__ == "__main__":
    main()