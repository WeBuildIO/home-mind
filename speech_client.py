import pyaudio
import requests
import json
import base64
from datetime import datetime

# -------------------------- 配置参数 --------------------------
FORMAT = pyaudio.paInt16    # 与服务端一致（PCM_16k）
CHANNELS = 1                # 单声道
RATE = 16000                # 采样率（与服务端一致）
CHUNK = 1024                # 播放缓冲区大小
RECORD_SECONDS = 5          # 录音时长（5秒）
SPRING_BOOT_URL = "http://192.168.2.102:8080/api/speech/recognize-chat"
conversation_id = None      # 保存会话ID，实现连续对话

# 初始化音频播放器（全局唯一，避免重复创建）
player = pyaudio.PyAudio()
play_stream = player.open(
    format=FORMAT,
    channels=CHANNELS,
    rate=RATE,
    output=True,  # 输出模式（播放语音）
    frames_per_buffer=CHUNK
)

# -------------------------- 工具函数 --------------------------
def record_audio():
    """采集PCM音频（与服务端参数一致）"""
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

    print("✅ 采集结束，正在识别和生成回复...")
    stream.stop_stream()
    stream.close()
    p.terminate()
    return bytes().join(frames)

def format_timestamp(timestamp):
    """将时间戳转为可读格式（如：2025-11-18 15:30:45）"""
    return datetime.fromtimestamp(timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")

def play_audio(audio_base64):
    try:
        print("🔊 正在播放小派的回复...")
        print(f"📊 音频Base64长度：{len(audio_base64)} 字符")  # 新增：打印Base64长度
        audio_bytes = base64.b64decode(audio_base64)
        print(f"📊 解码后音频大小：{len(audio_bytes)} 字节")  # 新增：打印音频字节数

        if len(audio_bytes) < 100:  # 音频太小（正常至少几千字节）
            print("⚠️  音频数据异常：解码后字节数过少，可能是空音频")
            return

        # 分块播放
        for i in range(0, len(audio_bytes), CHUNK):
            chunk = audio_bytes[i:i+CHUNK]
            play_stream.write(chunk)
        print("🔊 播放完成！")
    except Exception as e:
        print(f"❌ 语音播放失败：{str(e)}")
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
            timeout=60  # 超时时间设为60秒（适配AI回复+语音合成耗时）
        )
        response.raise_for_status()  # 抛出HTTP错误（如400/500）
        result = response.json()

        # 保存会话ID（用于下一轮连续对话）
        if result.get("conversationId"):
            conversation_id = result["conversationId"]

        # 打印格式化结果（严格对应服务端ChatResponse字段）
        print("\n" + "="*60)
        print(f"⏰ 时间：{format_timestamp(result.get('timestamp', 0))}")
        if result.get("error"):
            print(f"❌ 错误信息：{result['error']}")
        else:
            # 字段容错：避免服务端未返回时报错
            recognized_text = result.get("recognizedText", "无")
            chat_reply = result.get("chatReply", "无")
            conv_id = result.get("conversationId", "无")

            print(f"📝 你说的是：{recognized_text}")
            print(f"💬 小派回复：{chat_reply}")
            print(f"🆔 会话ID：{conv_id}")

            # 播放语音（如果服务端返回audioBase64）
            if result.get("audioBase64"):
                play_audio(result["audioBase64"])
            else:
                print("⚠️  未获取到语音数据，仅显示文本回复")
        print("="*60 + "\n")

    except requests.exceptions.Timeout:
        print(f"\n❌ 请求超时：服务端处理时间超过60秒")
    except requests.exceptions.ConnectionError:
        print(f"\n❌ 连接失败：无法访问服务端 {SPRING_BOOT_URL}")
    except requests.exceptions.HTTPError as e:
        print(f"\n❌ HTTP错误：{e}，服务端响应：{response.text}")
    except json.JSONDecodeError:
        print(f"\n❌ 响应格式错误：服务端返回非JSON数据：{response.text}")
    except Exception as e:
        print(f"\n❌ 未知错误：{str(e)}")

# -------------------------- 主函数 --------------------------
def main():
    try:
        print("🎉 语音连续对话工具（小派）- 输入 'quit' 退出")
        print(f"📌 服务端地址：{SPRING_BOOT_URL}")
        print(f"📌 录音时长：{RECORD_SECONDS}秒\n")

        while True:
            user_input = input("Press Enter 开始采集（或输入 'quit' 退出）...").strip()
            if user_input.lower() == "quit":
                print("👋 退出程序...")
                break
            audio_data = record_audio()
            send_audio_and_chat(audio_data)
    finally:
        # 程序退出时释放音频资源（避免占用）
        print("\n📤 释放资源中...")
        play_stream.stop_stream()
        play_stream.close()
        player.terminate()
        print("✅ 资源释放完成！")

if __name__ == "__main__":
    main()