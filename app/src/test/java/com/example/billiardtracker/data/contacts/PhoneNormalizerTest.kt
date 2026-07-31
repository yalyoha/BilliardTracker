package com.example.billiardtracker.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizerTest {
    @Test fun `11 digits starting with 7 stays as +7`() =
        assertEquals("+79001234567", normalizePhone("79001234567"))

    @Test fun `11 digits starting with 8 converts to +7`() =
        assertEquals("+79001234567", normalizePhone("89001234567"))

    @Test fun `10 digits assumed RU`() =
        assertEquals("+79001234567", normalizePhone("9001234567"))

    @Test fun `international kept`() =
        assertEquals("+380671234567", normalizePhone("380671234567"))

    @Test fun `garbage returns empty`() {
        assertEquals("", normalizePhone("abc"))
        assertEquals("", normalizePhone("123"))
    }
}
