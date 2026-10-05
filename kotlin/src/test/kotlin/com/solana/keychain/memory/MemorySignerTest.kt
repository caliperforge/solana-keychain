package com.solana.keychain.memory

import com.solana.keychain.Base58
import com.solana.keychain.SignerError
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

private const val ADDRESS = "9C6hybhQ6Aycep9jaUnP6uL9ZYvDjUp1aSkFWPUFJtpj"
private const val MESSAGE_B64 = "AQABA3m1Vi6P5lT5QHixEuipi6eQH4U65pW+1+DjkQutBJZkIVL40Zt5HSRFMkLhXy6rbLfP+ntqXtMAl5YOBpiB2xIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJAQICAAEMAgAAAEBCDwAAAAAA"
private const val SIGNED_TX_B64 = "AQUSPyADYLJarC6XLNhwmO1ZNP7/MECEKnIrOtFcIShPQX3yXWFNn9ftJEhqvrA0W01eyrBk8Pojgs+jRn23Nw4BAAEDebVWLo/mVPlAeLES6KmLp5AfhTrmlb7X4OORC60ElmQhUvjRm3kdJEUyQuFfLqtst8/6e2pe0wCXlg4GmIHbEgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkBAgIAAQwCAAAAQEIPAAAAAAA="

private val seed = ByteArray(32) { (it + 1).toByte() }
private val publicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
private val keypair = seed + publicKey
private val u8Array = keypair.joinToString(",", "[", "]") { (it.toInt() and 0xff).toString() }
private val unsigned = byteArrayOf(1) + ByteArray(64) + Base64.getDecoder().decode(MESSAGE_B64)

private fun keypairFile(contents: String) = File.createTempFile("keypair", ".json").apply {
    deleteOnExit()
    writeText(contents)
}

private fun assertRefused(code: String, block: () -> Unit) =
    assertEquals(code, assertFailsWith<SignerError> { block() }.code.value)

class MemorySignerTest {
    @Test
    fun `D1 reproduces the Go golden signed transaction`() {
        val signer = MemorySigner.fromBytes(seed)
        val signed = signer.signTransaction(unsigned)
        assertEquals(ADDRESS, signer.address)
        assertEquals(SIGNED_TX_B64, signed.encodedTransaction)
        assertTrue(signed.isComplete)
    }

    @Test
    fun `D2 every key source gives the same address`() {
        val signers = listOf(
            MemorySigner.fromBytes(seed),
            MemorySigner.fromBytes(keypair),
            MemorySigner.fromPrivateKeyString(Base58.encode(keypair)),
            MemorySigner.fromPrivateKeyString(" $u8Array\n"),
            MemorySigner.fromKeypairFile(keypairFile(u8Array).path),
        )
        signers.forEach { assertEquals(ADDRESS, it.address) }
    }

    @Test
    fun `D3 invalid keys are refused`() {
        val mismatched = keypair.copyOf().also { it[63] = (it[63] + 1).toByte() }
        val invalid = listOf<() -> Unit>(
            { MemorySigner.fromBytes(mismatched) },
            { MemorySigner.fromBytes(ByteArray(48)) },
            { MemorySigner.fromPrivateKeyString(Base58.encode(seed)) },
            { MemorySigner.fromPrivateKeyString("0OIl") },
            { MemorySigner.fromPrivateKeyString("[]") },
            { MemorySigner.fromPrivateKeyString(u8Array.replaceFirst("[1,", "[256,")) },
            { MemorySigner.fromPrivateKeyString(u8Array.replaceFirst("[1,", "[1.0,")) },
        )
        invalid.forEach { assertRefused("SIGNER_INVALID_PRIVATE_KEY", it) }
    }

    @Test
    fun `D4 keypair file failures are refused`() {
        val missing = File.createTempFile("missing", ".json").apply { delete() }
        assertRefused("SIGNER_IO_ERROR") { MemorySigner.fromKeypairFile(missing.path) }
        assertRefused("SIGNER_INVALID_PRIVATE_KEY") {
            MemorySigner.fromKeypairFile(keypairFile(Base58.encode(keypair)).path)
        }
    }

    @Test
    fun `D5 transactions the signer cannot sign are refused`() {
        val signer = MemorySigner.fromBytes(seed)
        val tooFewKeys = unsigned.copyOf().also { it[65] = 4 }
        val v1 = byteArrayOf(0, 0x81.toByte()) + unsigned.copyOfRange(66, unsigned.size)
        assertRefused("SIGNER_SIGNING_FAILED") { MemorySigner.fromBytes(ByteArray(32) { 9 }).signTransaction(unsigned) }
        assertRefused("SIGNER_SIGNING_FAILED") { signer.signTransaction(tooFewKeys) }
        assertRefused("SIGNER_SERIALIZATION_ERROR") { signer.signTransaction(v1) }
        assertRefused("SIGNER_SERIALIZATION_ERROR") { signer.signTransaction(unsigned.copyOf(100)) }
    }

    @Test
    fun `D6 a second signer leaves slot 0 zero and the transaction partial`() {
        val signer = MemorySigner.fromBytes(seed)
        val message = byteArrayOf(0x80.toByte(), 2, 0, 0, 2) + ByteArray(32) { 7 } + publicKey + ByteArray(32) + byteArrayOf(0, 0)
        val signed = signer.signTransaction(byteArrayOf(0) + message)
        val expected = byteArrayOf(2) + ByteArray(64) + signer.signMessage(message) + message
        assertContentEquals(expected, Base64.getDecoder().decode(signed.encodedTransaction))
        assertFalse(signed.isComplete)
    }

    @Test
    fun `D7 errors and the signer render no detail or key material`() {
        val error = assertFailsWith<SignerError> { MemorySigner.fromBytes(ByteArray(31)) }
        assertEquals("Invalid private key format", error.message)
        assertFalse(error.detail in error.toString())
        assertEquals("MemorySigner(address=$ADDRESS)", MemorySigner.fromBytes(seed).toString())
    }
}
