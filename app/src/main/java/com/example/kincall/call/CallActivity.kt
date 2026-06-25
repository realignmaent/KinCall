package com.example.kincall.call

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kincall.ui.theme.KinCallHangUp
import com.example.kincall.ui.theme.KinCallOnCallBackground

/**
 * 通话界面 Activity
 *
 * 优化点：
 * - 纯红色背景 → 暖米色背景 + 挂断按钮红色
 * - 显示联系人姓名、关系、首字母头像
 * - 显示通话时长
 * - 点击任意位置可挂断
 */
class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var durationSeconds by mutableIntStateOf(0)

    private val timerRunnable = object : Runnable {
        override fun run() {
            durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 从 Intent 获取联系人信息
        val contactName = intent.getStringExtra("contact_name") ?: ""
        val contactRelation = intent.getStringExtra("contact_relation") ?: ""
        val contactPhone = intent.getStringExtra("contact_phone") ?: ""

        // 开始计时
        startTime = System.currentTimeMillis()
        handler.post(timerRunnable)

        Log.d(TAG, "CallActivity 启动: $contactName ($contactPhone)")

        setContent {
            CallScreen(
                contactName = contactName,
                contactRelation = contactRelation,
                callDuration = formatDuration(durationSeconds),
                onHangUp = {
                    Log.d(TAG, "用户点击挂断")
                    KinCallInCallService.instance?.disconnectCurrentCall()
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        Log.d(TAG, "CallActivity 销毁")
        super.onDestroy()
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%02d:%02d".format(min, sec)
    }
}

/**
 * 通话界面 Composable
 */
@Composable
fun CallScreen(
    contactName: String = "",
    contactRelation: String = "",
    callDuration: String = "00:00",
    onHangUp: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onHangUp() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // 顶部：联系人信息
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 头像圆圈（首字母）
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contactName.firstOrNull()?.toString() ?: "?",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = contactName.ifBlank { "未知联系人" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (contactRelation.isNotBlank()) {
                    Text(
                        text = contactRelation,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 通话时长
                Text(
                    text = callDuration,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // 底部：挂断按钮
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onHangUp,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KinCallHangUp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "挂断",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击挂断",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
