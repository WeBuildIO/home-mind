"""核心业务逻辑：唤醒引擎初始化 + 连续对话流程"""
import requests
import json
import snowboydecoder
from config import SERVER, SNOWBOY
from utils import (
    format_timestamp,
    record_audio_with_silence_detect,
    play_audio_from_base64
)

# ---------------------- 全局对话状态（仅业务相关） ----------------------
in_conversation = False
current_conv_id = None
ENTRY_PROMPT = "\n📢 已进入连续对话（静默5秒自动退出）..."

# ---------------------- 连续对话核心流程 ----------------------
def run_continuous_conversation():
    """一次唤醒后的连续对话循环"""
    global in_conversation, current_conv_id
    in_conversation = True
    current_conv_id = None  # 重置会话ID
    print(ENTRY_PROMPT)

    while in_conversation:
        # 1. 调用工具函数采集音频（无静默则退出）
        audio_data = record_audio_with_silence_detect()
        if not audio_data:
            in_conversation = False
            break

        # 2. 上传音频到服务端
        try:
            # 构造请求参数（携带会话ID）
            params = {"conversationId": current_conv_id} if current_conv_id else {}
            response = requests.post(
                SERVER["url"],
                data=audio_data,
                params=params,
                headers={"Content-Type": "audio/pcm;rate=16000"},
                timeout=SERVER["timeout"]
            )
            response.raise_for_status()
            res = response.json()

            # 更新会话ID（维持上下文）
            current_conv_id = res.get("conversationId", current_conv_id)

            # 打印对话结果
            print("\n" + "="*60)
            print(f"⏰ 时间：{format_timestamp(res.get('timestamp', 0))}")
            print(f"📝 你说：{res.get('recognizedText', '未识别')}")
            print(f"💬 回复：{res.get('chatReply', '无回复')}")
            print("="*60)

            # 3. 播放回复（调用工具函数）
            if res.get("audioBase64"):
                play_audio_from_base64(res["audioBase64"])

        except Exception as e:
            print(f"\n❌ 对话异常：{str(e)}")
            print("="*60 + "\n")
            in_conversation = False
            break

    # 重置状态，回到等待唤醒
    in_conversation = False
    current_conv_id = None
    print("\n🔚 对话结束，等待下次唤醒...\n" + "="*60)

# ---------------------- 唤醒引擎初始化 ----------------------
def init_wakeup_engine():
    """初始化Snowboy唤醒引擎，返回检测器"""
    def wakeup_callback():
        """唤醒词触发后的回调"""
        print("\n" + "="*60)
        print("✅ 检测到唤醒词！")
        print("="*60)
        run_continuous_conversation()

    try:
        detector = snowboydecoder.HotwordDetector(
            model_str=SNOWBOY["model_path"],
            sensitivity=SNOWBOY["sensitivity"],
            apply_frontend=SNOWBOY["apply_frontend"]
        )
        detector.wakeup_callback = wakeup_callback

        # 启动提示
        print("="*60)
        print("🎉 语音对话工具已启动")
        print(f"📌 服务端：{SERVER['url']} | 唤醒词模型：{SNOWBOY['model_path']}")
        print("⌛ 等待唤醒词...（按Ctrl+C退出）")
        print("="*60)
        return detector
    except Exception as e:
        print(f"❌ 唤醒引擎初始化失败：{str(e)}")
        exit(1)

# ---------------------- 资源释放 ----------------------
def release_resources():
    """释放业务相关资源（无额外依赖）"""
    print("\n📤 释放资源...")
    print("✅ 资源释放完成！")