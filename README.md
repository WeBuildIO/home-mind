# 硬件
树莓派 3B+/4B/5（Raspbian 11+）

无驱 USB 麦克风

3.5mm 接口音响 / 耳机

电脑 / 服务器（运行服务端）

同一局域网环境

# 更新系统
```bash
sudo apt-get update
```
# 安装依赖
```bash
cd client
pip install -r requirements.txt
```
服务端安装 JDK 17（验证：java -version）

百度 AI 开放平台创建应用，开通「语音识别」「语音合成」服务，记录 API Key 和 Secret Key

# 麦克风+音响测试：录制10秒音频并播放
```bash
arecord -d 10 -r 16000 -c 1 -f S16_LE test.wav && aplay test.wav
```
# 服务端
1. 服务端配置（home-mind）
拉取代码，修改 src/main/resources/application.yml
```yaml
spring:
  application:
    name: home-mind
  ai:
    deepseek:
      api-key: 你的deepseekAPIKey  # 替换为自己的
baidu:
  speech:
    api-key: 你的百度APIKey  # 替换为自己的
    secret-key: 你的百度SecretKey  # 替换为自己的
```
启动服务，日志显示 Tomcat started on port 8080 即成功


# 树莓派启动客户端：
1. 树莓派客户端配置
将项目根目录下的`speech_client.py`放到树莓派，替换文件内`SERVICE_URL = "http://服务端IP:8080/api/speech/recognize-chat"`
2. 运行
```bash
python3 client.py
```
按 Enter 开始录音（5 秒内说话），支持：
聊天："你好呀"
天气查询："查询北京天气"
系统自动识别→AI 回复→语音播报，输入 quit 退出。
