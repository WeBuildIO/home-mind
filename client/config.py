import pyaudio
# ========================= 音频基础配置 =========================
# 说明：音频采集/播放的核心参数，根据硬件和服务端要求调整
AUDIO_CONFIG = {
    "format": pyaudio.paInt16,
    "channels": 1,
    "rate": 16000,
    "chunk": 512,  # 关键修改：从1024→512，检测周期从0.064s→0.032s，减少延迟
    "record_seconds": 5,
    "timeout_seconds": 6,
    "post_speech_silence": 1.0          # 1秒静音上传（不变）
}
# ========================= 静音检测配置（移除连续块计数）=========================
SILENCE_CONFIG = {
    "threshold": 2,
    "max_background_noise": 12,
    # 关键修改：删除continuous_chunks，检测到有效语音立即标记
    "min_speech_duration": 0.8
}
# ========================= 服务端配置 =========================
# 说明：服务端接口相关配置，需与后端接口保持一致
SERVER_CONFIG = {
    "url": "http://localhost:8080/api/speech/recognize-chat",  # 语音交互接口地址
    "timeout": 60                                               # 接口请求超时时间（秒）
}
# ========================= 唤醒配置 =========================
# 说明：Porcupine唤醒引擎配置，需替换为自己的Access Key和模型文件
WAKEUP_CONFIG = {
    "access_key": "xxx",                # 唤醒服务Access Key（从Porcupine官网申请）
    "ppn_file_name": "keyword_files/hey_siri_mac.ppn",  # 唤醒词模型文件名（需放在程序同级目录）
    "sensitivity": 0.85                 # 唤醒灵敏度（0-1，越高越容易触发，建议0.8-0.9）
}
# ========================= 全局状态（带详细注释） =========================
# 说明：存储程序运行时的全局变量，跨模块共享状态，避免全局变量散落
GLOBAL_STATE = {
    "conversation_id": None,            # 会话ID（用于连续对话上下文关联，首次对话为None）
    "bluetooth_mic_index": -1,          # 蓝牙麦克风设备索引（-1表示自动选择系统默认麦克风）
    "porcupine": None,                  # Porcupine唤醒引擎实例（初始化后赋值，退出时释放）
    "wakeup_recorder": None,            # 唤醒词录音器实例（用于持续监听唤醒词，初始化后启动）
    "audio_player": None,               # 音频播放器实例（用于播放服务端回复语音）
    "play_stream": None                 # 音频播放流（绑定播放器实例，用于分块播放音频数据）
}