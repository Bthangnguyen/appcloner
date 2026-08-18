package com.cloner.app.util

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * AppClonerFileProvider: ContentProvider siêu nhẹ cung cấp quyền đọc file APK cho hệ thống Android cài đặt.
 */
class AppClonerFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols, 1)
        val row = cursor.newRow()
        for (col in cols) {
            if (OpenableColumns.DISPLAY_NAME == col) {
                row.add(col, file.name)
            } else if (OpenableColumns.SIZE == col) {
                row.add(col, file.length())
            } else {
                row.add(col, null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        val path = uri.path?.lowercase() ?: ""
        return when {
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".apk") -> "application/vnd.android.package-archive"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            else -> "video/*"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${uri.path}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getFileForUri(uri: Uri): File {
        val path = uri.path ?: throw FileNotFoundException("Invalid URI: $uri")
        // Cho phép đọc file từ đường dẫn
        return File(path)
    }

    companion object {
        fun getUriForFile(file: File): Uri {
            return Uri.parse("content://com.cloner.app.fileprovider${file.absolutePath}")
        }
    }
}
