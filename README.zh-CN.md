## SonicConductor（客户端）

[English](./README.md) | [简体中文](./README.zh-CN.md)

SonicConductor Client 是 SonicConductor 平台的设备端组件，面向音频实验场景设计。  
它用于将手机/音频设备接入统一控制中心，以支持多设备远程、协同的录音与播放任务。

通过集中编排与统一控制，SonicConductor 可将原本依赖人工操作的多设备声学实验流程标准化为可复现的执行流程，从而降低操作成本，减少人为误差，并提升实验结果的一致性与可靠性。

服务端项目见：[SonicConductor Hub](https://github.com/lannooo/SonicConductor-Hub)

## 环境要求
- Android Studio
- Android 设备（API Level 26+）
- Kotlin >= 1.5.1
- Gradle（通常随 Android Studio 提供）

## 使用方法
1. 使用 Android Studio 构建并打包应用
2. 在移动设备上安装 APK
3. 启动应用，填写服务端 IP 与端口并建立连接
4. 向服务端发送注册消息，完成设备注册
5. 在日志视图中查看通信日志

### UI
<img src="./resource/ui.jpg" width = "300" height = "600" align=center />
