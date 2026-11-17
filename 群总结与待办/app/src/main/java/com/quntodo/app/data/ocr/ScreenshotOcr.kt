package com.quntodo.app.data.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用 ML Kit 的离线中文模型识别聊天截图里的文字。
 * 会过滤状态栏/按钮等界面噪声，并把同一气泡里被换行拆开的文字重新拼起来。
 */
class ScreenshotOcr(private val context: Context) {

    private data class Block(val text: String, val rect: Rect)

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognizeBlocks(file: File): List<String> = suspendCancellableCoroutine { continuation ->
        if (!file.isFile) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val image = runCatching {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        }.getOrElse {
            continuation.resumeWithException(it)
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { text ->
                val blocks = text.textBlocks.mapNotNull { block ->
                    val content = block.text.trim()
                    val rect = block.boundingBox
                    if (content.isEmpty() || rect == null) return@mapNotNull null
                    if (isUiNoise(content) || isStandaloneName(content)) return@mapNotNull null
                    Block(content, rect)
                }.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))

                continuation.resume(mergeWrappedLines(blocks).take(40))
            }
            .addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    private fun mergeWrappedLines(blocks: List<Block>): List<String> {
        val result = mutableListOf<String>()
        var previous: Block? = null

        for (block in blocks) {
            val last = previous
            if (last != null &&
                result.isNotEmpty() &&
                !endsWithTerminal(result.last()) &&
                verticalGap(last.rect, block.rect) in -10..60 &&
                horizontalOverlap(last.rect, block.rect) >= 0.5f
            ) {
                result[result.lastIndex] = result.last() + block.text
            } else {
                result += block.text
            }
            previous = block
        }
        return result
    }

    private fun endsWithTerminal(text: String): Boolean {
        val last = text.trim().lastOrNull() ?: return true
        return last in "。！？!?；;"
    }

    private fun verticalGap(previous: Rect, current: Rect): Int = current.top - previous.bottom

    private fun horizontalOverlap(previous: Rect, current: Rect): Float {
        val left = maxOf(previous.left, current.left)
        val right = minOf(previous.right, current.right)
        val overlap = (right - left).coerceAtLeast(0)
        val narrower = minOf(previous.width(), current.width()).coerceAtLeast(1)
        return overlap.toFloat() / narrower
    }

    private fun isStandaloneName(text: String): Boolean {
        val compact = text.replace(Regex("\\s+"), "")
        if (compact.length > 6) return false
        if (compact.any { it.isDigit() }) return false
        if (compact.any { it in "。！？!?；;，,、：:" }) return false
        if (ACTION_HINTS.any { compact.contains(it) }) return false
        return true
    }

    private fun isUiNoise(text: String): Boolean {
        val value = text.trim()
        if (value.length < 2) return true

        // 状态栏时间、电量、网速
        if (Regex("^\\d{1,2}:\\d{2}$").matches(value)) return true
        if (Regex(".*\\d{1,2}:\\d{2}\\s*$").matches(value) && value.length <= 16) return true
        if (Regex("^.*\\d+\\s*%.*$").matches(value) && value.length <= 16) return true
        if (Regex("^.*\\d+(\\.\\d+)?\\s*(KB|MB|GB)/s$").matches(value)) return true

        // 群标题、输入框、按钮
        if (Regex("^.{0,20}\\(\\d+\\)$").matches(value)) return true
        if (Regex("^.{0,20}\\d+\\s*[\\)）]$").matches(value)) return true
        if (value.contains("输入消息") || value.contains("按住") || value.contains("发送")) return true

        // OCR 把发送人识别成「名字 / 名字」的残片
        if (Regex("^[\\u4e00-\\u9fa5A-Za-z]{1,8}\\s*/\\s*[\\u4e00-\\u9fa5A-Za-z]{1,8}$").matches(value)) {
            return true
        }

        if (value in UI_WORDS) return true

        // 纯符号、纯数字
        if (Regex("^[\\p{Punct}\\s]+$").matches(value)) return true
        if (Regex("^\\d+$").matches(value)) return true

        return false
    }

    private companion object {
        val ACTION_HINTS = listOf(
            "交", "报名", "填", "截止", "请", "记得", "带", "开会", "会议", "集合",
            "参加", "领", "缴费", "回复", "确认", "需要", "务必", "提前", "准备",
            "活动", "材料", "作业", "表", "时间", "地点"
        )

        val UI_WORDS = setOf(
            "发送", "取消", "确定", "返回", "更多", "输入", "按住说话", "按住 说话",
            "QQ", "微信", "表情", "相册", "拍摄", "视频通话", "语音通话", "查看全部"
        )
    }
}
