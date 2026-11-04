package com.foldfinance.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.foldfinance.app.widget.FinanceWidgetUpdater
import com.foldfinance.core.data.backup.BackupSnapshot
import com.foldfinance.core.data.backup.BackupValidator
import com.foldfinance.core.data.backup.DatabaseBackup
import com.foldfinance.core.data.backup.InvalidBackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Почему экспорт или импорт не удался. Для UI — без подробностей содержимого. */
enum class BackupError {
    WRONG_PASSWORD,
    NOT_A_BACKUP,
    NEWER_VERSION,
    CORRUPTED,
    INVALID_CONTENT,
    TOO_LARGE,
    IO,
}

/** Результат операции бэкапа. */
sealed interface BackupResult {
    /** @property transactions сколько операций в файле. */
    data class Success(val transactions: Int) : BackupResult

    data class Failure(val error: BackupError) : BackupResult
}

/**
 * Зашифрованный экспорт/импорт всей БД в файл, выбранный через SAF.
 *
 * - Экспорт: снимок ([DatabaseBackup.snapshot]) → [BackupCodec] → запись в `uri` (с усечением).
 * - Импорт: чтение (не больше [MAX_FILE_BYTES]) → расшифровка → проверка ([BackupValidator]) →
 *   замена данных одной транзакцией. Любая ошибка до транзакции или внутри неё оставляет
 *   данные на устройстве прежними.
 *
 * [password] затирается (`'\u0000'`) в любом исходе; пароль нигде не сохраняется.
 * Ничего не логирует.
 */
@Singleton
class BackupManager internal constructor(
    private val context: Context,
    private val database: DatabaseBackup,
    private val codec: BackupCodec,
    private val onDataReplaced: (Context) -> Unit,
) {
    @Inject constructor(@ApplicationContext context: Context, database: DatabaseBackup) :
        this(context, database, BackupCodec(), FinanceWidgetUpdater::requestUpdate)

    suspend fun export(uri: Uri, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.snapshot()
            val bytes = codec.encode(snapshot, password)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
            out.use { it.write(bytes) }
            BackupResult.Success(snapshot.transactionCount)
        } catch (e: IOException) {
            BackupResult.Failure(BackupError.IO)
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupError.IO)
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun import(uri: Uri, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
            val bytes = input.use { it.readLimited(MAX_FILE_BYTES) } ?: return@withContext BackupResult.Failure(BackupError.TOO_LARGE)
            val snapshot = decode(bytes, password)
            database.restore(snapshot)
            onDataReplaced(context)
            BackupResult.Success(snapshot.transactionCount)
        } catch (e: BackupFileException) {
            BackupResult.Failure(e.reason.toError())
        } catch (e: InvalidBackupException) {
            BackupResult.Failure(e.reason.toError())
        } catch (e: android.database.SQLException) {
            // Ограничение БД при записи: транзакция откатилась, данные прежние.
            BackupResult.Failure(BackupError.INVALID_CONTENT)
        } catch (e: IOException) {
            BackupResult.Failure(BackupError.IO)
        } catch (e: SecurityException) {
            BackupResult.Failure(BackupError.IO)
        } finally {
            password.fill('\u0000')
        }
    }

    /** Расшифровать и проверить, ничего не меняя в БД (для тестов и предпросмотра). */
    fun decode(bytes: ByteArray, password: CharArray): BackupSnapshot =
        codec.decode(bytes, password).also(BackupValidator::validate)

    /** Пользователь передумал после выбора места: удалить пустой созданный SAF документ. */
    fun discardCreated(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            // Провайдер не умеет удалять — останется пустой файл; не критично.
        }
    }

    companion object {
        /** Защита от чтения в память гигантского чужого файла. */
        const val MAX_FILE_BYTES: Int = 64 * 1024 * 1024

        /** Имя файла по умолчанию в окне сохранения. */
        fun suggestedFileName(date: java.time.LocalDate): String = "foldfinance-$date.ffbk"

        const val MIME_TYPE: String = "application/octet-stream"
    }
}

private fun InputStream.readLimited(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private fun BackupFileException.Reason.toError(): BackupError = when (this) {
    BackupFileException.Reason.NOT_A_BACKUP -> BackupError.NOT_A_BACKUP
    BackupFileException.Reason.UNSUPPORTED_VERSION -> BackupError.NEWER_VERSION
    BackupFileException.Reason.WRONG_PASSWORD -> BackupError.WRONG_PASSWORD
    BackupFileException.Reason.CORRUPTED -> BackupError.CORRUPTED
}

private fun InvalidBackupException.Reason.toError(): BackupError = when (this) {
    InvalidBackupException.Reason.FOREIGN_FORMAT -> BackupError.NOT_A_BACKUP
    InvalidBackupException.Reason.NEWER_VERSION -> BackupError.NEWER_VERSION
    InvalidBackupException.Reason.INVALID_CONTENT -> BackupError.INVALID_CONTENT
}
