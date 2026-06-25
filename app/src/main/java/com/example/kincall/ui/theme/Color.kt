package com.example.kincall.ui.theme

import androidx.compose.ui.graphics.Color

// ===== KinCall 主题色 =====
// 老人友好型配色：温暖、高对比度、易辨识

/** 主色 - 温暖蓝绿色（亲切感） */
val KinCallPrimary = Color(0xFF2E7D6F)
val KinCallPrimaryLight = Color(0xFF5AA89A)
val KinCallPrimaryDark = Color(0xFF1B5E50)

/** 辅助色 - 暖橙色（温馨感，用于强调） */
val KinCallSecondary = Color(0xFFE8A44A)
val KinCallSecondaryLight = Color(0xFFF0C078)
val KinCallSecondaryDark = Color(0xFFD08830)

/** 背景色 - 米白色（柔和不刺眼） */
val KinCallBackground = Color(0xFFF8F5F0)
val KinCallSurface = Color(0xFFFFFFFF)

/** 错误色 - 柔和红色（删除、警告） */
val KinCallError = Color(0xFFD32F2F)
val KinCallErrorLight = Color(0xFFEF5350)

/** 文字色 - 深灰（比纯黑柔和，老人阅读舒适） */
val KinCallOnSurface = Color(0xFF2C2C2C)
val KinCallOnSurfaceVariant = Color(0xFF666666)

/** 成功色 - 绿色（保存成功等提示） */
val KinCallSuccess = Color(0xFF4CAF50)

// ===== 语音拨号界面专用色 =====

/** 语音拨号背景 - 暖米色（与主页一致，老人白天使用不刺眼） */
val KinCallVoiceBackground = Color(0xFFF8F5F0)

/** 录音中 - 温暖红色（比纯红柔和，老人不紧张） */
val KinCallListening = Color(0xFFE06060)

/** 录音脉冲环 - 半透明暖红 */
val KinCallListeningPulse = Color(0x33E06060)

/** 处理中 - 暖橙 */
val KinCallProcessing = Color(0xFFE8A44A)

/** 确认中 - 温暖绿 */
val KinCallConfirming = Color(0xFF2E7D6F)

/** 拨号中 - 柔和蓝 */
val KinCallDialing = Color(0xFF5AA89A)

/** 麦克风按钮 - 主色绿 */
val KinCallMicIdle = Color(0xFF2E7D6F)

/** 麦克风按钮 - 录音中红 */
val KinCallMicActive = Color(0xFFE06060)

/** 对话气泡 - 用户 */
val KinCallBubbleUser = Color(0xFF2E7D6F)

/** 对话气泡 - 助手 */
val KinCallBubbleAssistant = Color(0xFFEDE8E2)

/** 对话气泡文字 - 助手 */
val KinCallBubbleAssistantText = Color(0xFF2C2C2C)

/** 对话气泡 - 系统 */
val KinCallBubbleSystem = Color(0x00000000)

// ===== 通话界面专用色 =====

/** 通话中背景 - 温暖渐变起始色 */
val KinCallOnCallBackground = Color(0xFFF0EBE4)

/** 挂断按钮红 - 柔和，不刺眼 */
val KinCallHangUp = Color(0xFFD32F2F)
