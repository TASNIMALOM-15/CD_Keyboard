package com.example.bkeyboard

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-based AES-256-GCM encryption for backups. Format: "BKE1" + salt(16) + iv(12) + ciphertext. */
object Crypto {
    private val MAGIC = "BKE1".toByteArray()
    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(pass.toCharArray(), salt, 100000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun isEncrypted(b: ByteArray) = b.size > 4 && b.copyOfRange(0, 4).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, pass: String): ByteArray {
        val r = SecureRandom()
        val salt = ByteArray(16).also { r.nextBytes(it) }
        val iv = ByteArray(12).also { r.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, pass: String): ByteArray {
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data, 32, data.size - 32)
    }
}
