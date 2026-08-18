package com.cloner.app.gdrive

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * DriveVideoItem: Thông tin video và metadata lưu trên Google Drive
 */
data class DriveVideoItem(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val title: String,
    val caption: String,
    val hashtags: String,
    val targetClone: String,
    val suggestedTime: String,
    val modifiedTime: String
)

/**
 * GoogleDriveClient: Kết nối Google Drive REST API v3 trực tiếp từ Android
 * Hỗ trợ cả 2 phương thức:
 * 1. OAuth 2.0 Token JSON (client_id, client_secret, refresh_token)
 * 2. Service Account JSON (client_email, private_key)
 */
object GoogleDriveClient {

    private const val PREFS_NAME = "gdrive_config"
    private const val KEY_AUTH_JSON = "auth_json"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"

    private var cachedToken: String? = null
    private var tokenExpiryEpoch: Long = 0L

    fun saveServiceAccountJson(context: Context, jsonStr: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTH_JSON, jsonStr)
            .apply()
        cachedToken = null
    }

    fun getServiceAccountJson(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_JSON, null)
        if (!saved.isNullOrEmpty()) return saved

        return try {
            context.assets.open("default_gdrive_config.json").bufferedReader().use { it.readText() }
        } catch (ignored: Exception) {
            null
        }
    }

    fun hasCredentials(context: Context): Boolean {
        val json = getServiceAccountJson(context) ?: return false
        return (json.contains("refresh_token") && json.contains("client_id")) ||
                (json.contains("private_key") && json.contains("client_email"))
    }

    /**
     * Lấy Access Token (Tự động nhận diện OAuth2 Refresh Token hoặc Service Account JWT)
     */
    @Synchronized
    fun getAccessToken(context: Context): String? {
        val now = System.currentTimeMillis() / 1000
        if (cachedToken != null && now < tokenExpiryEpoch - 60) {
            return cachedToken
        }

        val jsonStr = getServiceAccountJson(context) ?: return null
        try {
            val json = JSONObject(jsonStr)

            // Phương án 1: OAuth 2.0 Refresh Token
            if (json.has("refresh_token") && json.has("client_id") && json.has("client_secret")) {
                val clientId = json.getString("client_id")
                val clientSecret = json.getString("client_secret")
                val refreshToken = json.getString("refresh_token")

                val url = URL(TOKEN_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "grant_type=" + URLEncoder.encode("refresh_token", "UTF-8") +
                        "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                        "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") +
                        "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")

                conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val tokenObj = JSONObject(res)
                    val token = tokenObj.getString("access_token")
                    val expiresIn = tokenObj.optLong("expires_in", 3600)
                    cachedToken = token
                    tokenExpiryEpoch = now + expiresIn
                    return token
                }
            }

            // Phương án 2: Service Account RS256 JWT
            if (json.has("private_key") && json.has("client_email")) {
                val clientEmail = json.getString("client_email")
                val rawPrivateKey = json.getString("private_key")

                val header = JSONObject().apply {
                    put("alg", "RS256")
                    put("typ", "JWT")
                }
                val payload = JSONObject().apply {
                    put("iss", clientEmail)
                    put("scope", "https://www.googleapis.com/auth/drive")
                    put("aud", TOKEN_URL)
                    put("exp", now + 3600)
                    put("iat", now)
                }

                val headerB64 = base64Url(header.toString().toByteArray(Charsets.UTF_8))
                val payloadB64 = base64Url(payload.toString().toByteArray(Charsets.UTF_8))
                val signingInput = "$headerB64.$payloadB64"

                val cleanKey = rawPrivateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s+".toRegex(), "")
                val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
                val keySpec = PKCS8EncodedKeySpec(keyBytes)
                val kf = KeyFactory.getInstance("RSA")
                val privateKey = kf.generatePrivate(keySpec)

                val signer = Signature.getInstance("SHA256withRSA")
                signer.initSign(privateKey)
                signer.update(signingInput.toByteArray(Charsets.UTF_8))
                val sigBytes = signer.sign()
                val sigB64 = base64Url(sigBytes)

                val jwt = "$signingInput.$sigB64"

                val url = URL(TOKEN_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
                        "&assertion=" + URLEncoder.encode(jwt, "UTF-8")

                conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val tokenObj = JSONObject(res)
                    val token = tokenObj.getString("access_token")
                    val expiresIn = tokenObj.optLong("expires_in", 3600)
                    cachedToken = token
                    tokenExpiryEpoch = now + expiresIn
                    return token
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun base64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
    }

    /**
     * Tìm ID thư mục theo tên (ví dụ: SubAI_Queue, SubAI_Done)
     */
    fun findFolderId(context: Context, folderName: String): String? {
        val token = getAccessToken(context) ?: return null
        val query = URLEncoder.encode("name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8")
        val url = URL("$DRIVE_API_BASE/files?q=$query&fields=files(id,name)")

        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode == 200) {
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            val files = JSONObject(res).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    /**
     * Lấy danh sách video đang nằm trong thư mục SubAI_Queue kèm metadata
     */
    fun listQueueVideos(context: Context): List<DriveVideoItem> {
        val token = getAccessToken(context) ?: return emptyList()
        val queueFolderId = findFolderId(context, "SubAI_Queue") ?: return emptyList()

        val list = mutableListOf<DriveVideoItem>()
        val query = URLEncoder.encode("'$queueFolderId' in parents and trashed = false", "UTF-8")
        val url = URL("$DRIVE_API_BASE/files?q=$query&fields=files(id,name,size,description,modifiedTime,mimeType)&pageSize=50")

        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (conn.responseCode == 200) {
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            val files = JSONObject(res).optJSONArray("files") ?: JSONArray()

            val jsonMetadataMap = mutableMapOf<String, JSONObject>()
            val videoFiles = mutableListOf<JSONObject>()

            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name")
                if (name.endsWith(".json")) {
                    val jsonContent = downloadTextContent(token, f.getString("id"))
                    if (jsonContent != null) {
                        try {
                            val metaObj = JSONObject(jsonContent)
                            val vName = metaObj.optString("video_file_name")
                            if (vName.isNotEmpty()) {
                                jsonMetadataMap[vName] = metaObj
                            }
                        } catch (ignored: Exception) {}
                    }
                } else if (name.endsWith(".mp4") || f.optString("mimeType").startsWith("video/")) {
                    videoFiles.add(f)
                }
            }

            for (vf in videoFiles) {
                val fileId = vf.getString("id")
                val fileName = vf.getString("name")
                val fileSize = vf.optLong("size", 0L)
                val desc = vf.optString("description", "")
                val modTime = vf.optString("modifiedTime", "")

                val meta = jsonMetadataMap[fileName]
                val title = meta?.optString("title")?.takeIf { it.isNotEmpty() } ?: fileName.removeSuffix(".mp4")
                val caption = meta?.optString("caption")?.takeIf { it.isNotEmpty() } ?: desc.ifEmpty { title }
                val hashtags = meta?.optString("hashtags")?.takeIf { it.isNotEmpty() } ?: "#trending #xuhuong #subai"
                val targetClone = meta?.optString("target_clone")?.takeIf { it.isNotEmpty() } ?: "clone1"
                val suggestedTime = meta?.optString("schedule_time") ?: ""

                list.add(
                    DriveVideoItem(
                        fileId = fileId,
                        fileName = fileName,
                        fileSize = fileSize,
                        title = title,
                        caption = caption,
                        hashtags = hashtags,
                        targetClone = targetClone,
                        suggestedTime = suggestedTime,
                        modifiedTime = modTime
                    )
                )
            }
        }
        return list
    }

    private fun downloadTextContent(token: String, fileId: String): String? {
        try {
            val url = URL("$DRIVE_API_BASE/files/$fileId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode == 200) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (ignored: Exception) {}
        return null
    }

    /**
     * Tải file video trực tiếp từ Google Drive về bộ nhớ tạm (Cache) trên điện thoại
     */
    fun downloadVideoToFile(
        context: Context,
        fileId: String,
        targetFile: File,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean {
        val token = getAccessToken(context) ?: return false
        val url = URL("$DRIVE_API_BASE/files/$fileId?alt=media")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        if (conn.responseCode == 200) {
            val totalBytes = conn.contentLength.toLong()
            targetFile.parentFile?.mkdirs()
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0 && progressCallback != null) {
                            val percent = (downloadedBytes * 100 / totalBytes).toInt()
                            progressCallback(percent)
                        }
                    }
                }
            }
            return targetFile.exists() && targetFile.length() > 0
        }
        return false
    }

    /**
     * Chuyển video sang thư mục 'SubAI_Done' trên Google Drive sau khi đăng thành công
     */
    fun markVideoAsDone(context: Context, fileId: String): Boolean {
        val token = getAccessToken(context) ?: return false
        val queueFolderId = findFolderId(context, "SubAI_Queue")
        val doneFolderId = findFolderId(context, "SubAI_Done") ?: return false

        val removeParents = if (queueFolderId != null) "&removeParents=$queueFolderId" else ""
        val url = URL("$DRIVE_API_BASE/files/$fileId?addParents=$doneFolderId$removeParents")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn.responseCode in 200..299
    }
}
