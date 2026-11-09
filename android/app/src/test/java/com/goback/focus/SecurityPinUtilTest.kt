package com.goback.focus

import com.goback.focus.ui.components.SecurityPinUtil
import org.junit.Assert.*
import org.junit.Test

class SecurityPinUtilTest {

    @Test
    fun pinValidation_requiresExactlyFourDigits() {
        assertTrue(SecurityPinUtil.isValidPin("1234"))
        assertTrue(SecurityPinUtil.isValidPin("0000"))
        assertTrue(SecurityPinUtil.isValidPin("9999"))

        assertFalse(SecurityPinUtil.isValidPin("123"))
        assertFalse(SecurityPinUtil.isValidPin("12345"))
        assertFalse(SecurityPinUtil.isValidPin(""))
        assertFalse(SecurityPinUtil.isValidPin("abcd"))
        assertFalse(SecurityPinUtil.isValidPin("12 4"))
        assertFalse(SecurityPinUtil.isValidPin("-123"))
    }

    @Test
    fun hashPin_isDeterministicAndUnique() {
        val hash1 = SecurityPinUtil.hashPin("1234")
        val hash2 = SecurityPinUtil.hashPin("1234")
        val hashOther = SecurityPinUtil.hashPin("4321")

        assertEquals(16, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hashOther)
    }

    @Test
    fun matchesPin_correctlyVerifiesCandidates() {
        val hash = SecurityPinUtil.hashPin("7890")

        // Matches hashed pin
        assertTrue(SecurityPinUtil.matchesPin(hash, "7890"))
        assertFalse(SecurityPinUtil.matchesPin(hash, "7891"))
        assertFalse(SecurityPinUtil.matchesPin(hash, "0000"))

        // Null or blank stored pin allows access (unprotected)
        assertTrue(SecurityPinUtil.matchesPin(null, "1234"))
        assertTrue(SecurityPinUtil.matchesPin("", "1234"))

        // Legacy plaintext 4-digit compatibility
        assertTrue(SecurityPinUtil.matchesPin("5678", "5678"))
        assertFalse(SecurityPinUtil.matchesPin("5678", "5679"))
    }
}
