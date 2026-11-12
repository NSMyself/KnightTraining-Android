package com.knightindustries.knighttraining.lichess

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Lichess login: the access token, encrypted with an Android Keystore key that never leaves
 * the device. The file lives in `noBackupFilesDir`, since a backup restored elsewhere couldn't be
 * decrypted anyway (the key isn't backed up); a reinstall simply starts signed out, which also
 * avoids iOS's Keychain-outlives-the-app trap (docs/lichess.md, 2026-10-11).
 */
class LichessCredentials(context: Context) {
    private val file = File(context.noBackupFilesDir, "lichess_token.bin")
    private val mutex = Mutex()
    private var cached: String? = null
    private var loaded = false

    suspend fun token(): String? = mutex.withLock {
        if (!loaded) {
            cached = withContext(Dispatchers.IO) { runCatching { decrypt(file.readBytes()) }.getOrNull() }
            loaded = true
        }
        cached
    }

    suspend fun save(token: String) = mutex.withLock {
        withContext(Dispatchers.IO) { file.writeBytes(encrypt(token)) }
        cached = token
        loaded = true
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        cached = null
        loaded = true
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    /** The IV's length, the IV, then the ciphertext. */
    private fun encrypt(token: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(token.toByteArray())
        return byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + sealed
    }

    private fun decrypt(bytes: ByteArray): String {
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size)))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "lichess_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
