package com.example.kincall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.kincall.ui.history.ChatHistoryScreen
import com.example.kincall.ui.theme.KinCallTheme

/**
 * 主界面 Activity
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
                                },
                                onChatHistoryClick = {
                                    navController.navigate("chat_history")
                                }
                            )
                        }

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

                        composable("contact_add") {
                            ContactEditScreen(
                                contactId = null,
                                onBack = { navController.popBackStack() },
                                app = app
                            )
                        }

                        composable("contact_edit/{contactId}") { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getString("contactId")?.toLongOrNull()
                            ContactEditScreen(
                                contactId = contactId,
                                onBack = { navController.popBackStack() },
                                app = app
                            )
                        }

                        // 聊天记录
                        composable("chat_history") {
                            ChatHistoryScreen(
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
 *
 * 优化点：
 * - Emoji → Material Icon
 * - 硬编码颜色 → MaterialTheme.colorScheme
 * - 按钮高度 80dp → 88dp，圆角 16dp → 20dp
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onVoiceCallClick: () -> Unit = {},
    onContactListClick: () -> Unit = {},
    onAsrTestClick: () -> Unit = {},
    onChatHistoryClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 应用标题
        Text(
            text = "小帮拨号",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 副标题
        Text(
            text = "说名字，就拨号",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))

        // 主按钮：语音拨号
        MainMenuButton(
            text = "语音拨号",
            icon = Icons.Default.Call,
            onClick = onVoiceCallClick,
            containerColor = MaterialTheme.colorScheme.primary,
            height = 88.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 次要按钮：亲情通讯录
        MainMenuButton(
            text = "亲情通讯录",
            icon = Icons.Default.Contacts,
            onClick = onContactListClick,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            height = 72.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 测试按钮：ASR 测试
        MainMenuButton(
            text = "ASR 测试",
            icon = Icons.Default.Build,
            onClick = onAsrTestClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            height = 72.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 聊天记录按钮
        MainMenuButton(
            text = "聊天记录",
            icon = Icons.Default.History,
            onClick = onChatHistoryClick,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            height = 56.dp
        )
    }
}

/**
 * 主菜单按钮组件 - Material Icon + 文字分行显示
 */
@Composable
private fun MainMenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    height: androidx.compose.ui.unit.Dp
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
