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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kincall.asrtest.AsrTestActivity
import com.example.kincall.call.VoiceCallActivity
import com.example.kincall.ui.contact.ContactEditScreen
import com.example.kincall.ui.contact.ContactListScreen
import com.example.kincall.ui.theme.KinCallTheme

/**
 * 主界面 Activity
 *
 * 使用 Compose Navigation 管理页面导航：
 * - "main" → 主页（三个入口按钮）
 * - "contacts" → 联系人列表
 * - "contact_add" → 添加联系人
 * - "contact_edit/{id}" → 编辑联系人
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KinCallTheme {
                val navController = rememberNavController()
                val app = application as KinCallApp

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // 主页
                        composable("main") {
                            MainScreen(
                                onVoiceCallClick = {
                                    startActivity(Intent(this@MainActivity, VoiceCallActivity::class.java))
                                },
                                onContactListClick = {
                                    navController.navigate("contacts")
                                },
                                onAsrTestClick = {
                                    startActivity(Intent(this@MainActivity, AsrTestActivity::class.java))
                                }
                            )
                        }

                        // 联系人列表
                        composable("contacts") {
                            ContactListScreen(
                                onBack = { navController.popBackStack() },
                                onAddContact = { navController.navigate("contact_add") },
                                onEditContact = { contactId ->
                                    navController.navigate("contact_edit/$contactId")
                                },
                                app = app
                            )
                        }

                        // 添加联系人
                        composable("contact_add") {
                            ContactEditScreen(
                                contactId = null,
                                onBack = { navController.popBackStack() },
                                app = app
                            )
                        }

                        // 编辑联系人
                        composable("contact_edit/{contactId}") { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getString("contactId")?.toLongOrNull()
                            ContactEditScreen(
                                contactId = contactId,
                                onBack = { navController.popBackStack() },
                                app = app
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 主界面 Composable
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

        // 主按钮：语音拨号
        Button(
            onClick = onVoiceCallClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text(text = "🎤 语音拨号", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 次要按钮：亲情通讯录
        Button(
            onClick = onContactListClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text(text = "📒 亲情通讯录", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 测试按钮：ASR 测试
        Button(
            onClick = onAsrTestClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
        ) {
            Text(text = "🔧 ASR 测试", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}
