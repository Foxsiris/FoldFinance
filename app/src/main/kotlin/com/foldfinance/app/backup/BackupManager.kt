package com.foldfinance.app.backup

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.DocumentsContract
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.app.widget.FinanceWidgetUpdater
import com.foldfinance.core.data.backup.BackupSnapshot
import com.foldfinance.core.data.backup.BackupValidator
import com.foldfinance.core.data.backup.DatabaseBackup
import com.foldfinance.core.data.backup.InvalidBackupException
import com.foldfinance.core.data.settings.IngestSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Почему экспорт или импорт не удался. Для UI — без подробностей содержимого.
 * [IO] — также неудачная проверка записанного файла и непредвиденная внутренняя ошибка.
 */
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
 * - Экспорт: снимок ([DatabaseBackup.snapshot]) → [BackupCodec] → запись в `uri` (с усечением) →
 *   файл перечитывается и расшифровывается тем же паролем; не сошлось — [BackupError.IO].
 * - Импорт: чтение (не больше [MAX_FILE_BYTES]) → расшифровка → проверка ([BackupValidator]) →
 *   замена данных одной транзакцией. Любая ошибка до транзакции или внутри неё оставляет
 *   данные на устройстве прежними. После замены — [onDataReplaced] (в проде [AfterRestore]:
 *   сброс курсора SMS, тихий полный скан, виджет).
 * - Ошибки: нарушение ограничения БД при записи снимка — [BackupError.INVALID_CONTENT]; прочие
 *   ошибки SQLite, ввода-вывода и любые непредвиденные — [BackupError.IO]. Методы всегда
 *   возвращают результат (кроме отмены), чтобы статус в UI не залипал в «Выполняется».
 *
 * [password] затирается (`'\u0000'`) в любом исходе; пароль нигде не сохраняется.
 * Ничего не логирует.
 */
@Singleton
class BackupManager internal constructor(
    private val context: Context,
    private val database: DatabaseBackup,
    private val codec: BackupCodec,
    private val onDataReplaced: suspend () -> Unit,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        database: DatabaseBackup,
        settings: IngestSettings,
        scheduler: IngestScheduler,
    ) : this(
        context,
        database,
        BackupCodec(),
        AfterRestore(
            settings = settings,
            startQuietFullScan = { scheduler.startFullScan(quiet = true) },
            refreshWidget = { FinanceWidgetUpdater.requestUpdate(context) },
        )::invoke,
    )

    suspend fun export(uri: Uri, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.snapshot()
            val bytes = codec.encode(snapshot, password)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
            out.use { it.write(bytes) }
            verifyWritten(uri, password, snapshot.transactionCount)
            BackupResult.Success(snapshot.transactionCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Запись, проверка записанного, доступ к файлу или непредвиденная ошибка.
            if (BuildConfig.DEBUG) Timber.w("backup export failed: %s", e.javaClass.simpleName)
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
            afterReplace()
            BackupResult.Success(snapshot.transactionCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFileException) {
            BackupResult.Failure(e.reason.toError())
        } catch (e: InvalidBackupException) {
            BackupResult.Failure(e.reason.toError())
        } catch (e: SQLiteConstraintException) {
            // Снимок нарушает ограничение БД (уникальность, внешний ключ): транзакция откатилась, данные прежние.
            BackupResult.Failure(BackupError.INVALID_CONTENT)
        } catch (e: Exception) {
            // Прочие ошибки SQLite (диск полон, БД занята), ввода-вывода, доступа и непредвиденные:
            // транзакция откатилась, данные прежние; статус не должен залипнуть в «Выполняется».
            if (BuildConfig.DEBUG) Timber.w("backup import failed: %s", e.javaClass.simpleName)
            BackupResult.Failure(BackupError.IO)
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Перечитать записанный файл и расшифровать тем же паролем: провайдер SAF мог записать не всё
     * (облачный, переполненный), а иначе пользователь узнал бы об этом только при восстановлении.
     */
    private fun verifyWritten(uri: Uri, password: CharArray, expectedTransactions: Int) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("written file is unreadable")
        val bytes = input.use { it.readLimited(MAX_FILE_BYTES) } ?: throw IOException("written file is too large")
        val restored = try {
            decode(bytes, password)
        } catch (e: BackupFileException) {
            throw IOException("written file does not decode", e)
        } catch (e: InvalidBackupException) {
            throw IOException("written file does not validate", e)
        }
        if (restored.transactionCount != expectedTransactions) throw IOException("written file is incomplete")
    }

    /**
     * Данные уже заменены: сбой последующих шагов не делает импорт неудачным (курсор SMS
     * сбрасывается первым, следующий скан всё догонит).
     */
    private suspend fun afterReplace() {
        try {
            onDataReplaced()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.w("after-restore step failed: %s", e.javaClass.simpleName)
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

/**
 * Шаги после восстановления из бэкапа: в снимке нет SMS, пришедших после экспорта, а курсор
 * дельта-скана остался от прежних данных — сбросить его и запустить тихий полный скан
 * (идемпотентен: восстановленные SMS не задваиваются). Скан — только при включённом автотрекинге;
 * без разрешения SMS воркер сам ничего не делает, курсор останется сброшенным.
 */
internal class AfterRestore(
    private val settings: IngestSettings,
    private val startQuietFullScan: () -> Unit,
    private val refreshWidget: () -> Unit,
) {
    suspend operator fun invoke() {
        settings.resetLastProcessedSmsId()
        if (settings.autoTrackingEnabled()) startQuietFullScan()
        refreshWidget()
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
