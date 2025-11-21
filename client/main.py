from config import SERVER_CONFIG
from speech_core import (
    init_audio_player,
    init_wakeup_engine,
    listen_wakeup_word,
    release_all_resources
)

def main():
    """程序入口：仅负责初始化、启动和资源释放，不包含业务逻辑"""
    print(f"服务端地址：{SERVER_CONFIG['url']}")
    print("规则：一次唤醒直接录音，6秒无有效语音自动退出")
    print("-"*50)

    # 1. 初始化音频播放器
    if not init_audio_player():
        print("程序退出")
        return

    # 2. 初始化唤醒引擎
    if not init_wakeup_engine():
        release_all_resources()
        print("程序退出")
        return

    # 3. 启动唤醒监听（核心业务入口）
    try:
        listen_wakeup_word()
    finally:
        # 4. 程序退出时释放所有资源
        release_all_resources()
        print("\n资源释放完成，程序退出")

if __name__ == "__main__":
    main()