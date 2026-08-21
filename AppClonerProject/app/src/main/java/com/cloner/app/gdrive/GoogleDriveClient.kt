package com.cloner.app.gdrive

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val modifiedTime: String,
    val metadataFileId: String = "",
    val sourceParentId: String = "",
    val jobId: String = "",
    val schemaVersion: Int = 0
)

/**
 * GoogleDriveClient: Kết nối Google Drive REST API v3 trực tiếp từ Android
 * Hỗ trợ cả 2 phương thức:
 * 1. OAuth 2.0 Token JSON (client_id, client_secret, refresh_token)
 * 2. Service Account JSON (client_email, private_key)
 */
object GoogleDriveClient {

    private const val PREFS_NAME = "gdrive_config"
    private const val KEY_AUTH_JSON = "auth_json_encrypted"
    private const val LEGACY_KEY_AUTH_JSON = "auth_json"
    private const val KEYSTORE_ALIAS = "gdrive_credentials_key"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
    private const val MAX_SUPPORTED_QUEUE_SCHEMA_VERSION = 1

    private var cachedToken: String? = null
    private var tokenExpiryEpoch: Long = 0L

    fun saveServiceAccountJson(context: Context, jsonStr: String): Boolean {
        val encrypted = try {
            encryptCredential(jsonStr)
        } catch (_: Exception) {
            null
        } ?: return false

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_KEY_AUTH_JSON)
            .putString(KEY_AUTH_JSON, encrypted)
            .apply()
        cachedToken = null
        tokenExpiryEpoch = 0L
        return true
    }

    fun getServiceAccountJson(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_AUTH_JSON, null)
        if (!encrypted.isNullOrEmpty()) {
            try {
                return decryptCredential(encrypted)
            } catch (_: Exception) {
                // Có thể là dữ liệu plaintext từ bản APK cũ; xử lý bên dưới để migrate.
            }
        }

        val legacy = prefs.getString(LEGACY_KEY_AUTH_JSON, null)
            ?: encrypted?.takeIf { isValidCredentialJson(it) }
        if (!legacy.isNullOrEmpty() && isValidCredentialJson(legacy)) {
            // Migrate một lần từ bản cũ sang Android Keystore.
            saveServiceAccountJson(context, legacy)
            return legacy
        }
        return null
    }

    private fun getCredentialKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptCredential(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getCredentialKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decryptCredential(value: String): String {
        val allBytes = Base64.decode(value, Base64.DEFAULT)
        require(allBytes.size > 12) { "Credential payload không hợp lệ" }
        val iv = allBytes.copyOfRange(0, 12)
        val encrypted = allBytes.copyOfRange(12, allBytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getCredentialKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    fun hasCredentials(context: Context): Boolean {
        val json = getServiceAccountJson(context) ?: return false
        return isValidCredentialJson(json)
    }

    fun isValidCredentialJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            (json.optString("refresh_token").isNotBlank() &&
                    json.optString("client_id").isNotBlank() &&
                    json.optString("client_secret").isNotBlank()) ||
                    (json.optString("private_key").isNotBlank() &&
                            json.optString("client_email").isNotBlank())
        } catch (_: Exception) {
            false
        }
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
     * Lấy toàn bộ video trong SubAI_Queue và các thư mục con, kèm metadata.
     * Metadata được ghép theo video_file_id trước, rồi mới fallback theo tên file.
     */
    fun listQueueVideos(context: Context): List<DriveVideoItem> {
        val token = getAccessToken(context) ?: return emptyList()
        val queueFolderId = findFolderId(context, "SubAI_Queue") ?: return emptyList()

        val allFiles = mutableListOf<JSONObject>()
        val foldersToVisit = ArrayDeque<String>()
        val visitedFolders = mutableSetOf<String>()
        foldersToVisit.add(queueFolderId)

        while (foldersToVisit.isNotEmpty()) {
            val folderId = foldersToVisit.removeFirst()
            if (!visitedFolders.add(folderId)) continue
            val children = listFilesInFolder(token, folderId)
            allFiles.addAll(children)
            children.filter {
                it.optString("mimeType") == "application/vnd.google-apps.folder"
            }.forEach { foldersToVisit.add(it.optString("id")) }
        }

        data class MetadataRecord(val driveFileId: String, val json: JSONObject)
        val metadataByVideoId = mutableMapOf<String, MetadataRecord>()
        val metadataByName = mutableMapOf<String, MetadataRecord>()
        val videoFiles = mutableListOf<JSONObject>()

        for (file in allFiles) {
            val name = file.optString("name")
            if (name.endsWith(".json", ignoreCase = true)) {
                val content = downloadTextContent(token, file.optString("id"))
                if (!content.isNullOrBlank()) {
                    try {
                        val meta = JSONObject(content)
                        val record = MetadataRecord(file.optString("id"), meta)
                        val videoId = meta.optString("video_file_id")
                        val videoName = meta.optString("video_file_name")
                        if (videoId.isNotBlank() && videoId != "uploaded") {
                            metadataByVideoId.putIfAbsent(videoId, record)
                        }
                        if (videoName.isNotBlank()) {
                            metadataByName.putIfAbsent(videoName, record)
                        }
                    } catch (_: Exception) {
                        // Ignore malformed sidecars; the video remains visible with safe defaults.
                    }
                }
            } else if (name.endsWith(".mp4", ignoreCase = true) ||
                file.optString("mimeType").startsWith("video/")) {
                videoFiles.add(file)
            }
        }

        return videoFiles.map { video ->
            val fileId = video.optString("id")
            val fileName = video.optString("name")
            val record = metadataByVideoId[fileId] ?: metadataByName[fileName]
            val rawMeta = record?.json
            val schemaVersion = rawMeta?.optInt("schema_version", 0) ?: 0
            val meta = rawMeta?.takeIf { schemaVersion <= MAX_SUPPORTED_QUEUE_SCHEMA_VERSION }
            val title = meta?.optString("title")?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.', fileName)
            val description = video.optString("description", "")
            val caption = meta?.optString("caption")?.takeIf { it.isNotBlank() }
                ?: description.ifBlank { title }
            val hashtags = meta?.optString("hashtags")?.takeIf { it.isNotBlank() }
                ?: "#trending #xuhuong #subai"
            val targetClone = meta?.optString("target_clone")?.takeIf { it.isNotBlank() }
                ?: "clone1"
            val suggestedTime = meta?.optString("schedule_time") ?: ""
            val parentId = video.optJSONArray("parents")?.optString(0).orEmpty()

            DriveVideoItem(
                fileId = fileId,
                fileName = fileName,
                fileSize = video.optLong("size", 0L),
                title = title,
                caption = caption,
                hashtags = hashtags,
                targetClone = targetClone,
                suggestedTime = suggestedTime,
                modifiedTime = video.optString("modifiedTime", ""),
                metadataFileId = record?.driveFileId.orEmpty(),
                sourceParentId = parentId,
                jobId = meta?.optString("job_id")?.takeIf { it.isNotBlank() }.orEmpty(),
                schemaVersion = schemaVersion
            )
        }.distinctBy { it.fileId }
    }

    private fun listFilesInFolder(token: String, folderId: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var pageToken: String? = null
        do {
            val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
            val page = pageToken?.let { "&pageToken=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val url = URL(
                "$DRIVE_API_BASE/files?q=$query" +
                        "&fields=nextPageToken,files(id,name,size,description,modifiedTime,mimeType,parents)" +
                        "&orderBy=modifiedTime%20desc&pageSize=1000$page"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode != 200) return result
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(body)
            val files = response.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) result.add(files.getJSONObject(i))
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return result
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
    fun markVideoAsDone(context: Context, fileId: String, metadataFileId: String = ""): Boolean {
        val token = getAccessToken(context) ?: return false
        val doneFolderId = findFolderId(context, "SubAI_Done") ?: return false
        val videoMoved = moveFileToDone(token, fileId, doneFolderId)
        val metadataMoved = metadataFileId.isBlank() || moveFileToDone(token, metadataFileId, doneFolderId)
        return videoMoved && metadataMoved
    }

    private fun moveFileToDone(token: String, fileId: String, doneFolderId: String): Boolean {
        if (fileId.isBlank()) return false
        return try {
            val getUrl = URL("$DRIVE_API_BASE/files/$fileId?fields=parents")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.setRequestProperty("Authorization", "Bearer $token")
            if (getConn.responseCode != 200) return false
            val parents = JSONObject(getConn.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("parents")
            val parentIds = mutableListOf<String>()
            if (parents != null) {
                for (i in 0 until parents.length()) parentIds.add(parents.optString(i))
            }
            if (parentIds.contains(doneFolderId)) return true
            val remove = parentIds.filter { it.isNotBlank() && it != doneFolderId }.joinToString(",")
            val query = "addParents=${URLEncoder.encode(doneFolderId, "UTF-8")}" +
                    if (remove.isNotBlank()) "&removeParents=${URLEncoder.encode(remove, "UTF-8")}" else ""
            val url = URL("$DRIVE_API_BASE/files/$fileId?$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tự động quét các bản TikTok Clone đang cài trên máy và đồng bộ lên file device_clones.json trên Drive
     */
    fun syncInstalledClonesToCloud(context: Context): Boolean {
        val token = getAccessToken(context) ?: return false
        val queueFolderId = findFolderId(context, "SubAI_Queue") ?: return false

        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            val clonesArray = JSONArray()
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty()
            val lastSeenAt = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
            ).format(Date())

            for (app in installedApps) {
                val pkg = app.packageName
                if (pkg.startsWith("com.ss.android.ugc.trill")) {
                    val label = pm.getApplicationLabel(app).toString()
                    val cloneId = if (pkg == "com.ss.android.ugc.trill") "base" else pkg.substringAfter("com.ss.android.ugc.trill.")
                    val obj = JSONObject().apply {
                        put("id", cloneId)
                        put("name", label)
                        put("package", pkg)
                        put("device_id", deviceId)
                        put("last_seen_at", lastSeenAt)
                    }
                    clonesArray.put(obj)
                }
            }

            val jsonContent = clonesArray.toString(2)
            val existing = listFilesInFolder(token, queueFolderId)
                .firstOrNull { it.optString("name") == "device_clones.json" }
            val url = if (existing != null) {
                URL("https://www.googleapis.com/upload/drive/v3/files/${existing.optString("id")}?uploadType=media")
            } else {
                URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            }
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = if (existing != null) "PATCH" else "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            if (existing != null) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.outputStream.use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }
            } else {
                val boundary = "-------SubAIClonesBoundary"
                val body = ("--$boundary\r\n" +
                        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        "{\"name\": \"device_clones.json\", \"parents\": [\"$queueFolderId\"]}\r\n" +
                        "--$boundary\r\n" +
                        "Content-Type: application/json\r\n\r\n" +
                        jsonContent + "\r\n--$boundary--\r\n").toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                conn.setRequestProperty("Content-Length", body.size.toString())
                conn.outputStream.use { it.write(body) }
            }

            return conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
