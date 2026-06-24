package com.example.kincall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kincall.asrtest.AsrTestActivity
import com.example.kincall.call.VoiceCallActivity
import com.example.kincall.ui.theme.KinCallTheme

/**
 * 主界面 Activity
 *
 * 提供三个主要入口：
 * 1. 语音拨号 - 核心功能，启动 VoiceCallActivity
 * 2. 亲情通讯录 - 管理联系人
 * 3. ASR 测试 - 测试语音识别
 *
 * UI 设计为大字体、大按钮，适合老年人使用。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KinCallTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onVoiceCallClick = {
                            startActivity(Intent(this, VoiceCallActivity::class.java))
                        },
                        onContactListClick = {
                            // TODO: 导航到联系人列表页面
                            // startActivity(Intent(this, ContactListActivity::class.java))
                        },
                        onAsrTestClick = {
                            startActivity(Intent(this, AsrTestActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 主界面 Composable
 *
 * @param modifier 修饰符
 * @param onVoiceCallClick 语音拨号按钮点击回调
 * @param onContactListClick 亲情通讯录按钮点击回调
 * @param onAsrTestClick ASR测试按钮点击回调
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onVoiceCallClick: () -> Unit = {},
    onContactListClick: () -> Unit = {},
    onAsrTestClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 应用图标（使用 Material Icons 的 Phone 图标）
        Icon(
            painter = painterResource(id = android.R.drawable.ic_menu_call),
            contentDescription = "小帮拨号",
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 应用标题
        Text(
            text = "小帮拨号",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333)
        )

        // 副标题
        Text(
            text = "说名字，就拨号",
            fontSize = 18.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))

        // 主按钮：语音拨号（最大、最醒目）
        Button(
            onClick = onVoiceCallClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)  // 绿色，表示"开始"
            )
        ) {
            Text(
                text = "🎤 语音拨号",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 次要按钮：亲情通讯录
        Button(
            onClick = onContactListClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3)  // 蓝色
            )
        ) {
            Text(
                text = "📒 亲情通讯录",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 测试按钮：ASR 测试
        Button(
            onClick = onAsrTestClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800)  // 橙色，表示测试功能
            )
        ) {
            Text(
                text = "🔧 ASR 测试",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KinCallTheme {
        MainScreen()
    }
}
