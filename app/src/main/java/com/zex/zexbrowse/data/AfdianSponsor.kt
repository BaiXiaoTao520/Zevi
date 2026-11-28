/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zex.zexbrowse.data

import com.zex.zexbrowse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

/** 一位赞助者的展示数据 */
data class Sponsor(
    val name: String,
    val avatarUrl: String,
    /** 累计赞助金额，站点原样返回的字符串，如 "30.00"；为空表示不展示 */
    val amount: String,
    /** 建立赞助关系的时间（秒），0 表示未知 */
    val since: Long
)

/**
 * 爱发电开放接口：查询赞助者名单。
 *
 * 按官方文档实现（afdian.com/api/open/query-sponsor）：
 * - 请求体为 `user_id` / `params` / `ts` / `sign` 四个字段；
 * - `params` 是**字符串形式的 JSON**，不是嵌套对象；
 * - `sign = md5(token + "params" + params + "ts" + ts + "user_id" + user_id)`，
 *   token 只参与计算，不随请求上传；
 * - 时间戳为秒级，与服务器偏差超过 3600 秒会被判过期。
 *
 * user_id 与 token 都不写进源码，由构建期注入（见 app/build.gradle.kts）。
 * 未注入时 [isAvailable] 为 false，界面给出「未配置」提示而不是发一个必然失败的请求。
 */
object AfdianSponsor {

    private const val ENDPOINT = "https://afdian.com/api/open/query-sponsor"
    private const val PAGE_SIZE = 50
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    private val userId: String get() = BuildConfig.AFDIAN_USER_ID.trim()
    private val token: String get() = BuildConfig.AFDIAN_TOKEN.trim()

    /** 凭证齐备才可用 */
    val isAvailable: Boolean get() = userId.isNotBlank() && token.isNotBlank()

    /**
     * 拉取赞助者名单。
     *
     * 只取第一页：面板是「感谢名单」，不是对账工具，翻页意义不大；
     * 单页上限 50，超出时按站点给出的排序展示最近建立关系的 50 位。
     *
     * 返回 [Result] 而不是空列表，是为了让界面能区分「确实还没有人赞助」与「请求失败」。
     */
    suspend fun sponsors(client: OkHttpClient = defaultClient()): Result<List<Sponsor>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.success(emptyList())
        runCatching {
            val params = JSONObject().put("page", 1).put("per_page", PAGE_SIZE).toString()
            val ts = System.currentTimeMillis() / 1000
            val sign = md5("$token" + "params" + params + "ts" + ts + "user_id" + userId)
            val payload = JSONObject()
                .put("user_id", userId)
                .put("params", params)
                .put("ts", ts)
                .put("sign", sign)
                .toString()
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                // 站点对没有 UA 的请求直接回 403，必须带上浏览器标识
                .header("User-Agent", USER_AGENT)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                val root = JSONObject(text)
                if (root.optInt("ec") != 200) error(root.optString("em").ifBlank { "ec=${root.optInt("ec")}" })
                val list = root.optJSONObject("data")?.optJSONArray("list") ?: return@use emptyList()
                buildList {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val user = item.optJSONObject("user") ?: continue
                        val name = user.optString("name").trim().ifBlank { continue }
                        add(
                            Sponsor(
                                name = name,
                                avatarUrl = normalizeAvatar(user.optString("avatar").trim()),
                                amount = item.optString("all_sum_amount").trim(),
                                since = item.optLong("create_time", 0L)
                            )
                        )
                    }
                }
            }
        }
    }

    /** 站点返回的头像地址没有协议头（pic1.afdiancdn.com/...），补上 https 才能被图片库加载 */
    private fun normalizeAvatar(raw: String): String = when {
        raw.isBlank() -> ""
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        else -> "https://$raw"
    }

    /** 赞助名单是一次性拉取，超时压短，失败就让用户手动刷新，不做长等待 */
    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
