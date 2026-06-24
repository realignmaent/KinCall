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
import androidx.compose.ui.res.painterResource
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
    private var callState by mutableStateOf(CallState.Idle)

    /** 当前识别的文本 */
    private var recognizedText by mutableStateOf("")

    /** 匹配到的联系人 */
    private var matchedContact by mutableStateOf<Contact?>(null)

    /** 状态消息（用于显示错误或提示） */
    private var statusMessage by mutableStateOf("")

    /** 对话历史记录 */
    private val conversationHistory = mutableStateListOf<ChatMessage>()

    /** 电话权限请求 */
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予，继续拨号
            matchedContact?.let { makeCall(it.phone) }
        } else {
            statusMessage = "需要电话权限才能拨号"
            callState = CallState.Error("需要电话权限，请在设置中授权")
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
    }

    /**
     * 麦克风按钮点击处理
     */
    private fun onMicButtonClicked() {
        when (callState) {
            CallState.Idle, CallState.Error -> {
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
        addMessage(ChatMessage.Role.SYSTEM, "正在听您说...")

        lifecycleScope.launch {
            try {
                // 调用 ASR 识别
                val result = withContext(Dispatchers.IO) {
                    app.asrClient.recognize()
                }

                if (!result.isSuccess) {
                    val error = result.errorMessage ?: "识别失败"
                    Log.e(TAG, "ASR failed: $error")
                    callState = CallState.Error(error)
                    statusMessage = error
                    addMessage(ChatMessage.Role.SYSTEM, "识别失败：$error")
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
                    callState = CallState.Error("未识别到语音")
                    statusMessage = "未识别到语音"
                    addMessage(ChatMessage.Role.SYSTEM, "未识别到语音")
                    withContext(Dispatchers.IO) {
                        app.speaker.speak("我没有听到声音，请再说一次")
                    }
                    delay(2000)
                    callState = CallState.Idle
                    return@launch
                }

                // 显示识别结果
                recognizedText = text
                addMessage(ChatMessage.Role.USER, text)
                callState = CallState.Processing
                statusMessage = "正在理解..."

                // 使用 ConversationManager 处理
                processWithConversationManager(text)

            } catch (e: Exception) {
                Log.e(TAG, "Error in startListening", e)
                callState = CallState.Error(e.message ?: "未知错误")
                statusMessage = "出错了：${e.message}"
                addMessage(ChatMessage.Role.SYSTEM, "出错了：${e.message}")
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
                    app.conversationManager.processUserSpeech(userText)
                }

                when (result) {
                    is ConversationResult.Calling -> {
                        // 确认拨号
                        matchedContact = result.contact
                        callState = CallState.Confirming
                        statusMessage = "确认拨打 ${result.contact.name}？"
                        addMessage(ChatMessage.Role.ASSISTANT, "好的，正在拨打给${result.contact.name}")

                        // 自动确认（MVP版本，后续可改为等待用户确认）
                        delay(1500)
                        confirmAndCall(result.contact)
                    }

                    is ConversationResult.NeedClarification -> {
                        // 需要用户澄清
                        statusMessage = result.llmReply
                        addMessage(ChatMessage.Role.ASSISTANT, result.llmReply)

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
                        addMessage(ChatMessage.Role.ASSISTANT, "好的，已取消")
                        withContext(Dispatchers.IO) {
                            app.speaker.speak("好的，已取消")
                        }
                        delay(1500)
                        callState = CallState.Idle
                    }

                    is ConversationResult.Error -> {
                        callState = CallState.Error(result.message)
                        statusMessage = result.message
                        addMessage(ChatMessage.Role.SYSTEM, "错误：${result.message}")
                        withContext(Dispatchers.IO) {
                            app.speaker.speak("抱歉，出了点问题，请再试一次")
                        }
                        delay(2000)
                        callState = CallState.Idle
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing with ConversationManager", e)
                callState = CallState.Error(e.message ?: "处理失败")
                statusMessage = "处理失败：${e.message}"
                addMessage(ChatMessage.Role.SYSTEM, "处理失败：${e.message}")
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
        addMessage(ChatMessage.Role.SYSTEM, "正在拨打 ${contact.name}（${contact.phone}）")

        // 检查电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            makeCall(contact.phone)
        } else {
            phonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /**
     * 拨打电话
     */
    private fun makeCall(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phone")
                // 请求免提
                putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
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
            callState = CallState.Error("拨号失败：${e.message}")
            statusMessage = "拨号失败"
            addMessage(ChatMessage.Role.SYSTEM, "拨号失败：${e.message}")
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
    private fun addMessage(role: ChatMessage.Role, content: String) {
        conversationHistory.add(ChatMessage(role, content))
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
enum class CallState {
    /** 空闲状态 */
    Idle,
    /** 正在录音 */
    Listening,
    /** 正在处理（ASR + LLM + 匹配） */
    Processing,
    /** 等待用户确认 */
    Confirming,
    /** 正在拨号 */
    Dialing,
    /** 出错 */
    Error
}

/**
 * 对话消息数据类
 */
data class ChatMessage(
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
 */
@Composable
fun VoiceCallScreen(
    modifier: Modifier = Modifier,
    callState: CallState = CallState.Idle,
    recognizedText: String = "",
    matchedContact: Contact? = null,
    statusMessage: String = "",
    conversationHistory: List<ChatMessage> = emptyList(),
    onMicClick: () -> Unit = {},
    onConfirmCall: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
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

        // 对话历史（占据中间区域）
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
 * 状态显示区域
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
        // 状态图标和文字
        val (icon, statusColor) = when (callState) {
            CallState.Idle -> "🎙️" to Color(0xFF666666)
            CallState.Listening -> "🔴" to Color(0xFFFF4444)
            CallState.Processing -> "🤔" to Color(0xFFFFBB33)
            CallState.Confirming -> "✅" to Color(0xFF4CAF50)
            CallState.Dialing -> "📞" to Color(0xFF2196F3)
            CallState.Error -> "❌" to Color(0xFFFF4444)
        }

        Text(
            text = icon,
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 状态文字
        Text(
            text = when (callState) {
                CallState.Idle -> "点击麦克风开始说话"
                CallState.Listening -> "正在听您说..."
                CallState.Processing -> "正在理解..."
                CallState.Confirming -> "确认拨号"
                CallState.Dialing -> "正在拨打..."
                CallState.Error -> statusMessage.ifEmpty { "出错了" }
            },
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
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center
            )
        }

        // 匹配到的联系人
        if (matchedContact != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "→ ${matchedContact.name} ${matchedContact.phone}",
                fontSize = 16.sp,
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 确认拨号区域
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
            .background(
                color = Color(0xFF2D2D44),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "拨打给 ${contact.name}？",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        if (contact.relation != null) {
            Text(
                text = "（${contact.relation}）",
                fontSize = 16.sp,
                color = Color(0xFFAAAAAA)
            )
        }
        Text(
            text = contact.phone,
            fontSize = 18.sp,
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 取消按钮
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF666666)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("取消", fontSize = 18.sp)
            }

            // 确认按钮
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("拨打", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 对话气泡
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val (bgColor, textColor, alignment) = when (message.role) {
        ChatMessage.Role.USER -> Triple(Color(0xFF2196F3), Color.White, Alignment.End)
        ChatMessage.Role.ASSISTANT -> Triple(Color(0xFF3D3D5C), Color.White, Alignment.Start)
        ChatMessage.Role.SYSTEM -> Triple(Color(0xFF2D2D44), Color(0xFFAAAAAA), Alignment.Center)
    }

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
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * 麦克风按钮
 */
@Composable
fun MicButton(
    callState: CallState,
    onClick: () -> Unit
) {
    val isListening = callState == CallState.Listening
    val buttonColor = if (isListening) Color(0xFFFF4444) else Color(0xFF4CAF50)
    val icon = if (isListening) "⏹" else "🎤"
    val label = when (callState) {
        CallState.Idle, CallState.Error -> "按住说话"
        CallState.Listening -> "点击停止"
        CallState.Processing -> "处理中..."
        CallState.Confirming -> "请确认"
        CallState.Dialing -> "拨号中..."
    }

    Button(
        onClick = onClick,
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor
        ),
        enabled = callState == CallState.Idle ||
                callState == CallState.Listening ||
                callState == CallState.Error
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                fontSize = 40.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = label,
        fontSize = 16.sp,
        color = Color(0xFFAAAAAA)
    )
}
