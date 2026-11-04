package com.foldfinance.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ключ БД не удалось получить. Данные без ключа не читаются — пересоздавать ключ молча нельзя. */
class DatabaseKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Шифрование ключа БД ключом-обёрткой (KEK). */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * KEK в Android Keystore: AES-256/GCM, ключ не покидает Keystore.
 * `setUnlockedDeviceRequired` не включаем: SMS и push принимаются и при заблокированном экране.
 */
class KeystoreKeyWrapper(private val alias: String = KEK_ALIAS) : KeyWrapper {

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKek())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return ByteBuffer.allocate(2 + iv.size + cipherText.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(cipherText)
            .array()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(wrapped)
        val version = buffer.get()
        if (version != FORMAT_VERSION) throw GeneralSecurityException("unsupported key file format")
        val iv = ByteArray(buffer.get().toInt() and 0xFF).also(buffer::get)
        val cipherText = ByteArray(buffer.remaining()).also(buffer::get)
        val kek = existingKek() ?: throw GeneralSecurityException("keystore key is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKek(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun getOrCreateKek(): SecretKey = existingKek() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }
        .generateKey()

    companion object {
        const val KEK_ALIAS: String = "foldfinance_db_kek"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}

/**
 * Ключ шифрования SQLCipher: 32 случайных байта из [SecureRandom], хранятся только в
 * зашифрованном виде (через [KeyWrapper]) в приватном файле [keyFile] (в `noBackupFilesDir`).
 * Ключ нигде не логируется; вызывающий должен не хранить массив дольше необходимого.
 */
class DatabaseKeyManager(
    private val keyFile: File,
    private val wrapper: KeyWrapper = KeystoreKeyWrapper(),
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Вернуть ключ БД, при первом запуске — создать и сохранить.
     * @throws DatabaseKeyException если файл ключа есть, но расшифровать его нельзя.
     */
    @Synchronized
    fun getOrCreateKey(): ByteArray {
        val atomic = AtomicFile(keyFile)
        if (keyFile.exists()) {
            return try {
                wrapper.unwrap(atomic.readFully()).also {
                    if (it.size != KEY_SIZE_BYTES) throw GeneralSecurityException("unexpected key size")
                }
            } catch (e: GeneralSecurityException) {
                throw DatabaseKeyException("Cannot unwrap database key", e)
            } catch (e: IOException) {
                throw DatabaseKeyException("Cannot read database key file", e)
            } catch (e: RuntimeException) {
                throw DatabaseKeyException("Corrupted database key file", e)
            }
        }
        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        try {
            val wrapped = wrapper.wrap(key)
            keyFile.parentFile?.mkdirs()
            val out = atomic.startWrite()
            try {
                out.write(wrapped)
                atomic.finishWrite(out)
            } catch (e: IOException) {
                atomic.failWrite(out)
                throw e
            }
        } catch (e: GeneralSecurityException) {
            throw DatabaseKeyException("Cannot wrap database key", e)
        } catch (e: IOException) {
            throw DatabaseKeyException("Cannot write database key file", e)
        }
        return key
    }

    companion object {
        const val KEY_SIZE_BYTES: Int = 32
        const val KEY_FILE_NAME: String = "db_key.bin"
    }
}
