package com.ahu_plus.util

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 简化的 TTF `glyf` 表解析器。
 *
 * 移植自 Samueli924/chaoxing 的 `api/cxsecret_font.py` 中 `font2map()` 所需的最小子集:
 *  - 读取 `cmap` 表获得 `unicode → glyph name` 映射(用于遍历 glyphOrder 时识别 `uniXXXX`)
 *  - 读取 `loca` 表获得每个字形的偏移
 *  - 读取 `glyf` 表的 `numberOfContours / endPtsOfContours / flags / coordinates`
 *
 * 不依赖 fontTools / Apache PDFBox 等额外库,纯 NIO ByteBuffer + Big-Endian 解析。
 *
 * 用法:
 *   val glyphMap = TtfGlyphParser.parseHashMap(ttfBytes) // glyphName("uni4E00") -> md5
 */
object TtfGlyphParser {

    /**
     * 解析 TTF 字节流,返回每个 glyph name 的形状指纹 MD5。
     *
     * @return Map<glyphName, md5> —— 与 Python 端 `font2map()` 输出格式一致。
     */
    fun parseHashMap(ttfBytes: ByteArray): Map<String, String> {
        val tables = readTableDirectory(ttfBytes)
        val glyfData = tables["glyf"] ?: return emptyMap()
        val locaData = tables["loca"] ?: return emptyMap()
        val headData = tables["head"] ?: return emptyMap()

        // head.indexToLocFormat: 0 = short offsets, 1 = long offsets
        val indexToLocFormat = readUInt16(headData, 50)
        val numGlyphs = readUInt16(tables["maxp"] ?: return emptyMap(), 4)

        val result = HashMap<String, String>(numGlyphs)

        for (glyphIndex in 0 until numGlyphs) {
            val glyphName = "glyph$glyphIndex"  // 我们只用坐标,不依赖 glyph name 匹配 unicode
            // 真要拿到 glyph name 需要 post-script 名或 CFF,这里跳过;
            // unicode→glyph name 的映射在 cmap 中,而解码逻辑只需 glyphName→hash。
            // Chaoxing 解码逻辑需要 uniXXXX→hash。我们反向:从 glyph coordinates 算出 md5,
            // 然后通过 glyphIndex 反查 cmap 拿到 unicode 名 → uniXXXX。
            val glyphHash = readGlyphHash(locaData, glyfData, glyphIndex, indexToLocFormat)
            if (glyphHash != null) {
                // 暂存:glyphIndex -> md5
                result[glyphName] = glyphHash
            }
        }

        // 现在通过 cmap 建立 unicode → glyphIndex → md5
        val cmap = readCmap(tables["cmap"] ?: return result)
        val finalMap = HashMap<String, String>(cmap.size)
        for ((unicode, glyphIndex) in cmap) {
            val hash = result["glyph$glyphIndex"] ?: continue
            finalMap["uni${unicode.toString(16).uppercase()}"] = hash
        }
        return finalMap
    }

    /**
     * 给定 TTF 字节流 + glyphIndex,计算该字形的 MD5 哈希。
     *
     * 实现与 Python 端 `hash_glyph()` 一致:
     *   pos_bin = "".join(f"{x}{y}{flag}" for x,y,flag in glyph)
     *   return md5(pos_bin).hexdigest()
     */
    private fun readGlyphHash(
        locaData: ByteArray,
        glyfData: ByteArray,
        glyphIndex: Int,
        indexToLocFormat: Int,
    ): String? {
        val (offset, length) = readLoca(locaData, glyphIndex, indexToLocFormat)
        if (length <= 0) return null

        val buf = ByteBuffer.wrap(glyfData, offset, length).order(ByteOrder.BIG_ENDIAN)
        val numberOfContours = buf.short.toInt()
        if (numberOfContours <= 0) return null  // 空 glyph 或复合 glyph(暂不支持)

        // 跳过 bounding box (xMin, yMin, xMax, yMax) — 共 8 字节
        // fontTools 的 glyphHeaderFormat = ">hhhHH" (10 字节),前 2 字节是 numberOfContours
        buf.position(buf.position() + 8)

        val endPts = ShortArray(numberOfContours)
        for (i in 0 until numberOfContours) {
            endPts[i] = buf.short
        }
        val totalPoints = (endPts[numberOfContours - 1].toInt() and 0xFFFF) + 1

        // instructions 长度
        val instructionLength = buf.short.toInt() and 0xFFFF
        buf.position(buf.position() + instructionLength)

        // flags
        val flags = ByteArray(totalPoints)
        var i = 0
        while (i < totalPoints) {
            val flag = buf.get().toInt() and 0xFF
            flags[i] = flag.toByte()
            i++
            if (flag and 0x08 != 0) {
                // repeat flag
                val repeatCount = buf.get().toInt() and 0xFF
                val end = minOf(i + repeatCount, totalPoints)
                while (i < end) {
                    flags[i] = flag.toByte()
                    i++
                }
            }
        }

        // x coordinates (delta-encoded)
        val xs = ShortArray(totalPoints)
        var x = 0
        for (k in 0 until totalPoints) {
            val flag = flags[k].toInt() and 0xFF
            val dx = when {
                flag and 0x02 != 0 -> {
                    val b = buf.get().toInt() and 0xFF
                    if (flag and 0x10 != 0) b else -b
                }
                flag and 0x10 != 0 -> 0
                else -> buf.short.toInt()
            }
            x += dx
            xs[k] = x.toShort()
        }

        // y coordinates
        val ys = ShortArray(totalPoints)
        var y = 0
        for (k in 0 until totalPoints) {
            val flag = flags[k].toInt() and 0xFF
            val dy = when {
                flag and 0x04 != 0 -> {
                    val b = buf.get().toInt() and 0xFF
                    if (flag and 0x20 != 0) b else -b
                }
                flag and 0x20 != 0 -> 0
                else -> buf.short.toInt()
            }
            y += dy
            ys[k] = y.toShort()
        }

        val sb = StringBuilder(totalPoints * 8)
        for (k in 0 until totalPoints) {
            val f = flags[k].toInt() and 0x01
            sb.append(xs[k].toInt()).append(ys[k].toInt()).append(f)
        }
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class LocaEntry(val offset: Int, val length: Int)

    private fun readLoca(locaData: ByteArray, glyphIndex: Int, format: Int): LocaEntry {
        val buf = ByteBuffer.wrap(locaData).order(ByteOrder.BIG_ENDIAN)
        val (off1, off2) = if (format == 0) {
            // short: 每个 offset 占 2 字节,实际 offset = value * 2
            val o1 = (buf.getShort(glyphIndex * 2).toInt() and 0xFFFF) * 2
            val o2 = (buf.getShort((glyphIndex + 1) * 2).toInt() and 0xFFFF) * 2
            o1 to o2
        } else {
            val o1 = buf.getInt(glyphIndex * 4)
            val o2 = buf.getInt((glyphIndex + 1) * 4)
            o1 to o2
        }
        return LocaEntry(off1, off2 - off1)
    }

    /**
     * 读取 cmap 表 (format 4),返回 unicode → glyphIndex 映射。
     */
    private fun readCmap(cmapData: ByteArray): Map<Int, Int> {
        val buf = ByteBuffer.wrap(cmapData).order(ByteOrder.BIG_ENDIAN)
        val version = buf.short.toInt() and 0xFFFF
        if (version != 0) return emptyMap()
        val numTables = buf.short.toInt() and 0xFFFF

        var bestOffset = -1
        var bestFormat = 0
        for (i in 0 until numTables) {
            val platformID = buf.short.toInt() and 0xFFFF
            val encodingID = buf.short.toInt() and 0xFFFF
            val subtableOffset = buf.getInt()
            // 优先选 Microsoft Unicode BMP (platform 3, encoding 1) 或 Unicode full (platform 0)
            val isUnicode = (platformID == 3 && encodingID == 1) ||
                (platformID == 0)
            if (isUnicode) {
                // 探查 format
                val subBuf = ByteBuffer.wrap(cmapData, subtableOffset, cmapData.size - subtableOffset).order(ByteOrder.BIG_ENDIAN)
                val fmt = subBuf.short.toInt() and 0xFFFF
                if (fmt == 4 || fmt == 6 || fmt == 12) {
                    bestOffset = subtableOffset
                    bestFormat = fmt
                    if (platformID == 3 && encodingID == 1) break  // 优先这个
                }
            }
        }
        if (bestOffset < 0) return emptyMap()

        val subBuf = ByteBuffer.wrap(cmapData, bestOffset, cmapData.size - bestOffset).order(ByteOrder.BIG_ENDIAN)
        return when (bestFormat) {
            4 -> readCmapFormat4(subBuf)
            6 -> readCmapFormat6(subBuf)
            12 -> readCmapFormat12(subBuf)
            else -> emptyMap()
        }
    }

    private fun readCmapFormat4(buf: ByteBuffer): Map<Int, Int> {
        buf.getShort()  // format
        val length = buf.short.toInt() and 0xFFFF
        buf.getShort()  // language
        val segCount = (buf.short.toInt() and 0xFFFF) / 2
        // skip searchRange, entrySelector, rangeShift (6 bytes)
        buf.position(buf.position() + 6)

        val endCodes = ShortArray(segCount)
        for (i in 0 until segCount) endCodes[i] = buf.short
        buf.short  // reservedPad

        val startCodes = ShortArray(segCount)
        for (i in 0 until segCount) startCodes[i] = buf.short

        val idDeltas = ShortArray(segCount)
        for (i in 0 until segCount) idDeltas[i] = buf.short

        // ★ 关键修复: 记录 idRangeOffset 数组的起始位置 (参照 TrueType 规范)
        // "idRangeOffset[i] is an offset from the beginning of the idRangeOffset array"
        val idRangeOffsetsStart = buf.position()

        val idRangeOffsets = IntArray(segCount)
        for (i in 0 until segCount) idRangeOffsets[i] = buf.short.toInt() and 0xFFFF

        val map = HashMap<Int, Int>()
        for (i in 0 until segCount) {
            val start = startCodes[i].toInt() and 0xFFFF
            val end = endCodes[i].toInt() and 0xFFFF
            val delta = idDeltas[i].toInt()
            val rangeOffset = idRangeOffsets[i]
            for (c in start..end) {
                val glyphId = if (rangeOffset == 0) {
                    ((c + delta) and 0xFFFF)
                } else {
                    // 使用 idRangeOffsetsStart 而不是 buf.position() (已推进到数组末尾)
                    // 直接通过 buf.getShort(index) 访问，无需额外 wrap ByteBuffer
                    val glyphIdArrayOffset = idRangeOffsetsStart + i * 2 + rangeOffset + (c - start) * 2
                    val raw = buf.getShort(glyphIdArrayOffset).toInt() and 0xFFFF
                    if (raw == 0) 0 else (raw + delta) and 0xFFFF
                }
                if (glyphId != 0) map[c] = glyphId
            }
        }
        return map
    }

    private fun readCmapFormat6(buf: ByteBuffer): Map<Int, Int> {
        buf.getShort()  // format
        buf.getShort()  // length
        buf.getShort()  // language
        val firstCode = buf.short.toInt() and 0xFFFF
        val entryCount = buf.short.toInt() and 0xFFFF
        val map = HashMap<Int, Int>(entryCount)
        for (i in 0 until entryCount) {
            val gid = buf.short.toInt() and 0xFFFF
            if (gid != 0) map[firstCode + i] = gid
        }
        return map
    }

    private fun readCmapFormat12(buf: ByteBuffer): Map<Int, Int> {
        buf.getShort()  // format
        buf.getShort()  // reserved (2 bytes, not 4)
        buf.getInt()    // length
        buf.getInt()    // language
        val numGroups = buf.getInt()
        val map = HashMap<Int, Int>(numGroups * 4)
        for (i in 0 until numGroups) {
            val startCharCode = buf.getInt()
            val endCharCode = buf.getInt()
            val startGlyphID = buf.getInt()
            for (c in startCharCode..endCharCode) {
                map[c] = startGlyphID + (c - startCharCode)
            }
        }
        return map
    }

    private fun readTableDirectory(ttfBytes: ByteArray): Map<String, ByteArray> {
        val buf = ByteBuffer.wrap(ttfBytes).order(ByteOrder.BIG_ENDIAN)
        buf.getInt()  // sfVersion
        val numTables = buf.short.toInt() and 0xFFFF
        buf.getShort(); buf.getShort(); buf.getShort()  // searchRange, entrySelector, rangeShift

        val records = ArrayList<Pair<String, Pair<Int, Int>>>(numTables)
        for (i in 0 until numTables) {
            val tag = String(byteArrayOf(buf.get(), buf.get(), buf.get(), buf.get()), Charsets.US_ASCII)
            val checksum = buf.getInt()
            val offset = buf.getInt()
            val length = buf.getInt()
            records.add(tag to (offset to length))
        }

        val out = HashMap<String, ByteArray>(records.size)
        for ((tag, info) in records) {
            val (offset, length) = info
            out[tag] = ttfBytes.copyOfRange(offset, offset + length)
        }
        return out
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        val buf = ByteBuffer.wrap(data, offset, 2).order(ByteOrder.BIG_ENDIAN)
        return buf.short.toInt() and 0xFFFF
    }
}
