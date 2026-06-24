package com.example.kincall.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * KinCall 通话服务
 *
 * 继承 Android InCallService，用于监听和控制系统通话。
 * 当有来电或去电时，自动将 CallActivity 带到前台，
 * 并强制开启免提（扬声器）模式，方便老年人使用。
 *
 * 需要在 AndroidManifest.xml 中声明：
 * <service
 *     android:name=".call.KinCallInCallService"
 *     android:permission="android.permission.BIND_INCALL_SERVICE"
 *     android:exported="true">
 *     <meta-data
 *         android:name="android.telecom.IN_CALL_SERVICE_UI"
 *         android:value="true" />
 *     <intent-filter>
 *         <action android:name="android.telecom.InCallService" />
 *     </intent-filter>
 * </service>
 */
class KinCallInCallService : InCallService() {

    companion object {
        private const val TAG = "KinCallInCallService"

        /**
         * 全局引用当前活跃的 InCallService 实例
         * 供 CallActivity 获取 Call 对象以控制通话
         */
        @Volatile
        var instance: KinCallInCallService? = null
            private set

        /**
         * 当前活跃的通话对象
         */
        var currentCall: Call? = null
            private set
    }

    /**
     * 通话回调
     * 监听通话状态变化
     */
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val stateName = when (state) {
                Call.STATE_NEW -> "NEW"
                Call.STATE_RINGING -> "RINGING"
                Call.STATE_DIALING -> "DIALING"
                Call.STATE_ACTIVE -> "ACTIVE"
                Call.STATE_HOLDING -> "HOLDING"
                Call.STATE_DISCONNECTED -> "DISCONNECTED"
                Call.STATE_CONNECTING -> "CONNECTING"
                Call.STATE_PULLING_CALL -> "PULLING"
                else -> "UNKNOWN($state)"
            }
            Log.d(TAG, "通话状态变化: $stateName")
        }
    }

    /**
     * 当有新通话添加时调用
     * 将 CallActivity 带到前台，记录当前通话
     */
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: 有新通话")

        // 保存当前通话引用
        currentCall = call
        instance = this

        // 注册通话状态回调
        call.registerCallback(callCallback)

        // 启动 CallActivity（全屏挂断按钮界面）
        val intent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    /**
     * 当通话被移除时调用
     * 清理资源，关闭 CallActivity
     */
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: 通话结束")

        // 注销回调
        call.unregisterCallback(callCallback)

        // 清理引用
        currentCall = null
        instance = null
    }

    /**
     * 当通话音频状态变化时调用
     * 强制开启免提（扬声器），方便老年人听清
     */
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)

        audioState?.let {
            val currentRoute = it.route
            Log.d(TAG, "当前音频路由: $currentRoute")

            // 如果当前不是扬声器模式，强制切换到扬声器
            if (currentRoute != CallAudioState.ROUTE_SPEAKER) {
                Log.d(TAG, "强制切换到扬声器模式")
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        }
    }

    /**
     * 挂断当前通话
     * 供 CallActivity 调用
     */
    fun disconnectCurrentCall() {
        currentCall?.let {
            Log.d(TAG, "挂断通话")
            it.disconnect()
        }
    }
}
