/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zex.zexbrowse.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 调试功能的解锁状态。
 *
 * 解锁后一直有效，直到用户在调试页里主动点「退出调试模式」：
 * 进程结束、系统回收、甚至重启设备都不会让入口消失。高级设置属于排查工具，
 * 用户是特意连点 7 次打开的，不该因为切了个后台就又要重来一遍。
 *
 * 状态落在 SharedPreferences 而不是 DataStore：这里需要在组合期同步读到值，
 * 决定设置页是否渲染入口卡片；异步读取会先闪一下「没有入口」再补上。
 *
 * 用 Compose 的 mutableStateOf 持有：设置页依赖它决定是否显示入口卡片，
 * 解锁瞬间列表需要即时刷新。
 */
object DebugUnlock {

    private const val PREFS_NAME = "debug_unlock"
    private const val KEY_UNLOCKED = "unlocked"

    /** 连点次数阈值，与 Android 开发者选项的传统一致 */
    private const val REQUIRED_TAPS = 7

    /**
     * 连点计数的失效间隔。
     *
     * 没有这个窗口，用户几个月里零星点几次也会凑满 7 次而意外解锁；
     * 超过这个间隔即视为重新开始计数。
     */
    private const val TAP_WINDOW_MILLIS = 1_500L

    private var prefs: SharedPreferences? = null

    var unlocked by mutableStateOf(false)
        private set

    private var taps = 0
    private var lastTapAt = 0L

    /**
     * 读回上次持久化的解锁状态，由 Application.onCreate 调用。
     *
     * 必须在首次组合之前完成，否则设置页会先按「未解锁」渲染一帧。
     */
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        unlocked = store.getBoolean(KEY_UNLOCKED, false)
    }

    /**
     * 记录一次点击。
     *
     * @return 还差几次；已解锁或刚好本次解锁时返回 0
     */
    fun tap(now: Long = System.currentTimeMillis()): Int {
        if (unlocked) return 0
        if (now - lastTapAt > TAP_WINDOW_MILLIS) taps = 0
        lastTapAt = now
        taps++
        if (taps >= REQUIRED_TAPS) {
            taps = 0
            unlocked = true
            prefs?.edit()?.putBoolean(KEY_UNLOCKED, true)?.apply()
            return 0
        }
        return REQUIRED_TAPS - taps
    }

    /** 退出调试模式：立即失效并清除持久化状态，需重新连点 */
    fun lock() {
        taps = 0
        lastTapAt = 0L
        unlocked = false
        prefs?.edit()?.putBoolean(KEY_UNLOCKED, false)?.apply()
    }
}
