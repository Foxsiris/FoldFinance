package com.foldfinance.app.backup

import com.foldfinance.core.data.backup.BackupSnapshot
import com.foldfinance.core.data.backup.InvalidBackupException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Снимок БД ↔ байты файла бэкапа: JSON (kotlinx.serialization) → gzip → [BackupCipher].
 *
 * Сжатие до шифрования безопасно для файла «в покое» и уменьшает его в разы (тексты SMS).
 * Расшифрованные массивы затираются сразу после разбора.
 */
class BackupCodec(private val cipher: BackupCipher = BackupCipher()) {

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(snapshot: BackupSnapshot, password: CharArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { json.encodeToStream(BackupSnapshot.serializer(), snapshot, it) }
        val plain = buffer.toByteArray()
        try {
            return cipher.encrypt(plain, password)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * @throws BackupFileException не наш файл, другая версия, неверный пароль, повреждение.
     * @throws InvalidBackupException расшифровалось, но это не снимок FoldFinance (нет полей).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(file: ByteArray, password: CharArray): BackupSnapshot {
        val plain = cipher.decrypt(file, password)
        try {
            return GZIPInputStream(ByteArrayInputStream(plain)).use { json.decodeFromStream(BackupSnapshot.serializer(), it) }
        } catch (e: SerializationException) {
            // Обязательного поля нет или тип не тот: не снимок FoldFinance (или испорчен до шифрования).
            throw InvalidBackupException(InvalidBackupException.Reason.INVALID_CONTENT, "snapshot structure is invalid")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException(InvalidBackupException.Reason.INVALID_CONTENT, "snapshot structure is invalid")
        } catch (e: IOException) {
            throw BackupFileException(BackupFileException.Reason.CORRUPTED, e)
        } finally {
            plain.fill(0)
        }
    }

    private companion object {
        /** Неизвестные поля пропускаются, чтобы проверка версии дала понятную ошибку. */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
