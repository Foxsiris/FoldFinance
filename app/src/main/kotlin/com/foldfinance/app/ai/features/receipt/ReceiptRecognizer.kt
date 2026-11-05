package com.foldfinance.app.ai.features.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.core.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/** Result of reading a receipt photo. */
sealed interface ReceiptResult {
    data class Read(val scan: ReceiptScan) : ReceiptResult

    /** The model answered but nothing usable came out (blurry photo, not a receipt). */
    data object Unreadable : ReceiptResult

    data class Failed(val reason: String) : ReceiptResult
}

/**
 * Receipts (п.5): photo → model (FULL, with the image) → JSON lines → [ReceiptParser].
 * The model copies names and line sums and picks a category from the user's list; it never adds
 * anything up — totals and the comparison with the operation are Kotlin ([ReceiptSplitPlanner]).
 */
class ReceiptRecognizer @Inject constructor(
    private val ai: LocalAi,
) {
    suspend fun recognize(image: Bitmap, categories: List<Category>): ReceiptResult {
        val request = AiRequest(
            prompt = "Прочитай чек на фото.",
            system = systemPrompt(categories),
            images = listOf(image),
            tier = AiTier.FULL,
            temperature = 0f,
            maxOutputTokens = 1024,
            jsonOnly = true,
        )
        return when (val response = ai.generate(request)) {
            is AiResponse.Failed -> ReceiptResult.Failed(response.reason)
            is AiResponse.Text -> ReceiptParser.parse(response.text, categories)?.let { ReceiptResult.Read(it) } ?: ReceiptResult.Unreadable
        }
    }

    companion object {
        fun systemPrompt(categories: List<Category>): String = buildString {
            appendLine("Ты читаешь фото кассового чека и переписываешь позиции. Ничего не складывай и не умножай.")
            appendLine("Ответ — только JSON: {\"items\":[{\"name\":\"...\",\"amount\":\"...\",\"category\":\"...\"}],\"total\":\"...\"}.")
            appendLine("amount — сумма строки ровно как напечатана в чеке (например \"189.90\"); если не видно — null.")
            appendLine("total — напечатанное «ИТОГО», если есть, иначе null. Скидки и пакеты — отдельными позициями только если напечатаны суммой.")
            append("category — одно из точных названий: ${categories.joinToString(", ") { "\"${it.name}\"" }}; если не подходит ни одно — null.")
        }
    }
}

/**
 * Photo files of receipts: camera shots go to `cacheDir/receipts` (shared with the camera app
 * through `FileProvider`, no CAMERA permission) and are deleted right after reading.
 */
class ReceiptImages @Inject constructor(@ApplicationContext private val context: Context) {

    /** A new empty file for `ActivityResultContracts.TakePicture`. */
    fun newCaptureFile(): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File.createTempFile("receipt_", ".jpg", dir)
    }

    fun authority(): String = context.packageName + AUTHORITY_SUFFIX

    /** Decodes [uri] scaled so the long side is at most [maxSide] px; software bitmap for the model. */
    suspend fun load(uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longSide = max(w, h)
                if (longSide > maxSide) {
                    val scale = maxSide.toFloat() / longSide
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    /** Removes every captured receipt photo (privacy: nothing stays on disk). */
    suspend fun clearCaptures() = withContext(Dispatchers.IO) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
        Unit
    }

    companion object {
        const val DIR = "receipts"
        const val AUTHORITY_SUFFIX = ".receipts"
        const val MAX_SIDE = 1600
    }
}
