"""程序入口：启动唤醒引擎 + 优雅退出"""
import signal
from core import init_wakeup_engine, release_resources

# 捕获Ctrl+C，优雅退出
def signal_handler(signal, frame):
    print("\n🛑 正在退出程序...")
    detector.terminate()  # 释放Snowboy资源
    release_resources()   # 释放核心业务资源
    print("👋 程序已安全退出！")
    exit(0)

# 绑定信号
signal.signal(signal.SIGINT, signal_handler)

# 启动核心流程
if __name__ == "__main__":
    detector = init_wakeup_engine()
    # 启动唤醒检测（阻塞式）
    detector.start(
        detected_callback=detector.wakeup_callback,
        sleep_time=0.02  # 降低CPU负载
    )