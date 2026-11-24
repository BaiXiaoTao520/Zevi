/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zex.zexbrowse.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNameTest {
    @Test
    fun repairsOnlyWhenLatin1CanEncodeTheWholeName() {
        val garbled = "\u00E4\u00B8\u00AD"
        assertEquals("中.zip", DownloadFileName.readable("$garbled.zip"))
        assertEquals("${garbled}文.zip", DownloadFileName.readable("${garbled}文.zip"))
        assertEquals("café.pdf", DownloadFileName.readable("café.pdf"))
    }

    @Test
    fun queryDecodesOnceAndKeepsOnlyTheFileName() {
        assertEquals(
            "part%2F1.zip",
            DownloadFileName.fromUrl("https://example.com/download?path=dir%2Fsub%5Cpart%252F1.zip")
        )
    }

    @Test
    fun extendedDispositionDecodesOnceAndKeepsOnlyTheFileName() {
        assertEquals(
            "part%5C1.zip",
            DownloadFileName.fromContentDisposition("attachment; filename*=UTF-8''dir%2Fpart%255C1.zip")
        )
    }

    @Test
    fun plainDispositionKeepsLiteralPercentEscapes() {
        assertEquals(
            "part%20one+two.zip",
            DownloadFileName.fromContentDisposition("attachment; filename=\"part%20one+two.zip\"")
        )
    }
}
