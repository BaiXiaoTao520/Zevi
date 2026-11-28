/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zex.zexbrowse.data

import com.zex.zexbrowse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 一位赞助者已获准公开的展示数据 */
data class Sponsor(
    val name: String,
    val avatarUrl: String,
    /** 仅在赞助者同意公开时提供金额；为空表示不展示 */
    val amount: String
)

/** 从公开 HTTPS 地址获取经审核的赞助名单。 */
object AfdianSponsor {

    private const val PAGE_SIZE = 50
    private val endpoint = BuildConfig.SPONSOR_FEED_URL.toHttpUrlOrNull()
        ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }

    val isAvailable: Boolean get() = endpoint != null

    /** 返回 [Result] 以区分空名单与请求失败。 */
    suspend fun sponsors(client: OkHttpClient = defaultClient()): Result<List<Sponsor>> = withContext(Dispatchers.IO) {
        val url = endpoint ?: return@withContext Result.success(emptyList())
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val list = JSONObject(response.body?.string().orEmpty()).getJSONArray("sponsors")
                buildList {
                    for (index in 0 until minOf(list.length(), PAGE_SIZE)) {
                        val item = list.optJSONObject(index) ?: continue
                        val name = (item.opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                        add(
                            Sponsor(
                                name = name,
                                avatarUrl = (item.opt("avatarUrl") as? String)?.toHttpUrlOrNull()
                                    ?.takeIf { it.isHttps }?.toString().orEmpty(),
                                amount = (item.opt("amount") as? String)?.trim().orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    /** 总超时覆盖完整响应体，失败时可手动刷新。 */
    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()
}
