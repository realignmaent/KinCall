package com.example.kincall.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource  // may be unused
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.kincall.KinCallApp
import com.example.kincall.data.entity.Contact
import com.example.kincall.llm.ConversationResult
import com.example.kincall.ui.theme.KinCallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kincall.ui.theme.*

/**
 * 语音拨号主界面 Activity
 *
 * 核心交互流程：
 * 1. 用户点击麦克风按钮 → 开始录音
 * 2. ASR 识别语音 → ConversationManager 处理
 * 3. LLM 分析意图 + IntentMatcher 匹配联系人
 * 4. TTS 确认 → 用户确认 → 拨打电话
 *
 * 状态机：
 * Idle → Listening → Processing → Confirming → Dialing
 *                ↓           ↓           ↓
 *              Error       Error       Idle
 */
class VoiceCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VoiceCallActivity"
    }

    /** 获取 Application 级别的依赖 */
    private val app by lazy { application as KinCallApp }

    /** 对话状态 */
    private var callState: CallState by mutableStateOf(CallState.Idle)

    /** 当前识别的文本 */
    private var recognizedText by mutableStateOf("")

    /** 匹配到的联系人 */
    private var matchedContact by mutableStateOf<Contact?>(null)

    /** 状态消息（用于显示错误或提示） */
    private var statusMessage by mutableStateOf("")

    /** 对话历史记录 */
    private val conversationHistory = mutableStateListOf<UiChatMessage>()

    /** 电话权限请求 */
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予，继续拨号
            matchedContact?.let { makeCall(it.phone) }
        } else {
            statusMessage = "需要电话权限才能拨号"
            callState = CallState.CallError("需要电话权限，请在设置中授权")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 在锁屏上显示 + 保持屏幕常亮
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContent {
            KinCallTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceCallScreen(
                        modifier = Modifier.padding(innerPadding),
                        callState = callState,
                        recognizedText = recognizedText,
                        matchedContact = matchedContact,
                        statusMessage = statusMessage,
                        conversationHistory = conversationHistory,
                        onMicClick = { onMicButtonClicked() },
                        onConfirmCall = { onConfirmCall() },
                        onCancel = { onCancel() }
                    )
                }
            }
        }

        // 启动对话并播报问候语
        lifecycleScope.launch {
            val greeting = withContext(Dispatchers.IO) {
                app.conversationManager.startConversation()
            }
            addMessage(UiChatMessage.Role.ASSISTANT, greeting)
            statusMessage = greeting
            withContext(Dispatchers.IO) {
                app.speaker.speak(greeting)
            }
        }
    }

    /**
     * 麦克风按钮点击处理
     */
    private fun onMicButtonClicked() {
        when (callState) {
            CallState.Idle, is CallState.CallError -> {
                startListening()
            }
            CallState.Listening -> {
                // 用户再次点击 = 手动停止录音
                stopListening()
            }
            CallState.Processing, CallState.Confirming, CallState.Dialing -> {
                // 忽略，正在处理中
            }
        }
    }

    /**
     * 开始语音识别流程
     */
    private fun startListening() {
        callState = CallState.Listening
        recognizedText = ""
        matchedContact = null
        statusMessage = "正在听您说..."
        addMessage(UiChatMessage.Role.SYSTEM, "正在听您说...")

        lifecycleScope.launch {
            try {
                // 调用 ASR 识别
                val result = withContext(Dispatchers.IO) {
                    app.asrClient.recognize()
                }

                if (!result.isSuccess) {
                    val error = result.errorMessage ?: "识别失败"
                    Log.e(TAG, "ASR failed: $error")
                    callState = CallState.CallError(error)
                    statusMessage = error
                    addMessage(UiChatMessage.Role.SYSTEM, "识别失败：$error")
                    // TTS 播报错误
                    withContext(Dispatchers.IO) {
                        app.speaker.speak("抱歉，我没听清，请再说一次")
                    }
                    delay(2000)
                    callState = CallState.Idle
                    return@launch
                }

                val text = result.text.trim()
                if (text.isEmpty()) {
                    callState = CallState.CallError("未识别到语音")
                    statusMessage = "未识别到语音"
                    addMessage(UiChatMessage.Role.SYSTEM, "未识别到语音")
                    withContext(Dispatchers.IO) {
                        app.speaker.speak("我没有听到声音，请再说一次")
                    }
                    delay(2000)
                    callState = CallState.Idle
                    return@launch
                }

                // 显示识别结果
                recognizedText = text
                addMessage(UiChatMessage.Role.USER, text)
                callState = CallState.Processing
                statusMessage = "正在理解..."

                // 使用 ConversationManager 处理
                processWithConversationManager(text)

            } catch (e: Exception) {
                Log.e(TAG, "Error in startListening", e)
                callState = CallState.CallError(e.message ?: "未知错误")
                statusMessage = "出错了：${e.message}"
                addMessage(UiChatMessage.Role.SYSTEM, "出错了：${e.message}")
            }
        }
    }

    /**
     * 使用 ConversationManager 处理用户语音
     */
    private fun processWithConversationManager(userText: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    app.conversationManager.processUserText(userText)
                }

                when (result) {
                    is ConversationResult.Calling -> {
                        // 确认拨号
                        matchedContact = result.contact
                        callState = CallState.Confirming
                        statusMessage = "确认拨打 ${result.contact.name}？"
                        addMessage(UiChatMessage.Role.ASSISTANT, "好的，正在拨打给${result.contact.name}")

                        // TTS 播报确认
                        withContext(Dispatchers.IO) {
                            app.speaker.speak("好的 正在拨打给${result.contact.name}")
                        }

                        // 自动确认（MVP版本，后续可改为等待用户确认）
                        delay(500)
                        confirmAndCall(result.contact)
                    }

                    is ConversationResult.NeedClarification -> {
                        // 需要用户澄清
                        statusMessage = result.llmReply
                        addMessage(UiChatMessage.Role.ASSISTANT, result.llmReply)

                        // TTS 播报澄清问题
                        withContext(Dispatchers.IO) {
                            app.speaker.speak(result.llmReply)
                        }

                        // 等待 TTS 播报完成，然后重新开始录音
                        delay(500)
                        callState = CallState.Idle
                        // 自动重新开始监听
                        delay(1000)
                        startListening()
                    }

                    is ConversationResult.Cancelled -> {
                        // 用户取消
                        statusMessage = "已取消"
                        addMessage(UiChatMessage.Role.ASSISTANT, "好的，已取消")
                        withContext(Dispatchers.IO) {
                            app.speaker.speak("好的，已取消")
                        }
                        delay(1500)
                        callState = CallState.Idle
                    }

                    is ConversationResult.Error -> {
                        callState = CallState.CallError(result.message)
                        statusMessage = result.message
                        addMessage(UiChatMessage.Role.SYSTEM, "错误：${result.message}")
                        withContext(Dispatchers.IO) {
                            app.speaker.speak("抱歉，出了点问题，请再试一次")
                        }
                        delay(2000)
                        callState = CallState.Idle
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing with ConversationManager", e)
                callState = CallState.CallError(e.message ?: "处理失败")
                statusMessage = "处理失败：${e.message}"
                addMessage(UiChatMessage.Role.SYSTEM, "处理失败：${e.message}")
            }
        }
    }

    /**
     * 确认并拨打电话
     */
    private fun confirmAndCall(contact: Contact) {
        matchedContact = contact
        callState = CallState.Dialing
        statusMessage = "正在拨打 ${contact.name}..."
        addMessage(UiChatMessage.Role.SYSTEM, "正在拨打 ${contact.name}（${contact.phone}）")

        // 检查电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            makeCall(contact.phone, contact)
        } else {
            phonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /**
     * 拨打电话（带联系人信息）
     */
    private fun makeCall(phone: String, contact: Contact? = null) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phone")
                // 请求免提
                putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
            }
            // 传递联系人信息给通话界面
            contact?.let {
                intent.putExtra("contact_name", it.name)
                intent.putExtra("contact_relation", it.relation ?: "")
                intent.putExtra("contact_phone", it.phone)
            }
            startActivity(intent)

            // 1.5秒后强制开启免提（兜底方案）
            lifecycleScope.launch {
                delay(1500)
                try {
                    val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to force speakerphone", e)
                }
            }

            // 拨号后重置状态
            lifecycleScope.launch {
                delay(3000)
                callState = CallState.Idle
                recognizedText = ""
                matchedContact = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            callState = CallState.CallError("拨号失败：${e.message}")
            statusMessage = "拨号失败"
            addMessage(UiChatMessage.Role.SYSTEM, "拨号失败：${e.message}")
        }
    }

    /**
     * 用户点击确认拨号
     */
    private fun onConfirmCall() {
        matchedContact?.let { confirmAndCall(it) }
    }

    /**
     * 用户取消
     */
    private fun onCancel() {
        callState = CallState.Idle
        recognizedText = ""
        matchedContact = null
        statusMessage = ""
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                app.speaker.speak("好的，已取消")
            }
        }
    }

    /**
     * 停止录音
     */
    private fun stopListening() {
        app.asrClient.stopRecording()
    }

    /**
     * 添加对话消息
     */
    private fun addMessage(role: UiChatMessage.Role, content: String) {
        conversationHistory.add(UiChatMessage(role, content))
        // 只保留最近 20 条消息
        if (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        app.asrClient.release()
        app.speaker.release()
    }
}

/**
 * 拨号状态枚举
 */
sealed class CallState {
    /** 空闲状态 */
    data object Idle : CallState()
    /** 正在录音 */
    data object Listening : CallState()
    /** 正在处理（ASR + LLM + 匹配） */
    data object Processing : CallState()
    /** 等待用户确认 */
    data object Confirming : CallState()
    /** 正在拨号 */
    data object Dialing : CallState()
    /** 出错 */
    data class CallError(val message: String) : CallState()
}

/**
 * 对话消息数据类
 */
data class UiChatMessage(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,       // 用户说的
        ASSISTANT,  // 助手回复
        SYSTEM      // 系统提示
    }
}

/**
 * 语音拨号界面 Composable
 *
 * 优化点：
 * - 深色背景 → 暖米色背景（老人白天使用不刺眼）
 * - Emoji → Material Icon
 * - 硬编码颜色 → 主题色
 * - 麦克风按钮添加脉冲动画（录音状态反馈）
 * - 对话气泡适配浅色主题
 * - 确认拨号区域适配浅色主题
 */
@Composable
fun VoiceCallScreen(
    modifier: Modifier = Modifier,
    callState: CallState = CallState.Idle,
    recognizedText: String = "",
    matchedContact: Contact? = null,
    statusMessage: String = "",
    conversationHistory: List<UiChatMessage> = emptyList(),
    onMicClick: () -> Unit = {},
    onConfirmCall: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部状态区域
        StatusArea(
            callState = callState,
            statusMessage = statusMessage,
            recognizedText = recognizedText,
            matchedContact = matchedContact
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 匹配到联系人时显示确认按钮
        if (callState == CallState.Confirming && matchedContact != null) {
            ConfirmCallArea(
                contact = matchedContact!!,
                onConfirm = onConfirmCall,
                onCancel = onCancel
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 对话历史
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(conversationHistory) { message ->
                ChatBubble(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 底部麦克风按钮
        MicButton(
            callState = callState,
            onClick = onMicClick
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 状态显示区域 - Material Icon + 主题色
 */
@Composable
fun StatusArea(
    callState: CallState,
    statusMessage: String,
    recognizedText: String,
    matchedContact: Contact?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 状态图标和颜色
        val (statusText, statusColor, statusIcon) = when (callState) {
            CallState.Idle -> Triple(
                "点击麦克风开始说话",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.Mic
            )
            CallState.Listening -> Triple(
                "正在听您说...",
                KinCallListening,
                Icons.Default.Mic
            )
            CallState.Processing -> Triple(
                "正在理解...",
                KinCallProcessing,
                Icons.Default.Search
            )
            CallState.Confirming -> Triple(
                "确认拨号",
                KinCallConfirming,
                Icons.Default.CheckCircle
            )
            CallState.Dialing -> Triple(
                "正在拨打...",
                KinCallDialing,
                Icons.Default.Phone
            )
            is CallState.CallError -> Triple(
                statusMessage.ifEmpty { "出错了" },
                MaterialTheme.colorScheme.error,
                Icons.Default.Error
            )
        }

        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = statusColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = statusText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            textAlign = TextAlign.Center
        )

        // 识别到的文本
        if (recognizedText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "「$recognizedText」",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // 匹配到的联系人
        if (matchedContact != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "→ ${matchedContact.name} ${matchedContact.phone}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 确认拨号区域 - 浅色卡片风格
 */
@Composable
fun ConfirmCallArea(
    contact: Contact,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            text = "拨打给 ${contact.name}？",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (contact.relation != null) {
            Text(
                text = "（${contact.relation}）",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = contact.phone,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 取消按钮
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text(
                    "取消",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 确认按钮
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("拨打", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 对话气泡 - 浅色主题适配
 */
@Composable
fun ChatBubble(message: UiChatMessage) {
    val isUser = message.role == UiChatMessage.Role.USER
    val isSystem = message.role == UiChatMessage.Role.SYSTEM

    // 系统消息不加背景，只用浅色小字
    if (isSystem) {
        Text(
            text = message.content,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        return
    }

    val bgColor = if (isUser) KinCallBubbleUser else KinCallBubbleAssistant
    val textColor = if (isUser) Color.White else KinCallBubbleAssistantText
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message.content,
            fontSize = 16.sp,
            color = textColor,
            modifier = Modifier
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * 麦克风按钮 - 带脉冲动画
 *
 * 录音时外圈不断扩大+淡出，给老人明确的录音状态反馈
 */
@Composable
fun MicButton(
    callState: CallState,
    onClick: () -> Unit
) {
    val isListening = callState == CallState.Listening

    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val label = when (callState) {
        CallState.Idle, is CallState.CallError -> "按住说话"
        CallState.Listening -> "点击停止"
        CallState.Processing -> "处理中..."
        CallState.Confirming -> "请确认"
        CallState.Dialing -> "拨号中..."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // 脉冲环（仅录音时显示）
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = pulseAlpha
                        }
                        .background(
                            color = KinCallListeningPulse,
                            shape = CircleShape
                        )
                )
            }

            // 主按钮
            Button(
                onClick = onClick,
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) KinCallMicActive else KinCallMicIdle
                ),
                enabled = callState == CallState.Idle ||
                        callState == CallState.Listening ||
                        callState is CallState.CallError
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
