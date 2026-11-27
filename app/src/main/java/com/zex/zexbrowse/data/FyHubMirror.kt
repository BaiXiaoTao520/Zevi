/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zex.zexbrowse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** 枫源镜像上的一个可下载资产 */
data class FyHubAsset(
    /** 已去掉 v 前缀的版本号，如 12.5.0 */
    val version: String,
    /** 站点给的下载路径，形如 /Zevi/v12.5.0/app-release.apk */
    val downloadPath: String,
    /** 资产摘要，形如 2c7c...（已去掉 sha256: 前缀） */
    val sha256: String
)

/**
 * 枫源镜像（FyHub）资源站接入。
 *
 * 只使用官方文档列明的稳定接口：
 * - `GET /api/public/v1/projects/{project_id}/assets` 查询版本与资产，无需任何验证；
 * - 下载走文档的「方式一」：把用户带到主站验证页
 *   `https://fyhub.cn/{project_id}/{version}/{file_name}`，
 *   由主站完成 PoW 挑战、领取授权并选择下载节点后再跳转真实地址。
 *
 * 不实现文档「方式二」的 `/api/public/v2/api/*` 程序下载链路：它要求客户端自己完成
 * 3072 位 RSA repeated-squaring，服务端下发的 iterations 是数万次量级的单线程模平方，
 * 在手机上是一段很长的纯计算。本应用自身就是浏览器，验证页交给自己的内核跑既符合
 * 文档对前端入口的推荐做法，也不必在应用里重复实现挑战。
 */
object FyHubMirror {

    private const val BASE = "https://fyhub.cn"

    /** 本应用在该站的项目标识，见 GET /api/public/v1/projects */
    const val PROJECT_ID = "Zevi"

    /** 项目页 */
    fun projectUrl(): String = "$BASE/$PROJECT_ID/"

    /**
     * 查询该项目下所有当前可下载的正式资产。
     *
     * 只保留 `available = true` 且 `prerelease = false` 的条目；
     * 接口不可达或返回结构异常时返回空列表，由调用方回退其它来源。
     */
    suspend fun assets(client: OkHttpClient): List<FyHubAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE/api/public/v1/projects/$PROJECT_ID/assets")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<FyHubAsset>()
                val text = response.body?.string()?.trim().orEmpty()
                if (text.isBlank()) return@use emptyList<FyHubAsset>()
                val root = runCatching { JSONObject(text) }.getOrNull() ?: return@use emptyList<FyHubAsset>()
                if (root.optString("status") != "success") return@use emptyList<FyHubAsset>()
                val array = root.optJSONObject("data")?.optJSONArray("assets") ?: return@use emptyList<FyHubAsset>()
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        if (!item.optBoolean("available", false)) continue
                        if (item.optBoolean("prerelease", false)) continue
                        val version = item.optString("version").trim().removePrefix("v").removePrefix("V")
                        val path = item.optString("download_path").trim()
                        if (version.isBlank() || path.isBlank()) continue
                        add(
                            FyHubAsset(
                                version = version,
                                downloadPath = path,
                                sha256 = item.optString("digest_sha256").removePrefix("sha256:")
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 主站验证页地址：验证、授权与节点选择都由站点页面完成 */
    fun verifyPageUrl(downloadPath: String): String = BASE + downloadPath
}
