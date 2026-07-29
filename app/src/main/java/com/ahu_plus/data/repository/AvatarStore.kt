package com.ahu_plus.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.ahu_plus.data.diagnostic.SafeLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 头像图片文件本地存储 (filesDir/avatars/)。
 *
 * - 真实相片缓存: [realAvatarFile] —— 由 [YcardRepository.getUserAvatarUrl] 拿到 URL
 *   后经 [YcardRepository.downloadAvatarBytes] 下载字节写入。**切换头像模式不清**,
 *   仅退登/全清时由 [clearAll] 清理(见 AhuPlusApplication.clearAccountScopedRepositoryState)。
 * - 自定头像: [customAvatarFile] —— 用户裁剪后的 [Bitmap] 压缩写入。
 *
 * 线程: 所有 IO 操作自行切到 [Dispatchers.IO]。
 */
class AvatarStore(
    private val appContext: Context,
    private val ycardRepository: YcardRepository,
) {
    private val dir: File get() = File(appContext.filesDir, "avatars")

    /** 真实相片缓存文件(无论是否存在)。 */
    val realAvatarFile: File get() = File(dir, "real_avatar.jpg")

    /** 自定头像文件(无论是否存在)。 */
    val customAvatarFile: File get() = File(dir, "custom_avatar.jpg")

    /** 真实相片缓存文件,不存在返回 null。 */
    fun realAvatarFileOrNull(): File? = realAvatarFile.takeIf { it.exists() }

    /** 自定头像文件,不存在返回 null。 */
    fun customAvatarFileOrNull(): File? = customAvatarFile.takeIf { it.exists() }

    /**
     * 确保真实相片缓存文件存在。force=true 或文件缺失时重新下载覆盖。
     *
     * @return 缓存文件(下载成功或已存在);下载/写入失败返回 null。
     */
    suspend fun ensureRealAvatar(url: String, force: Boolean = false): File? =
        withContext(Dispatchers.IO) {
            val file = realAvatarFile
            if (!force && file.exists()) return@withContext file
            val bytes = ycardRepository.downloadAvatarBytes(url).getOrNull()
                ?: return@withContext null
            try {
                dir.mkdirs()
                file.writeBytes(bytes)
                file
            } catch (e: Exception) {
                Log.e(TAG, "写入真实相片缓存失败", e)
                if (file.exists()) file.delete()
                null
            }
        }

    /**
     * 把裁剪后的自定头像 [bitmap] 压缩写入 [customAvatarFile]。
     *
     * @return 写入成功的文件;失败返回 null。
     */
    suspend fun saveCustomAvatar(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        val file = customAvatarFile
        try {
            dir.mkdirs()
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "写入自定头像失败", e)
            if (file.exists()) file.delete()
            null
        }
    }

    /**
     * 退登/全清: 删除全部头像缓存文件(真实相片 + 自定头像)。
     * mode 的清理由 SessionManager 负责,这里只清图片字节文件。
     */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "AvatarStore"
    }
}
