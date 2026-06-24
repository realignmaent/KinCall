package com.example.kincall.call

import android.os.Bundle
import android.view.WindowManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 通话界面 Activity
 *
 * 全屏红色挂断按钮，覆盖整个屏幕。
 * - 在锁屏上显示（FLAG_SHOW_WHEN_LOCKED）
 * - 保持屏幕常亮（FLAG_TURN_SCREEN_ON）
 * - 大白色文字 "点击挂断" 居中显示
 * - 点击任意位置即可挂断电话
 *
 * 设计目标：为老年人提供最简单直观的挂断操作
 */
class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 在锁屏上显示 + 点亮屏幕
        // 使用 WindowManager.LayoutParams 的 flags
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        Log.d(TAG, "CallActivity 启动")

        setContent {
            CallScreen()
        }
    }

    /**
     * 通话界面 Compose UI
     *
     * 整个屏幕为红色，中间大白字 "点击挂断"
     * 点击任意位置即挂断电话
     */
    @Composable
    fun CallScreen() {
        // 整屏红色背景，点击挂断
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD32F2F))  // 红色背景
                .clickable(
                    indication = null,  // 无涟漪效果
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // 点击挂断电话
                    Log.d(TAG, "用户点击挂断")
                    KinCallInCallService.instance?.disconnectCurrentCall()
                    finish()
                },
            contentAlignment = Alignment.Center
        ) {
            // 大白字 "点击挂断"
            Text(
                text = "点击挂断",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "CallActivity 销毁")
        super.onDestroy()
    }
}
