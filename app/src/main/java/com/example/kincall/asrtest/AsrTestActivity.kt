package com.example.kincall.asrtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kincall.asr.AsrResult
import com.example.kincall.asr.XfAsrClient
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ASR 测试界面 Activity
 *
 * 功能：
 * 1. 提供 10 条预设的老年人语音测试句子
 * 2. 点击句子后自动朗读（TTS）并开始录音识别
 * 3. 支持自由录音模式
 * 4. 显示识别结果及与目标句子的匹配判断（通过/未通过）
 *
 * 讯飞语音听写（IAT）凭证：
 * APPID = d356e133
 * APIKey = efe7a588756cb2d2631e6b709e538e64
 * APISecret = ODU3NzE1Y2VlZTdhMGM0YWM1MmRmMDNi
 */
class AsrTestActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AsrTestActivity"

        // 讯飞语音听写凭证
        private const val APP_ID = "d356e133"
        private const val API_KEY = "efe7a588756cb2d2631e6b709e538e64"
        private const val API_SECRET = "ODU3NzE1Y2VlZTdhMGM0YWM1MmRmMDNi"
    }

    // 10 条预设测试句子（模拟老年人常见的语音内容）
    private val testSentences = listOf(
        "打电话给儿子",
        "我想听新闻",
        "今天天气怎么样",
        "帮我查一下明天的闹钟",
        "打电话给社区服务中心",
        "我肚子有点饿",
        "帮我叫一辆出租车",
        "播放京剧",
        "打电话给女儿",
        "我身体不舒服"
    )

    // TTS 引擎
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // 录音权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "需要麦克风权限才能使用语音识别", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 TTS
        tts = TextToSpeech(this, this)

        // 请求录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            AsrTestScreen()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            tts?.speechRate = 0.8f  // 稍慢的语速，方便老年人听清
            ttsReady = true
            Log.d(TAG, "TTS 初始化成功")
        } else {
            Log.e(TAG, "TTS 初始化失败: status=$status")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    /**
     * 用 TTS 朗读文本
     */
    private fun speakText(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "asr_test")
        }
    }

    /**
     * ASR 测试界面 Compose UI
     */
    @Composable
    fun AsrTestScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // 当前选中的测试句子索引（-1 表示自由录音模式）
        var selectedIndex by remember { mutableIntStateOf(-1) }

        // 识别状态
        var isRecognizing by remember { mutableStateOf(false) }

        // 识别结果列表：Pair(目标句子, 识别结果)
        var results by remember { mutableStateOf(listOf<Pair<String, AsrResult>>()) }

        // 自由录音的结果
        var freeResult by remember { mutableStateOf<AsrResult?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // 标题
            Text(
                text = "语音识别测试",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )

            // 说明文字
            Text(
                text = "点击句子 → 自动朗读 → 录音识别 → 判断是否正确",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            // 自由录音按钮
            Button(
                onClick = {
                    if (!isRecognizing) {
                        selectedIndex = -1
                        isRecognizing = true
                        coroutineScope.launch {
                            val asrClient = XfAsrClient(
                                context = context,
                                appId = APP_ID,
                                apiKey = API_KEY,
                                apiSecret = API_SECRET
                            )
                            try {
                                val result = asrClient.recognize()
                                freeResult = result
                            } catch (e: Exception) {
                                Log.e(TAG, "自由录音识别失败", e)
                                freeResult = AsrResult(
                                    text = "",
                                    isSuccess = false,
                                    errorMessage = e.message
                                )
                            } finally {
                                asrClient.release()
                                isRecognizing = false
                            }
                        }
                    }
                },
                enabled = !isRecognizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isRecognizing && selectedIndex == -1) "录音中..." else "自由录音",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 显示自由录音结果
            freeResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "自由录音结果：",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (result.isSuccess) result.text else "失败: ${result.errorMessage}",
                            fontSize = 16.sp,
                            color = if (result.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = "耗时: ${result.latencyMs}ms",
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 分隔线
            Divider(color = Color(0xFFDDDDDD))

            Spacer(modifier = Modifier.height(8.dp))

            // 测试句子列表标题
            Text(
                text = "测试句子（点击朗读并识别）",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 测试句子列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(testSentences) { index, sentence ->
                    // 查找该句子的最新识别结果
                    val sentenceResults = results.filter { it.first == sentence }
                    val latestResult = sentenceResults.lastOrNull()?.second

                    TestSentenceItem(
                        index = index + 1,
                        sentence = sentence,
                        isCurrentTarget = selectedIndex == index && isRecognizing,
                        result = latestResult,
                        onClick = {
                            if (!isRecognizing) {
                                selectedIndex = index
                                isRecognizing = true

                                // 先用 TTS 朗读句子
                                speakText(sentence)

                                // 延迟后开始录音识别（等 TTS 朗读完）
                                coroutineScope.launch {
                                    // 等待 TTS 朗读（根据句子长度估算，每字约 300ms）
                                    val delayMs = (sentence.length * 300L + 1000L).coerceAtMost(5000L)
                                    kotlinx.coroutines.delay(delayMs)

                                    val asrClient = XfAsrClient(
                                        context = context,
                                        appId = APP_ID,
                                        apiKey = API_KEY,
                                        apiSecret = API_SECRET
                                    )
                                    try {
                                        val result = asrClient.recognize()
                                        results = results + (sentence to result)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "识别失败: $sentence", e)
                                        results = results + (sentence to AsrResult(
                                            text = "",
                                            isSuccess = false,
                                            errorMessage = e.message
                                        ))
                                    } finally {
                                        asrClient.release()
                                        isRecognizing = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 测试句子条目 Composable
     *
     * @param index 序号
     * @param sentence 测试句子
     * @param isCurrentTarget 是否为当前正在识别的目标
     * @param result 识别结果（可为 null）
     * @param onClick 点击回调
     */
    @Composable
    fun TestSentenceItem(
        index: Int,
        sentence: String,
        isCurrentTarget: Boolean,
        result: AsrResult?,
        onClick: () -> Unit
    ) {
        // 判断识别结果是否与目标句子匹配
        val isPass = result?.let {
            it.isSuccess && containsKeywords(sentence, it.text)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isCurrentTarget, onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCurrentTarget -> Color(0xFFFFF3E0)  // 正在录音：橙色背景
                    isPass == true -> Color(0xFFE8F5E9)    // 通过：绿色背景
                    isPass == false -> Color(0xFFFFEBEE)   // 未通过：红色背景
                    else -> Color.White                     // 未测试：白色背景
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 序号
                    Text(
                        text = "$index.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666),
                        modifier = Modifier.width(30.dp)
                    )

                    // 测试句子
                    Text(
                        text = sentence,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333),
                        modifier = Modifier.weight(1f)
                    )

                    // 状态标签
                    when {
                        isCurrentTarget -> {
                            Text(
                                text = "录音中...",
                                fontSize = 14.sp,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        isPass == true -> {
                            Text(
                                text = "通过",
                                fontSize = 14.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        isPass == false -> {
                            Text(
                                text = "未通过",
                                fontSize = 14.sp,
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 识别结果详情
                result?.let { asrResult ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (asrResult.isSuccess) {
                            "识别结果: ${asrResult.text}"
                        } else {
                            "识别失败: ${asrResult.errorMessage}"
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "耗时: ${asrResult.latencyMs}ms",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }

    /**
     * 简单的关键字匹配判断
     * 检查识别文本是否包含目标句子中的关键字
     * 用于老年语音识别场景下的模糊匹配
     *
     * @param target 目标句子
     * @param recognized 识别结果
     * @return 是否匹配
     */
    private fun containsKeywords(target: String, recognized: String): Boolean {
        // 移除标点符号和空格后进行比较
        val cleanTarget = target.replace(Regex("[，。！？、\\s]"), "")
        val cleanRecognized = recognized.replace(Regex("[，。！？、\\s]"), "")

        if (cleanRecognized.isEmpty()) return false

        // 如果识别结果完全包含目标，直接通过
        if (cleanRecognized.contains(cleanTarget)) return true

        // 计算关键字覆盖率：将目标拆成 2-gram，检查有多少出现在识别结果中
        if (cleanTarget.length < 2) {
            return cleanRecognized.contains(cleanTarget)
        }

        val bigrams = mutableSetOf<String>()
        for (i in 0 until cleanTarget.length - 1) {
            bigrams.add(cleanTarget.substring(i, i + 2))
        }

        var matchCount = 0
        for (bigram in bigrams) {
            if (cleanRecognized.contains(bigram)) {
                matchCount++
            }
        }

        // 超过 60% 的 bigram 匹配则认为通过
        val matchRate = matchCount.toFloat() / bigrams.size
        return matchRate >= 0.6f
    }
}
