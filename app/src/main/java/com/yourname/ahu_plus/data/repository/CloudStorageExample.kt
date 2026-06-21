package com.yourname.ahu_plus.data.repository

import android.util.Log

/**
 * CloudStorageRepository 使用示例
 *
 * 在 ViewModel 或其他地方注入 cloudStorageRepository 后调用这些方法
 */
object CloudStorageExample {

    private const val TAG = "CloudStorage"

    /**
     * 示例：上传 JSON 数据到 COS
     */
    suspend fun uploadUserData(repo: CloudStorageRepository, userId: String, jsonData: String) {
        try {
            val cosKey = "user_data/$userId.json"
            val result = repo.uploadString(cosKey, jsonData)
            Log.d(TAG, "上传成功: ${repo.getFileUrl(cosKey)}")
        } catch (e: Exception) {
            Log.e(TAG, "上传失败: ${e.message}")
        }
    }

    /**
     * 示例：从 COS 下载 JSON 数据
     */
    suspend fun downloadUserData(repo: CloudStorageRepository, userId: String): String? {
        return try {
            val cosKey = "user_data/$userId.json"
            val content = repo.downloadAsString(cosKey)
            Log.d(TAG, "下载成功: ${content.take(100)}...")
            content
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${e.message}")
            null
        }
    }

    /**
     * 示例：上传本地文件到 COS
     */
    suspend fun uploadFile(repo: CloudStorageRepository, localPath: String, cosKey: String) {
        try {
            val result = repo.uploadFile(cosKey, localPath)
            Log.d(TAG, "文件上传成功: ${repo.getFileUrl(cosKey)}")
        } catch (e: Exception) {
            Log.e(TAG, "文件上传失败: ${e.message}")
        }
    }

    /**
     * 示例：生成临时签名 URL（用于私有文件分享）
     */
    fun getShareUrl(repo: CloudStorageRepository, cosKey: String): String {
        val signedUrl = repo.getSignedUrl(cosKey, 3600) // 1小时有效
        Log.d(TAG, "签名URL: $signedUrl")
        return signedUrl
    }

    /**
     * 示例：删除 COS 上的文件
     */
    suspend fun deleteFile(repo: CloudStorageRepository, cosKey: String) {
        try {
            repo.deleteFile(cosKey)
            Log.d(TAG, "文件删除成功: $cosKey")
        } catch (e: Exception) {
            Log.e(TAG, "文件删除失败: ${e.message}")
        }
    }
}
