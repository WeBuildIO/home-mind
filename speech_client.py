import pyaudio
import requests
import json
from datetime import datetime

# -------------------------- 配置参数 --------------------------
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 16000
CHUNK = 1024
RECORD_SECONDS = 5
SPRING_BOOT_URL = "http://192.168.2.102:8080/api/speech/recognize-chat"
conversation_id = None  # 保存会话ID，实现连续对话

# -------------------------- 工具函数 --------------------------
def record_audio():
    """采集PCM音频"""
    p = pyaudio.PyAudio()
    stream = p.open(
        format=FORMAT,
        channels=CHANNELS,
        rate=RATE,
        input=True,
        frames_per_buffer=CHUNK
    )

    print(f"\n🎤 开始采集语音（{RECORD_SECONDS}秒后自动上传）...")
    frames = []
    for _ in range(0, int(RATE / CHUNK * RECORD_SECONDS)):
        data = stream.read(CHUNK)
        frames.append(data)

    print("✅ 采集结束，正在识别和对话...")
    stream.stop_stream()
    stream.close()
    p.terminate()
    return bytes().join(frames)

def format_timestamp(timestamp):
    """将时间戳转为可读格式（如：2025-11-18 15:30:45）"""
    return datetime.fromtimestamp(timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")

# -------------------------- 核心逻辑 --------------------------
def send_audio_and_chat(audio_bytes):
    global conversation_id

    # 构造请求参数：携带会话ID（首次无）
    params = {}
    if conversation_id:
        params["conversationId"] = conversation_id

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

        # 保存会话ID（用于下一轮对话）
        if result.get("conversationId"):
            conversation_id = result["conversationId"]

        # 打印格式化结果（对应 ChatResponse 字段）
        print("\n" + "="*60)
        print(f"⏰ 时间：{format_timestamp(result['timestamp'])}")
        if result.get("error"):
            print(f"❌ 错误信息：{result['error']}")
        else:
            print(f"📝 识别到的语音：{result['recognizedText']}")
            print(f"💬 小派回复：{result['chatReply']}")  # 对应 chatReply 字段
            print(f"🆔 会话ID：{result['conversationId']}")
        print("="*60 + "\n")

    except requests.exceptions.RequestException as e:
        print(f"\n❌ 请求失败：{str(e)}")
    except json.JSONDecodeError:
        print(f"\n❌ 响应格式错误：{response.text}")

# -------------------------- 主函数 --------------------------
def main():
    print("🎉 语音连续对话工具（输入 'quit' 退出）")
    while True:
        user_input = input("Press Enter 开始采集（或输入 'quit' 退出）...").strip()
        if user_input.lower() == "quit":
            print("👋 退出程序...")
            break
        audio_data = record_audio()
        send_audio_and_chat(audio_data)

if __name__ == "__main__":
    main()