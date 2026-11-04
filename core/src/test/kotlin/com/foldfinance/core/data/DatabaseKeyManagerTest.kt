package com.foldfinance.core.data

import com.foldfinance.core.data.security.DatabaseKeyException
import com.foldfinance.core.data.security.DatabaseKeyManager
import com.foldfinance.core.data.security.KeyWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore в Robolectric нет: проверяем логику менеджера с программным AES-GCM вместо KEK.
 * Обёртка через Keystore — в ручном чек-листе (устройство).
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseKeyManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private class SoftwareWrapper(private val kek: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()) : KeyWrapper {
        override fun wrap(plain: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(128, iv)) }
            return iv + c.doFinal(plain)
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, wrapped.copyOfRange(0, 12))) }
            return c.doFinal(wrapped.copyOfRange(12, wrapped.size))
        }
    }

    @Test
    fun key_isGeneratedOnce_storedWrapped_andStable() {
        val file = tmp.root.resolve("no_backup/db_key.bin")
        val wrapper = SoftwareWrapper()

        val first = DatabaseKeyManager(file, wrapper).getOrCreateKey()
        val second = DatabaseKeyManager(file, wrapper).getOrCreateKey()

        assertThat(first).hasLength(DatabaseKeyManager.KEY_SIZE_BYTES)
        assertThat(second).isEqualTo(first)
        val stored = file.readBytes()
        assertThat(stored.toList().windowed(first.size).none { it == first.toList() }).isTrue()
    }

    @Test
    fun wrongKek_failsLoudly_insteadOfRegenerating() {
        val file = tmp.root.resolve("db_key.bin")
        DatabaseKeyManager(file, SoftwareWrapper()).getOrCreateKey()
        val before = file.readBytes()

        assertThrows(DatabaseKeyException::class.java) {
            DatabaseKeyManager(file, SoftwareWrapper()).getOrCreateKey()
        }
        assertThat(file.readBytes()).isEqualTo(before)
    }
}
