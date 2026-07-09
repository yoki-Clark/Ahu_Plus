package com.ahu_plus.util

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * 超星学习通加密字体解码器。
 *
 * 关键修复 (2026-06-20):
 *   - TtfGlyphParser 补上了漏读的 8 字节 bounding box，哈希与 fontTools 完全一致
 *   - 解码时优先从 `<style id="cxSecretStyle">` 提取字体，避免匹配到页面中其他 base64 数据
 *   - 支持单引号和双引号包裹的字体 URL
 *   - 字体缓存：首次解码成功后缓存 fontBytes，后续请求直接复用
 */
object CxFontDecoder {

    private const val TAG = "CxFontDecoder"

    // 优先匹配 <style id="cxSecretStyle"> 内的 base64 字体
    private val STYLE_TAG_PATTERN = Regex("""<style[^>]*id\s*=\s*["']cxSecretStyle["'][^>]*>([\s\S]*?)</style>""")
    // 在 style 标签内容中匹配 base64 字体数据 (支持单引号和双引号)
    private val FONT_BASE64_IN_STYLE = Regex("""base64,([\w+/=]+)["']""")
    // 兜底：直接在 HTML 中匹配 base64 字体 (仅匹配长数据，避免误匹配图片等)
    private val FONT_BASE64_FALLBACK = Regex("""base64,([\w+/=]{100,})["']""")

    // 全局 hash map: md5(形状) → unicode 名 (如 "uni4E00")
    @Volatile
    private var hashMap: Map<String, String>? = null

    // 缓存上次成功解码的字体字节（不同页面可能使用相同字体）
    @Volatile
    private var cachedFontBytes: ByteArray? = null

    // 康熙部首替换表
    private data class KxRadicals(val src: String, val dst: String)

    private val KX_RADICALS_TAB: KxRadicals = run {
        val src = "⼀⼁⼂⼃⼄⼅⼆⼇⼈⼉⼊⼋⼌⼍⼎⼏⼐⼑⼒⼓⼔⼕⼖⼗⼘⼙⼚⼛⼜⼝⼞⼟⼠⼡⼢⼣⼤⼥⼦⼧⼨⼩⼪⼫⼬⼭⼮⼯⼰⼱⼲⼳⼴⼵⼶⼷⼸⼹⼺⼻⼼⼽⼾⼿⽀⽁⽂⽃⽄⽅⽆⽇⽈⽉⽊⽋⽌⽍⽎⽏⽐⽑⽒⽓⽔⽕⽖⽗⽘⽙⽚⽛⽜⽝⽞⽟⽠⽡⽢⽣⽤⽥⽦⽧⽨⽩⽪⽫⽬⽭⽮⽯⽰⽱⽲⽳⽴⽵⽶⽷⽸⽹⽺⽻⽼⽽⽾⽿⾀⾁⾂⾃⾄⾅⾆⾇⾈⾉⾊⾋⾌⾍⾎⾏⾐⾑⾒⾓⾔⾕⾖⾗⾘⾙⾚⾛⾜⾝⾞⾟⾠⾡⾢⾣⾤⾥⾦⾧⾨⾩⾪⾫⾬⾭⾮⾯⾰⾱⾲⾳⾴⾵⾶⾷⾸⾹⾺⾻⾼髙⾽⾾⾿⿀⿁⿂⿃⿄⿅⿆⿇⿈⿉⿊⿋⿌⿍⿎⿏⿐⿑⿒⿓⿔⿕⺠⻬⻩⻢⻜⻅⺟⻓"
        val dst = "一丨丶丿乙亅二亠人儿入八冂冖冫几凵刀力勹匕匚匸十卜卩厂厶又口囗土士夂夊夕大女子宀寸小尢尸屮山巛工己巾干幺广廴廾弋弓彐彡彳心戈戶手支攴文斗斤方无日曰月木欠止歹殳毋比毛氏气水火爪父爻爿片牙牛犬玄玉瓜瓦甘生用田疋疒癶白皮皿目矛矢石示禸禾穴立竹米糸缶网羊羽老而耒耳聿肉臣自至臼舌舛舟艮色艸虍虫血行衣襾見角言谷豆豕豸貝赤走足身車辛辰辵邑酉采里金長門阜隶隹雨青非面革韋韭音頁風飛食首香馬骨高高髟鬥鬯鬲鬼魚鳥鹵鹿麥麻黃黍黑黹黽鼎鼓鼠鼻齊齒龍龜龠民齐黄马飞见母长"
        require(src.length == dst.length) { "KX_RADICALS_TAB src(${src.length}) != dst(${dst.length})" }
        KxRadicals(src, dst)
    }

    /**
     * 初始化全局 hash map。**仅可调用一次**（应用启动时）。
     */
    @Synchronized
    fun init(context: Context) {
        if (hashMap != null) return
        try {
            val json = context.assets.open("font_map_table.json").use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
            val obj = JSONObject(json)
            val inverse = HashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()  // 如 "uni4E00"
                val md5 = obj.getString(key)
                inverse[md5] = key
            }
            hashMap = inverse
            Log.i(TAG, "font_map_table 加载完成: ${inverse.size} 个条目")
        } catch (e: Exception) {
            Log.e(TAG, "加载 font_map_table 失败", e)
        }
    }

    /**
     * 从 HTML 中提取 base64 字体并解密加密文本。
     *
     * 优先从 `<style id="cxSecretStyle">` 标签中提取字体数据，
     * 如果找不到则使用缓存的字体。
     */
    fun decodeFromHtml(html: String, encryptedText: String): String {
        val fontBytes = extractFontFromHtml(html) ?: cachedFontBytes ?: return encryptedText
        return decodeBytes(fontBytes, encryptedText)
    }

    /**
     * 便捷封装。
     */
    fun decode(html: String, encryptedText: String): String = decodeFromHtml(html, encryptedText)

    /**
     * 从 HTML 中提取字体字节。优先从 style 标签中提取，失败则用缓存。
     */
    private fun extractFontFromHtml(html: String): ByteArray? {
        // 1. 先找 <style id="cxSecretStyle"> 标签
        val styleMatch = STYLE_TAG_PATTERN.find(html)
        if (styleMatch != null) {
            val styleContent = styleMatch.groupValues[1]
            val fontMatch = FONT_BASE64_IN_STYLE.find(styleContent)
            if (fontMatch != null) {
                val base64 = fontMatch.groupValues[1]
                val bytes = tryDecodeBase64(base64)
                if (bytes != null) {
                    Log.d(TAG, "从 cxSecretStyle 标签提取字体成功: ${bytes.size} bytes")
                    cachedFontBytes = bytes
                    return bytes
                }
                Log.w(TAG, "cxSecretStyle 标签中 base64 解码失败")
            } else {
                Log.w(TAG, "cxSecretStyle 标签中未找到 base64 数据, 内容前200字: ${styleContent.take(200)}")
            }
        } else {
            // 检查是否有 cxSecretStyle 但正则不匹配
            if (html.contains("cxSecretStyle")) {
                Log.w(TAG, "HTML 包含 cxSecretStyle 但正则未匹配")
            }
        }

        // 2. 兜底：在整个 HTML 中搜索长 base64 数据
        val fallbackMatch = FONT_BASE64_FALLBACK.find(html)
        if (fallbackMatch != null) {
            val base64 = fallbackMatch.groupValues[1]
            val bytes = tryDecodeBase64(base64)
            if (bytes != null) {
                Log.d(TAG, "从兜底正则提取字体成功: ${bytes.size} bytes")
                cachedFontBytes = bytes
                return bytes
            }
        }

        // 3. 使用缓存
        if (cachedFontBytes != null) {
            Log.d(TAG, "使用缓存字体: ${cachedFontBytes!!.size} bytes")
        } else {
            Log.w(TAG, "未找到字体数据且无缓存, html 长度=${html.length}, 包含cxSecret=${html.contains("cxSecret")}")
        }
        return null
    }

    private fun tryDecodeBase64(base64: String): ByteArray? {
        return try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * 核心解密逻辑。
     *
     * 1. 解析 TTF 得到 glyphName(uniXXXX) → md5(形状)
     * 2. 对每个加密字符 ch，构造 uni{ord(ch):X} → dst_hash
     * 3. 用 dst_hash 反查 hashMap(离线) → 原始 unicode 名 → chr(int("4E00",16))
     * 4. 整串结果用 KX_RADICALS_TAB 替换康熙部首
     */
    fun decodeBytes(fontBytes: ByteArray, encryptedText: String): String {
        val map = hashMap ?: run {
            Log.e(TAG, "hashMap 未初始化! 请确保 CxFontDecoder.init() 已调用")
            return encryptedText
        }
        val dstFontMap = try {
            TtfGlyphParser.parseHashMap(fontBytes)
        } catch (e: Throwable) {
            Log.e(TAG, "TTF 解析失败", e)
            return encryptedText
        }

        if (dstFontMap.isEmpty()) {
            Log.w(TAG, "TTF 解析结果为空, fontBytes.size=${fontBytes.size}")
            return encryptedText
        }

        Log.d(TAG, "TTF 解析完成: ${dstFontMap.size} 个字形, font_map_table: ${map.size} 个条目")

        var hitCount = 0
        var missCount = 0
        val sb = StringBuilder(encryptedText.length)
        for (ch in encryptedText) {
            val name = "uni${ch.code.toString(16).uppercase()}"
            val dstHash = dstFontMap[name]
            if (dstHash != null) {
                val originalName = map[dstHash]
                if (originalName != null && originalName.startsWith("uni") && originalName.length > 3) {
                    val hex = originalName.substring(3)
                    try {
                        val codePoint = hex.toInt(16)
                        sb.appendCodePoint(codePoint)
                        hitCount++
                        continue
                    } catch (_: NumberFormatException) {
                        // fall through
                    }
                }
                missCount++
                if (missCount <= 5) {
                    Log.d(TAG, "哈希未命中: $name (U+${ch.code.toString(16).uppercase()}) → hash=$dstHash")
                }
            } else {
                // 不在加密字体中，可能是普通字符
            }
            sb.append(ch)
        }

        if (missCount > 0) {
            Log.w(TAG, "解码完成: 命中=$hitCount, 未命中=$missCount, 总字符=${encryptedText.length}")
        }

        // 康熙部首 → 现代汉字
        val tab = KX_RADICALS_TAB
        val sb2 = StringBuilder(sb.length)
        for (ch in sb) {
            val idx = tab.src.indexOf(ch)
            sb2.append(if (idx >= 0) tab.dst[idx] else ch)
        }
        return sb2.toString()
    }
}
