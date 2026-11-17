package com.quntodo.app.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageStore(private val context: Context) {

    private fun imagesDir(): File = File(context.filesDir, "images").also { it.mkdirs() }

    suspend fun copyFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val target = File(imagesDir(), "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    fun deletePath(path: String) {
        runCatching { File(path).delete() }
    }

    fun deletePaths(paths: List<String>) {
        paths.forEach(::deletePath)
    }

    fun fileUri(path: String): Uri? = runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path)
        )
    }.getOrNull()

    fun exists(path: String): Boolean = runCatching { File(path).isFile }.getOrDefault(false)
}
