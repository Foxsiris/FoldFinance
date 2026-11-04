package com.foldfinance.app.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Файл бэкапа не удалось прочитать. Данные на устройстве не тронуты. */
class BackupFileException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Нет сигнатуры `FFBK`: это не файл бэкапа FoldFinance. */
        NOT_A_BACKUP,

        /** Файл другой (более новой) версии формата. */
        UNSUPPORTED_VERSION,

        /** Проверка подлинности GCM не прошла: неверный пароль или файл изменён/повреждён. */
        WRONG_PASSWORD,

        /** Файл обрезан или содержимое после расшифровки не читается. */
        CORRUPTED,
    }
}

/**
 * Шифрование файла бэкапа паролем пользователя.
 *
 * Формат (версия 1), все поля подряд:
 * ```
 * magic   4 байта  "FFBK" (ASCII)
 * version 1 байт   0x01
 * salt    16 байт  случайная соль PBKDF2
 * iv      12 байт  случайный nonce AES-GCM
 * data    …        AES-256-GCM(шифротекст ‖ тег 128 бит)
 * ```
 * Ключ: PBKDF2WithHmacSHA256(пароль, salt, [ITERATIONS] итераций, 256 бит). Заголовок
 * (magic…iv, 33 байта) — дополнительные аутентифицированные данные (AAD): подмена версии,
 * соли или IV ломает проверку тега. Параметры KDF зафиксированы версией формата.
 *
 * Пароль не хранится: массив символов затирается вызывающим ([BackupManager]); ключ и
 * промежуточные массивы затираются здесь.
 */
class BackupCipher(private val random: SecureRandom = SecureRandom()) {

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val header = header(salt, iv)
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            val sealed = cipher.doFinal(plain)
            return ByteBuffer.allocate(header.size + sealed.size).put(header).put(sealed).array()
        } finally {
            key.fill(0)
        }
    }

    /** @throws BackupFileException файл не наш, другой версии, неверный пароль или повреждён. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < MAGIC.size || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupFileException(BackupFileException.Reason.NOT_A_BACKUP)
        }
        if (file.size <= MAGIC.size) throw BackupFileException(BackupFileException.Reason.CORRUPTED)
        if (file[MAGIC.size] != VERSION) throw BackupFileException(BackupFileException.Reason.UNSUPPORTED_VERSION)
        if (file.size < HEADER_SIZE + TAG_BITS / 8) throw BackupFileException(BackupFileException.Reason.CORRUPTED)

        val header = file.copyOfRange(0, HEADER_SIZE)
        val salt = header.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_SIZE)
        val iv = header.copyOfRange(MAGIC.size + 1 + SALT_SIZE, HEADER_SIZE)
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            return cipher.doFinal(file, HEADER_SIZE, file.size - HEADER_SIZE)
        } catch (e: AEADBadTagException) {
            throw BackupFileException(BackupFileException.Reason.WRONG_PASSWORD, e)
        } catch (e: GeneralSecurityException) {
            throw BackupFileException(BackupFileException.Reason.CORRUPTED, e)
        } finally {
            key.fill(0)
        }
    }

    private fun header(salt: ByteArray, iv: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(VERSION).put(salt).put(iv).array()

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        val MAGIC: ByteArray = byteArrayOf('F'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
        const val VERSION: Byte = 1
        const val SALT_SIZE: Int = 16
        const val IV_SIZE: Int = 12
        const val HEADER_SIZE: Int = 4 + 1 + SALT_SIZE + IV_SIZE
        const val ITERATIONS: Int = 600_000
        const val KEY_BITS: Int = 256
        const val TAG_BITS: Int = 128
        const val KDF: String = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
